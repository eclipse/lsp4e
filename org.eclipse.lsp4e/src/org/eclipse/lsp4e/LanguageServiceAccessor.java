/*******************************************************************************
 * Copyright (c) 2016, 2019 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Lucas Bullen (Red Hat Inc.) - [Bug 517428] Requests sent before initialization
 *  Martin Lippert (Pivotal Inc.) - bug 531167, 531670, 536258
 *  Kris De Volder (Pivotal Inc.) - Get language servers by capability predicate.
 *******************************************************************************/
package org.eclipse.lsp4e;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.content.IContentDescription;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LanguageServersRegistry.LanguageServerDefinition;
import org.eclipse.lsp4e.server.StreamConnectionProvider;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.part.FileEditorInput;

/**
 * The entry-point to retrieve a Language Server for a given resource/project.
 * Deals with instantiations and caching of underlying
 * {@link LanguageServerWrapper}.
 *
 */
public class LanguageServiceAccessor {

	private LanguageServiceAccessor() {
		// this class shouldn't be instantiated
	}

	private static Set<LanguageServerWrapper> startedServers = new HashSet<>();
	private static Map<StreamConnectionProvider, LanguageServerDefinition> providersToLSDefinitions = new HashMap<>();

	/**
	 * This is meant for test code to clear state that might have leaked from other
	 * tests. It isn't meant to be used in production code.
	 */
	public static void clearStartedServers() {
		synchronized (startedServers) {
			startedServers.forEach(LanguageServerWrapper::stop);
			startedServers.clear();
		}
	}

	/**
	 * A bean storing association of a Document/File with a language server.
	 */
	public static class LSPDocumentInfo {

		private final @NonNull URI fileUri;
		private final @NonNull IDocument document;
		private final @NonNull LanguageServerWrapper wrapper;

		private LSPDocumentInfo(@NonNull URI fileUri, @NonNull IDocument document,
				@NonNull LanguageServerWrapper wrapper) {
			this.fileUri = fileUri;
			this.document = document;
			this.wrapper = wrapper;
		}

		public @NonNull IDocument getDocument() {
			return this.document;
		}

		/**
		 * TODO consider directly returning a {@link TextDocumentIdentifier}
		 * @return
		 */
		public @NonNull URI getFileUri() {
			return this.fileUri;
		}

		/**
		 * Returns the language server, regardless of if it is initialized.
		 *
		 * @deprecated use {@link #getInitializedLanguageClient()} instead.
		 */
		@Deprecated
		public LanguageServer getLanguageClient() {
			try {
				return this.wrapper.getInitializedServer().get();
			} catch (ExecutionException e) {
				LanguageServerPlugin.logError(e);
				return this.wrapper.getServer();
			} catch (InterruptedException e) {
				LanguageServerPlugin.logError(e);
				Thread.currentThread().interrupt();
				return this.wrapper.getServer();
			}
		}

		public int getVersion() {
			return wrapper.getVersion(fileUri);
		}

		public CompletableFuture<LanguageServer> getInitializedLanguageClient() {
			return this.wrapper.getInitializedServer();
		}

		public @Nullable ServerCapabilities getCapabilites() {
			return this.wrapper.getServerCapabilities();
		}

		public boolean isActive() {
			return this.wrapper.isActive();
		}
	}


	public static @NonNull List<CompletableFuture<LanguageServer>> getInitializedLanguageServers(@NonNull IFile file,
			@Nullable Predicate<ServerCapabilities> request) throws IOException {
		synchronized (startedServers) {
			Collection<LanguageServerWrapper> wrappers = getLSWrappers(file, request);
			return wrappers.stream().map(wrapper -> wrapper.getInitializedServer().thenApplyAsync(server -> {
				try {
					wrapper.connect(file, null);
				} catch (IOException e) {
					LanguageServerPlugin.logError(e);
				}
				return server;
			})).collect(Collectors.toList());
		}
	}

	public static void disableLanguageServerContentType(
			@NonNull ContentTypeToLanguageServerDefinition contentTypeToLSDefinition) {
		Optional<LanguageServerWrapper> result = startedServers.stream()
				.filter(server -> server.serverDefinition.equals(contentTypeToLSDefinition.getValue())).findFirst();
		if (result.isPresent()) {
			IContentType contentType = contentTypeToLSDefinition.getKey();
			if (contentType != null) {
				result.get().disconnectContentType(contentType);
			}
		}

	}

