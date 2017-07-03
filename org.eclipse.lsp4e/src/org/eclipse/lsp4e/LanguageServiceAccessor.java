/*******************************************************************************
 * Copyright (c) 2016, 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * Deals with instantiations and caching of underlying {@link ProjectSpecificLanguageServerWrapper}.
 *
 */
public class LanguageServiceAccessor {

	private LanguageServiceAccessor() {
		// this class shouldn't be instantiated
	}

	private static Set<ProjectSpecificLanguageServerWrapper> projectServers = new HashSet<>();
	private static Map<StreamConnectionProvider, LanguageServerDefinition> providersToLSDefinitions = new HashMap<>();

	/**
	 * A bean storing association of a Document/File with a language server.
	 */
	public static class LSPDocumentInfo {

		private final @NonNull URI fileUri;
		private final @NonNull IDocument document;
		private final @NonNull ProjectSpecificLanguageServerWrapper wrapper;
		private final @NonNull LanguageServer server;

		private LSPDocumentInfo(@NonNull URI fileUri, @NonNull IDocument document, @NonNull ProjectSpecificLanguageServerWrapper wrapper, @NonNull LanguageServer server) {
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

		public @NonNull LanguageServer getLanguageClient() {
			return this.server;
		}

		public @Nullable ServerCapabilities getCapabilites() {
			return this.wrapper.getServerCapabilities();
		}

		public boolean isActive() {
			return this.wrapper.isActive();
		}
	}

	/**
	 *
	 * @param document
	 * @param capabilityRequest
	 * @return
	 * @deprecated use {@link #getLSPDocumentInfosFor(IDocument, Predicate)} instead
	 */
	@Deprecated
	@Nullable public static LSPDocumentInfo getLSPDocumentInfoFor(@NonNull IDocument document, @Nullable Predicate<ServerCapabilities> capabilityRequest) {
		final IFile file = LSPEclipseUtils.getFile(document);
		final @NonNull Predicate<ServerCapabilities> request = capabilityRequest != null ? capabilityRequest : capabilities -> Boolean.TRUE;
		if (file != null && file.exists()) {
			URI fileUri = LSPEclipseUtils.toUri(file);
			try {
				Collection<ProjectSpecificLanguageServerWrapper> wrappers = getLSWrappers(file, request);
				if (!wrappers.isEmpty()) {
					ProjectSpecificLanguageServerWrapper wrapper = wrappers.iterator().next();
					wrapper.connect(file.getLocation(), document);
					@Nullable
					LanguageServer server = wrapper.getServer();
					if (server != null) {
						return new LSPDocumentInfo(fileUri, document, wrapper, server);
					}
				}
			} catch (final Exception e) {
				LanguageServerPlugin.logError(e);
			}
		} else {
			LanguageServerPlugin.logInfo("Non IFiles not supported yet"); //$NON-NLS-1$
			//fileUri = "file://" + location.toFile().getAbsolutePath();
			//TODO handle case of plain file (no IFile)
		}
		return null;
	}

	public static @Nullable Collection<LanguageServer> getLanguageServers(@NonNull IFile file, Predicate<ServerCapabilities> request) throws IOException {
		Collection<ProjectSpecificLanguageServerWrapper> wrappers = getLSWrappers(file, request);
		wrappers.forEach(w -> {
			try {
				w.connect(file.getLocation(), null);
			} catch (IOException e) {
				LanguageServerPlugin.logError(e);
			}
		});
		return wrappers.stream().map(ProjectSpecificLanguageServerWrapper::getServer).collect(Collectors.toList());
	}

	/**
	 * Get the requested language server instance for the given file. Starts the language server if not already started.
	 * @param file
	 * @param serverId
	 * @return a LanguageServer for the given file, which is defined with provided server ID and conforms to specified requst
	 */
	public static LanguageServer getLanguageServer(@NonNull IFile file, @NonNull LanguageServerDefinition lsDefinition) throws IOException {
		ProjectSpecificLanguageServerWrapper wrapper = getLSWrapperForConnection(file.getProject(), lsDefinition);
		if (wrapper != null) {
			wrapper.connect(file.getLocation(), null);
			return wrapper.getServer();
		}
		return null;
	}

	@NonNull private static Collection<ProjectSpecificLanguageServerWrapper> getLSWrappers(@NonNull IFile file, @NonNull Predicate<ServerCapabilities> request) throws IOException {
		LinkedHashSet<ProjectSpecificLanguageServerWrapper> res = new LinkedHashSet<>();
		IProject project = file.getProject();
		if (project == null) {
			return res;
		}

		res.addAll(getMatchingStartedWrappers(file, request));

		// look for running language servers via content-type
		IContentType[] fileContentTypes = null;
		try (InputStream contents = file.getContents()) {
			fileContentTypes = Platform.getContentTypeManager().findContentTypesFor(contents, file.getName()); //TODO consider using document as inputstream
		} catch (CoreException e) {
			LanguageServerPlugin.logError(e);
			return res;
		}

		for (IContentType contentType : fileContentTypes) {
			if (contentType == null) {
				continue;
			}
			for (LanguageServerDefinition serverDefinition : LanguageServersRegistry.getInstance().findProviderFor(contentType)) {
				if (serverDefinition != null) {
					ProjectSpecificLanguageServerWrapper wrapper = getLSWrapperForConnection(project, serverDefinition);
					if (request == null
						|| wrapper.getServerCapabilities() == null /* null check is workaround for https://github.com/TypeFox/ls-api/issues/47 */
						|| request.test(wrapper.getServerCapabilities())) {
						res.add(wrapper);
					}
				}
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
	 */
	public static ProjectSpecificLanguageServerWrapper getLSWrapperForConnection(@NonNull IProject project, @NonNull LanguageServerDefinition serverDefinition) throws IOException {
		ProjectSpecificLanguageServerWrapper wrapper = null;

		synchronized(projectServers) {
			for (ProjectSpecificLanguageServerWrapper startedWrapper : getStartedLSWrappers(project)) {
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
				projectServers.add(wrapper);
			}
		}
		return wrapper;
	}

	private static @NonNull List<ProjectSpecificLanguageServerWrapper> getStartedLSWrappers(@NonNull IProject project) {
		return projectServers.stream().filter(wrapper -> wrapper.project.equals(project)).collect(Collectors.toList());
	}

	private static Collection<ProjectSpecificLanguageServerWrapper> getMatchingStartedWrappers(@NonNull IFile file,
	       @NonNull Predicate<ServerCapabilities> request) {
		final IProject project = file.getProject();

		synchronized(projectServers) {
			return projectServers.stream()
				.filter(wrapper -> wrapper.project.equals(project))
				.filter(wrapper -> wrapper.getServerCapabilities() == null || request.test(wrapper.getServerCapabilities()))
				.filter(wrapper -> wrapper.isConnectedTo(file.getLocation()))
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
		for (ProjectSpecificLanguageServerWrapper wrapper : projectServers) {
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
				for (ProjectSpecificLanguageServerWrapper wrapper : getLSWrappers(file, capabilityRequest)) {
					wrapper.connect(file.getLocation(), document);
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
			LanguageServerPlugin.logInfo("Non IFiles not supported yet"); //$NON-NLS-1$
			//fileUri = "file://" + location.toFile().getAbsolutePath();
			//TODO handle case of plain file (no IFile)
		}
		return Collections.emptyList();
	}

}
