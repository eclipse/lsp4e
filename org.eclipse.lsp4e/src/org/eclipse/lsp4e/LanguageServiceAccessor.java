/*******************************************************************************
 * Copyright (c) 2016, 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Lucas Bullen (Red Hat Inc.) - [Bug 517428] Requests sent before initialization
 *******************************************************************************/
package org.eclipse.lsp4e;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LanguageServersRegistry.LanguageServerDefinition;
import org.eclipse.lsp4e.server.StreamConnectionProvider;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.services.LanguageServer;

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
	 * A bean storing association of a Document/File with a language server.
	 */
	public static class LSPDocumentInfo {

		private final @NonNull URI fileUri;
		private final @NonNull IDocument document;
		private final @NonNull LanguageServerWrapper wrapper;
		private final @NonNull LanguageServer server;

		private LSPDocumentInfo(@NonNull URI fileUri, @NonNull IDocument document,
				@NonNull LanguageServerWrapper wrapper, @NonNull LanguageServer server) {
			this.fileUri = fileUri;
			this.document = document;
			this.wrapper = wrapper;
			this.server = server;
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
		 * @deprecated use {@link #getInitializedServer()} instead.
		 */
		@Deprecated
		public @NonNull LanguageServer getLanguageClient() {
			return this.server;
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

	/**
	 * Returns the language servers, regardless of if they are initialized.
	 *
	 * @deprecated use {@link #getInitializedLanguageServers()} instead.
	 */
	@Deprecated
	public static @NonNull Collection<LanguageServer> getLanguageServers(@NonNull IFile file,
			Predicate<ServerCapabilities> request) throws IOException {
		Collection<LanguageServerWrapper> wrappers = getLSWrappers(file, request);
		wrappers.forEach(w -> {
			try {
				w.connect(file, null);
			} catch (IOException e) {
				LanguageServerPlugin.logError(e);
			}
		});
		return wrappers.stream().map(LanguageServerWrapper::getServer).collect(Collectors.toList());
	}

	public static @NonNull List<CompletableFuture<LanguageServer>> getInitializedLanguageServers(@NonNull IFile file,
			Predicate<ServerCapabilities> request) throws IOException {
		Collection<LanguageServerWrapper> wrappers = getLSWrappers(file, request);
		wrappers.forEach(w -> {
			try {
				w.connect(file, null);
			} catch (IOException e) {
				LanguageServerPlugin.logError(e);
			}
		});
		return wrappers.stream().map(LanguageServerWrapper::getInitializedServer).collect(Collectors.toList());
	}

	/**
	 * Get the requested language server instance for the given file. Starts the language server if not already started.
	 * @param file
	 * @param serverId
	 * @return a LanguageServer for the given file, which is defined with provided server ID and conforms to specified request
	 * @deprecated will be removed soon
	 */
	@Deprecated
	private static LanguageServer getLanguageServer(@NonNull IFile file, @NonNull LanguageServerDefinition lsDefinition)
			throws IOException {
		return getLanguageServer(file, lsDefinition, null);
	}

	/**
	 * Get the requested language server instance for the given file. Starts the language server if not already started.
	 * @param file
	 * @param serverId
	 * @param capabilitesPredicate a predicate to check capabilities
	 * @return a LanguageServer for the given file, which is defined with provided server ID and conforms to specified request
	 * @deprecated use {@link #getInitializedLanguageServer()} instead.
	 */
	@Deprecated
	public static LanguageServer getLanguageServer(@NonNull IFile file, @NonNull LanguageServerDefinition lsDefinition,
			Predicate<ServerCapabilities> capabilitiesPredicate)
			throws IOException {
		ProjectSpecificLanguageServerWrapper wrapper = getLSWrapperForConnection(file.getProject(), lsDefinition);
		if (wrapper != null && (capabilitiesPredicate == null
				|| wrapper.getServerCapabilities() == null /* null check is workaround for https://github.com/TypeFox/ls-api/issues/47 */
				|| capabilitiesPredicate.test(wrapper.getServerCapabilities()))) {
			wrapper.connect(file, null);
			return wrapper.getServer();
		}
		return null;
	}

	/**
	 * Get the requested language server instance for the given file. Starts the language server if not already started.
	 * @param file
	 * @param serverId
	 * @param capabilitesPredicate a predicate to check capabilities
	 * @return a LanguageServer for the given file, which is defined with provided server ID and conforms to specified request
	 */
	public static CompletableFuture<LanguageServer> getInitializedLanguageServer(@NonNull IFile file,
			@NonNull LanguageServerDefinition lsDefinition,
			Predicate<ServerCapabilities> capabilitiesPredicate)
			throws IOException {
		LanguageServerWrapper wrapper = getLSWrapperForConnection(file.getProject(), lsDefinition);
		if (wrapper != null && (capabilitiesPredicate == null
				|| wrapper.getServerCapabilities() == null /* null check is workaround for https://github.com/TypeFox/ls-api/issues/47 */
				|| capabilitiesPredicate.test(wrapper.getServerCapabilities()))) {
			wrapper.connect(file, null);
			return wrapper.getInitializedServer();
		}
		return null;
	}


	/**
	 *
	 * @param file
	 * @param request
	 * @return
	 * @throws IOException
	 * @noreference This method is currently internal and should only be referenced for testing
	 */
	@NonNull
	public static Collection<LanguageServerWrapper> getLSWrappers(@NonNull IFile file,
			@NonNull Predicate<ServerCapabilities> request) throws IOException {
		LinkedHashSet<LanguageServerWrapper> res = new LinkedHashSet<>();
		IProject project = file.getProject();
		if (project == null) {
			return res;
		}

		res.addAll(getMatchingStartedWrappers(file, request));

		// look for running language servers via content-type
		Queue<IContentType> contentTypes = new LinkedList<>();
		Set<IContentType> addedContentTypes = new HashSet<>();
		try (InputStream contents = file.getContents()) {
			contentTypes.addAll(Arrays.asList(Platform.getContentTypeManager().findContentTypesFor(contents, file.getName()))); //TODO consider using document as inputstream
			addedContentTypes.addAll(contentTypes);
		} catch (CoreException e) {
			LanguageServerPlugin.logError(e);
			return res;
		}

		while (!contentTypes.isEmpty()) {
			IContentType contentType = contentTypes.poll();
			if (contentType == null) {
				continue;
			}
			for (ContentTypeToLanguageServerDefinition mapping : LanguageServersRegistry.getInstance().findProviderFor(contentType)) {
				if (mapping != null && mapping.getValue() != null) {
					ProjectSpecificLanguageServerWrapper wrapper = getLSWrapperForConnection(project, mapping.getValue());
					if (request == null
						|| wrapper.getServerCapabilities() == null /* null check is workaround for https://github.com/TypeFox/ls-api/issues/47 */
						|| request.test(wrapper.getServerCapabilities())) {
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


	/**
	 * Return existing {@link ProjectSpecificLanguageServerWrapper} for the given connection. If not found,
	 * create a new one with the given connection and register it for this project/content-type.
	 * @param project
	 * @param serverDefinition
	 * @return
	 * @throws IOException
	 * @Deprecated will be made private soon
	 * @noreference will be made private soon
	 */
	@Deprecated
	public static ProjectSpecificLanguageServerWrapper getLSWrapperForConnection(@NonNull IProject project,
			@NonNull LanguageServerDefinition serverDefinition) throws IOException {
		LanguageServerWrapper wrapper = null;

		synchronized(startedServers) {
			for (LanguageServerWrapper startedWrapper : getStartedLSWrappers(project)) {
				if (startedWrapper.serverDefinition.equals(serverDefinition)) {
					wrapper = startedWrapper;
					break;
				}
			}
			if (wrapper == null) {
				wrapper = new ProjectSpecificLanguageServerWrapper(project, serverDefinition);
				wrapper.start();
			}

			if (wrapper != null) {
				startedServers.add(wrapper);
			}
		}
		return (ProjectSpecificLanguageServerWrapper) wrapper;
	}

	private static @NonNull List<LanguageServerWrapper> getStartedLSWrappers(
			@NonNull IProject project) {
		return startedServers.stream().filter(wrapper -> wrapper.canOperate(project))
				.collect(Collectors.toList());
		// TODO multi-root: also return servers which support multi-root?
	}

	private static Collection<LanguageServerWrapper> getMatchingStartedWrappers(@NonNull IFile file,
	       @NonNull Predicate<ServerCapabilities> request) {
		synchronized(startedServers) {
			return startedServers.stream()
					.filter(wrapper -> {
						try {
							return wrapper.isConnectedTo(file.getLocation()) ||
								(LanguageServersRegistry.getInstance().matches(file, wrapper.serverDefinition) && wrapper.canOperate(file.getProject()));
						} catch (IOException | CoreException e) {
							LanguageServerPlugin.logError(e);
							return false;
						}
					})
				.filter(wrapper -> wrapper.getServerCapabilities() == null || request.test(wrapper.getServerCapabilities()))
				.collect(Collectors.toList());
		}
	}

	/**
	 * Gets list of LS initialized for given project.
	 *
	 * @param project
	 * @param request
	 * @return list of Language Servers
	 */
	@NonNull public static List<@NonNull LanguageServer> getLanguageServers(@NonNull IProject project, Predicate<ServerCapabilities> request) {
		List<@NonNull LanguageServer> serverInfos = new ArrayList<>();
		for (LanguageServerWrapper wrapper : startedServers) {
			if (!wrapper.canOperate(project)) {
				continue;
			}
			@Nullable LanguageServer server = wrapper.getServer();
			if (server == null) {
				continue;
			}
			if ((request == null
			    || wrapper.getServerCapabilities() == null /* null check is workaround for https://github.com/TypeFox/ls-api/issues/47 */
			    || request.test(wrapper.getServerCapabilities()))) {
				serverInfos.add(server);
			}
		}
		return serverInfos;
	}

	protected static LanguageServerDefinition getLSDefinition(@NonNull StreamConnectionProvider provider) {
		return providersToLSDefinitions.get(provider);
	}

	@NonNull public static List<@NonNull LSPDocumentInfo> getLSPDocumentInfosFor(@NonNull IDocument document, @NonNull Predicate<ServerCapabilities> capabilityRequest) {
		final IFile file = LSPEclipseUtils.getFile(document);
		URI fileUri = null;
		if (file != null && file.exists()) {
			fileUri = LSPEclipseUtils.toUri(file);
			List<LSPDocumentInfo> res = new ArrayList<>();
			try {
				for (LanguageServerWrapper wrapper : getLSWrappers(file, capabilityRequest)) {
					wrapper.connect(file, document);
					@Nullable
					LanguageServer server = wrapper.getServer();
					if (server != null) {
						res.add(new LSPDocumentInfo(fileUri, document, wrapper, server));
					}
				}
			} catch (final Exception e) {
				LanguageServerPlugin.logError(e);
			}
			return res;
		} else {
			//fileUri = "file://" + location.toFile().getAbsolutePath();
			//TODO handle case of plain file (no IFile)
		}
		return Collections.emptyList();
	}

}