	public static void enableLanguageServerContentType(
			@NonNull ContentTypeToLanguageServerDefinition contentTypeToLSDefinition,
			@NonNull IEditorReference[] editors) {
		for (IEditorReference editor : editors) {
			try {
				if (editor.getEditorInput() instanceof FileEditorInput) {
					IFile editorFile = ((FileEditorInput) editor.getEditorInput()).getFile();
					IContentType contentType = contentTypeToLSDefinition.getKey();
					LanguageServerDefinition lsDefinition = contentTypeToLSDefinition.getValue();
					IContentDescription contentDesc = editorFile.getContentDescription();
					if (contentTypeToLSDefinition.isEnabled() && contentType != null && contentDesc != null
							&& contentType.equals(contentDesc.getContentType())
							&& lsDefinition != null) {
						try {
							getInitializedLanguageServer(editorFile, lsDefinition, capabilities -> true);
						} catch (IOException e) {
							LanguageServerPlugin.logError(e);
						}
					}
				}
			} catch (CoreException e) {
				LanguageServerPlugin.logError(e);
			}
		}

	}

	/**
	 * Get the requested language server instance for the given file. Starts the language server if not already started.
	 * @param file
	 * @param lsDefinition
	 * @param capabilitiesPredicate a predicate to check capabilities
	 * @return a LanguageServer for the given file, which is defined with provided server ID and conforms to specified request
	 * @deprecated use {@link #getInitializedLanguageServer(IFile, LanguageServerDefinition, Predicate)} instead.
	 */
	@Deprecated
	public static LanguageServer getLanguageServer(@NonNull IFile file, @NonNull LanguageServerDefinition lsDefinition,
			Predicate<ServerCapabilities> capabilitiesPredicate)
			throws IOException {
		LanguageServerWrapper wrapper = getLSWrapperForConnection(file.getProject(), lsDefinition, file.getFullPath());
		if (capabilitiesComply(wrapper, capabilitiesPredicate)) {
			wrapper.connect(file, null);
			return wrapper.getServer();
		}
		return null;
	}

	/**
	 * Get the requested language server instance for the given file. Starts the language server if not already started.
	 * @param file
	 * @param lsDefinition
	 * @param capabilitiesPredicate a predicate to check capabilities
	 * @return a LanguageServer for the given file, which is defined with provided server ID and conforms to specified request.
	 *  If {@code capabilitesPredicate} does not test positive for the server's capabilities, {@code null} is returned.
	 */
	public static CompletableFuture<LanguageServer> getInitializedLanguageServer(@NonNull IFile file,
			@NonNull LanguageServerDefinition lsDefinition,
			Predicate<ServerCapabilities> capabilitiesPredicate)
			throws IOException {
		LanguageServerWrapper wrapper = getLSWrapperForConnection(file.getProject(), lsDefinition, file.getFullPath());
		if (capabilitiesComply(wrapper, capabilitiesPredicate)) {
			wrapper.connect(file, null);
			return wrapper.getInitializedServer();
		}
		return null;
	}

	/**
	 * Get the requested language server instance for the given document. Starts the
	 * language server if not already started.
	 *
	 * @param document the document for which the initialized LanguageServer shall be returned
	 * @param lsDefinition the ID of the LanguageServer to be returned
	 * @param capabilitiesPredicate
	 *            a predicate to check capabilities
	 * @return a LanguageServer for the given file, which is defined with provided
	 *         server ID and conforms to specified request. If
	 *         {@code capabilitesPredicate} does not test positive for the server's
	 *         capabilities, {@code null} is returned.
	 */
	public static CompletableFuture<LanguageServer> getInitializedLanguageServer(@NonNull IDocument document,
			@NonNull LanguageServerDefinition lsDefinition, Predicate<ServerCapabilities> capabilitiesPredicate)
			throws IOException {
		IPath initialPath = LSPEclipseUtils.toPath(document);
		LanguageServerWrapper wrapper = getLSWrapperForConnection(document, lsDefinition, initialPath);
		if (capabilitiesComply(wrapper, capabilitiesPredicate)) {
			wrapper.connect(document);
			return wrapper.getInitializedServer();
		}
		return null;
	}

	/**
	 * Checks if the given {@code wrapper}'s capabilities comply with the given
	 * {@code capabilitiesPredicate}.
	 *
	 * @param wrapper
	 *            the server that's capabilities are tested with
	 *            {@code capabilitiesPredicate}
	 * @param capabilitiesPredicate
	 *            predicate testing the capabilities of {@code wrapper}.
	 * @return The result of applying the capabilities of {@code wrapper} to
	 *         {@code capabilitiesPredicate}, or {@code false} if
	 *         {@code capabilitiesPredicate == null} or
	 *         {@code wrapper.getServerCapabilities() == null}
	 */
	private static boolean capabilitiesComply(LanguageServerWrapper wrapper,
			Predicate<ServerCapabilities> capabilitiesPredicate) {
		return capabilitiesPredicate == null
				|| wrapper.getServerCapabilities() == null /* null check is workaround for https://github.com/TypeFox/ls-api/issues/47 */
				|| capabilitiesPredicate.test(wrapper.getServerCapabilities());
	}


	/**
	 * TODO we need a similar method for generic IDocument (enabling non-IFiles)
	 *
	 * @param file
	 * @param request
	 * @return
	 * @throws IOException
	 * @noreference This method is currently internal and should only be referenced
	 *              for testing
	 */
	@NonNull
	public static Collection<LanguageServerWrapper> getLSWrappers(@NonNull IFile file,
			@Nullable Predicate<ServerCapabilities> request) throws IOException {
		LinkedHashSet<LanguageServerWrapper> res = new LinkedHashSet<>();
		IProject project = file.getProject();
		if (project == null) {
			return res;
		}

		res.addAll(getMatchingStartedWrappers(file, request));

		// look for running language servers via content-type
		Queue<IContentType> contentTypes = new LinkedList<>();
		Set<IContentType> addedContentTypes = new HashSet<>();
		contentTypes.addAll(LSPEclipseUtils.getFileContentTypes(file));
		addedContentTypes.addAll(contentTypes);

		while (!contentTypes.isEmpty()) {
			IContentType contentType = contentTypes.poll();
			if (contentType == null) {
				continue;
			}
			for (ContentTypeToLanguageServerDefinition mapping : LanguageServersRegistry.getInstance().findProviderFor(contentType)) {
				if (mapping != null && mapping.getValue() != null && mapping.isEnabled()) {
					LanguageServerWrapper wrapper = getLSWrapperForConnection(project, mapping.getValue(), file.getFullPath());
					if (capabilitiesComply(wrapper, request)) {
						res.add(wrapper);
					}
				}
			}
			if (contentType.getBaseType() != null && !addedContentTypes.contains(contentType.getBaseType())) {
				addedContentTypes.add(contentType.getBaseType());
				contentTypes.add(contentType.getBaseType());
			}
		}
		return res;
	}

	@NonNull
	private static Collection<LanguageServerWrapper> getLSWrappers(@NonNull IDocument document) {
		LinkedHashSet<LanguageServerWrapper> res = new LinkedHashSet<>();
		IFile file = LSPEclipseUtils.getFile(document);
		URI uri = LSPEclipseUtils.toUri(document);
		if (uri == null) {
			return Collections.emptyList();
		}
		IPath path = new Path(uri.getPath());

		// look for running language servers via content-type
		Queue<IContentType> contentTypes = new LinkedList<>();
		Set<IContentType> processedContentTypes = new HashSet<>();
		contentTypes.addAll(LSPEclipseUtils.getDocumentContentTypes(document));

		synchronized (startedServers) {
			// already started compatible servers that fit request
			res.addAll(startedServers.stream()
					.filter(wrapper -> {
						try {
							return wrapper.isConnectedTo(uri) || LanguageServersRegistry.getInstance().matches(document, wrapper.serverDefinition);
						} catch (Exception e) {
							LanguageServerPlugin.logError(e);
							return false;
						}
					})
					.filter(wrapper -> wrapper.canOperate(document))
					.collect(Collectors.toList()));

			while (!contentTypes.isEmpty()) {
				IContentType contentType = contentTypes.poll();
				if (contentType == null || processedContentTypes.contains(contentType)) {
					continue;
				}
				for (ContentTypeToLanguageServerDefinition mapping : LanguageServersRegistry.getInstance()
						.findProviderFor(contentType)) {
					if (mapping == null || !mapping.isEnabled()) {
						continue;
					}
					LanguageServerDefinition serverDefinition = mapping.getValue();
					if (serverDefinition == null) {
						continue;
					}
					if (startedServers.stream().anyMatch(wrapper -> wrapper.serverDefinition.equals(serverDefinition)
							&& wrapper.canOperate(document))) {
						// we already checked a compatible LS with this definition
						continue;
					}
					final IProject fileProject = file != null ? file.getProject() : null;
					LanguageServerWrapper wrapper = fileProject != null ? new LanguageServerWrapper(fileProject, serverDefinition) :
						new LanguageServerWrapper(serverDefinition, path);
					startedServers.add(wrapper);
					res.add(wrapper);
				}
				if (contentType.getBaseType() != null) {
					contentTypes.add(contentType.getBaseType());
				}
				processedContentTypes.add(contentType);
			}
			return res;
		}
	}

	/**
	 * Return existing {@link LanguageServerWrapper} for the given connection. If
	 * not found, create a new one with the given connection and register it for
	 * this project/content-type.
	 *
	 * @param project
	 * @param serverDefinition
	 * @return
	 * @throws IOException
	 * @Deprecated will be made private soon
	 * @noreference will be made private soon
	 * @deprecated
	 */
	@Deprecated
	public static LanguageServerWrapper getLSWrapperForConnection(@NonNull IProject project,
			@NonNull LanguageServerDefinition serverDefinition) throws IOException {
		return 	getLSWrapperForConnection(project, serverDefinition, null);
	}

	@Deprecated
	private static LanguageServerWrapper getLSWrapperForConnection(@NonNull IProject project,
			@NonNull LanguageServerDefinition serverDefinition, @Nullable IPath initialPath) throws IOException {
		LanguageServerWrapper wrapper = null;

		synchronized (startedServers) {
			for (LanguageServerWrapper startedWrapper : getStartedLSWrappers(project)) {
				if (startedWrapper.serverDefinition.equals(serverDefinition)) {
					wrapper = startedWrapper;
					break;
				}
			}
			if (wrapper == null) {
				wrapper = project != null ? new LanguageServerWrapper(project, serverDefinition) :
					new LanguageServerWrapper(serverDefinition, initialPath);
				wrapper.start();
			}

			startedServers.add(wrapper);
		}
		return wrapper;
	}

	private static LanguageServerWrapper getLSWrapperForConnection(@NonNull IDocument document,
			@NonNull LanguageServerDefinition serverDefinition, @Nullable IPath initialPath) throws IOException {
		LanguageServerWrapper wrapper = null;

		synchronized (startedServers) {
			for (LanguageServerWrapper startedWrapper : getStartedLSWrappers(document)) {
				if (startedWrapper.serverDefinition.equals(serverDefinition)) {
					wrapper = startedWrapper;
					break;
				}
			}
			if (wrapper == null) {
				wrapper = new LanguageServerWrapper(serverDefinition, initialPath);
				wrapper.start();
			}

			startedServers.add(wrapper);
		}
		return wrapper;
	}

	/**
	 * Interface to be used for passing lambdas to {@link LanguageServiceAccessor#addStartedServerSynchronized(ServerSupplier)}.
	 */
	@FunctionalInterface
	private static interface ServerSupplier {
		LanguageServerWrapper get() throws IOException;
	}

	private static @NonNull List<LanguageServerWrapper> getStartedLSWrappers(
			@NonNull IProject project) {
		return getStartedLSWrappers(wrapper -> wrapper.canOperate(project));
	}

	private static @NonNull List<LanguageServerWrapper> getStartedLSWrappers(
			@NonNull IDocument document) {
		return getStartedLSWrappers(wrapper -> wrapper.canOperate(document));
	}

	private static  @NonNull List<LanguageServerWrapper> getStartedLSWrappers(@NonNull Predicate<LanguageServerWrapper> predicate) {
		return startedServers.stream().filter(predicate)
				.collect(Collectors.toList());
			// TODO multi-root: also return servers which support multi-root?
	}

	private static Collection<LanguageServerWrapper> getMatchingStartedWrappers(@NonNull IFile file,
			@Nullable Predicate<ServerCapabilities> request) {
		synchronized (startedServers) {
			return startedServers.stream().filter(wrapper -> wrapper.isActive() && wrapper.isConnectedTo(file.getLocationURI())
					|| (LanguageServersRegistry.getInstance().matches(file, wrapper.serverDefinition)
							&& wrapper.canOperate(file.getProject()))).filter(wrapper -> request == null
					|| (wrapper.getServerCapabilities() == null || request.test(wrapper.getServerCapabilities())))
					.collect(Collectors.toList());
		}
	}

	/**
	 * Gets list of running LS satisfying a capability predicate. This does not
	 * start any matching language servers, it returns the already running ones.
	 *
	 * @param request
	 * @return list of Language Servers
	 */
	@NonNull
	public static List<@NonNull LanguageServer> getActiveLanguageServers(Predicate<ServerCapabilities> request) {
		return getLanguageServers(null, request, true);
	}

	/**
	 * Gets list of LS initialized for given project.
	 *
	 * @param project
	 * @param request
	 * @return list of Language Servers
	 */
	@NonNull
	public static List<@NonNull LanguageServer> getLanguageServers(@NonNull IProject project,
			Predicate<ServerCapabilities> request) {
		return getLanguageServers(project, request, false);
	}

	/**
	 * Gets list of LS initialized for given project
	 *
	 * @param onlyActiveLS
	 *            true if this method should return only the already running
	 *            language servers, otherwise previously started language servers
	 *            will be re-activated
	 * @return list of Language Servers
	 */
	@NonNull
	public static List<@NonNull LanguageServer> getLanguageServers(@Nullable IProject project,
			Predicate<ServerCapabilities> request, boolean onlyActiveLS) {
		List<@NonNull LanguageServer> serverInfos = new ArrayList<>();
		for (LanguageServerWrapper wrapper : startedServers) {
			if ((!onlyActiveLS || wrapper.isActive()) && (project == null || wrapper.canOperate(project))) {
				@Nullable
				LanguageServer server = wrapper.getServer();
				if (server == null) {
					continue;
				}
				if (capabilitiesComply(wrapper, request)) {
					serverInfos.add(server);
				}
			}
		}
		return serverInfos;
	}

	protected static LanguageServerDefinition getLSDefinition(@NonNull StreamConnectionProvider provider) {
		return providersToLSDefinitions.get(provider);
	}

	@NonNull public static List<@NonNull LSPDocumentInfo> getLSPDocumentInfosFor(@NonNull IDocument document, @NonNull Predicate<ServerCapabilities> capabilityRequest) {
		URI fileUri = LSPEclipseUtils.toUri(document);
		List<LSPDocumentInfo> res = new ArrayList<>();
		try {
			getLSWrappers(document).stream().filter(wrapper -> wrapper.getServerCapabilities() == null
					|| capabilityRequest.test(wrapper.getServerCapabilities())).forEach(wrapper -> {
						try {
							wrapper.connect(document);
						} catch (IOException e) {
							LanguageServerPlugin.logError(e);
						}
						res.add(new LSPDocumentInfo(fileUri, document, wrapper));
					});
		} catch (final Exception e) {
			LanguageServerPlugin.logError(e);
		}
		return res;
	}

	/**
	 *
	 * @param document
	 * @param filter
	 * @return
	 * @since 0.9
	 */
	@NonNull
	public static CompletableFuture<List<@NonNull LanguageServer>> getLanguageServers(@NonNull IDocument document,
			Predicate<ServerCapabilities> filter) {
		URI uri = LSPEclipseUtils.toUri(document);
		if (uri == null) {
			return CompletableFuture.completedFuture(Collections.emptyList());
		}
		final List<@NonNull LanguageServer> res = Collections.synchronizedList(new ArrayList<>());
		try {
			return CompletableFuture.allOf(getLSWrappers(document).stream().map(wrapper ->
						wrapper.getInitializedServer().thenComposeAsync(server -> {
							if (server != null && (filter == null || filter.test(wrapper.getServerCapabilities()))) {
								try {
									return wrapper.connect(document);
								} catch (IOException ex) {
									LanguageServerPlugin.logError(ex);
								}
							}
							return CompletableFuture.completedFuture(null);
						}).thenAccept(server -> {
							if (server != null) {
								res.add(server);
							}
						})).toArray(CompletableFuture[]::new)).thenApply(theVoid -> res);
		} catch (final Exception e) {
			LanguageServerPlugin.logError(e);
		}
		return CompletableFuture.completedFuture(Collections.emptyList());


	}

	public static boolean checkCapability(LanguageServer languageServer, Predicate<ServerCapabilities> condition) {
		return startedServers.stream().filter(wrapper -> wrapper.isActive() && wrapper.getServer() == languageServer)
				.anyMatch(wrapper -> condition.test(wrapper.getServerCapabilities()));
	}

	public static Optional<LanguageServerDefinition> resolveServerDefinition(LanguageServer languageServer) {
		synchronized (startedServers) {
			return startedServers.stream().filter(wrapper -> languageServer.equals(wrapper.getServer())).findFirst().map(wrapper -> wrapper.serverDefinition);
		}
	}
}
