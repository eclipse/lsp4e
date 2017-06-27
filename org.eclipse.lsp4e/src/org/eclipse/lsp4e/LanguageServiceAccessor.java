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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
import org.eclipse.jface.text.ITextViewer;
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

	static class WrapperEntryKey {
		final IProject project;
		final IContentType contentType;

		public WrapperEntryKey(IProject project, IContentType contentType) {
			this.project = project;
			this.contentType = contentType;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((contentType == null) ? 0 : contentType.hashCode());
			result = prime * result + ((project == null) ? 0 : project.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			WrapperEntryKey other = (WrapperEntryKey) obj;
			if (contentType == null) {
				if (other.contentType != null) {
					return false;
				}
			} else if (!contentType.equals(other.contentType)) {
				return false;
			}
			if (project == null) {
				if (other.project != null) {
					return false;
				}
			} else if (!project.equals(other.project)) {
				return false;
			}
			return true;
		}

	}

	private static Map<WrapperEntryKey, List<ProjectSpecificLanguageServerWrapper>> projectServers = new HashMap<>();
	private static Map<StreamConnectionProvider, LanguageServerDefinition> providersToLSDefinitions = new HashMap<>();

	/**
	 * A bean storing association of a Document/File with a language server.
	 * See {@link LanguageServiceAccessor#getLSPDocumentInfoFor(ITextViewer, Predicate)}
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
	 * A bean storing association of a IProject with a language server.
	 */
	public static class LSPServerInfo {

		private final @NonNull IProject project;
		private final @Nullable ServerCapabilities capabilities;
		private final @NonNull LanguageServer languageServer;

		private LSPServerInfo(@NonNull IProject project, @NonNull LanguageServer languageServer,
		        @Nullable ServerCapabilities capabilities) {
			this.project = project;
			this.languageServer = languageServer;
			this.capabilities = capabilities;
		}

		public @NonNull IProject getProject() {
			return project;
		}

		public @NonNull LanguageServer getLanguageServer() {
			return this.languageServer;
		}

		public @Nullable ServerCapabilities getCapabilites() {
			return this.capabilities;
		}
	}

	@Nullable public static LSPDocumentInfo getLSPDocumentInfoFor(@NonNull ITextViewer viewer, @Nullable Predicate<ServerCapabilities> capabilityRequest) {
		return getLSPDocumentInfoFor(viewer.getDocument(), capabilityRequest);
	}

	@Nullable public static LSPDocumentInfo getLSPDocumentInfoFor(@NonNull IDocument document, @Nullable Predicate<ServerCapabilities> capabilityRequest) {
		final IFile file = LSPEclipseUtils.getFile(document);
		if (file != null && file.exists()) {
			URI fileUri = LSPEclipseUtils.toUri(file);
			try {
				ProjectSpecificLanguageServerWrapper wrapper = getLSWrapper(file, capabilityRequest, null);
				if (wrapper != null) {
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

	public static @Nullable LanguageServer getLanguageServer(@NonNull IFile file, Predicate<ServerCapabilities> request) throws IOException {
		ProjectSpecificLanguageServerWrapper wrapper = getLSWrapper(file, request, null);
		if (wrapper != null) {
			wrapper.connect(file.getLocation(), null);
			return wrapper.getServer();
		}
		return null;
	}

	/**
	 *
	 * @param file
	 * @param request
	 * @param serverId
	 * @return a LanguageServer for the given file, which is defined with provided server ID and conforms to specified requst
	 */
	public static @Nullable LanguageServer getLanguageServer(@NonNull IFile file, Predicate<ServerCapabilities> request, @NonNull String serverId) throws IOException {
		ProjectSpecificLanguageServerWrapper wrapper = getLSWrapper(file, request, serverId);
		if (wrapper != null) {
			wrapper.connect(file.getLocation(), null);
			return wrapper.getServer();
		}
		return null;
	}

	@Nullable private static ProjectSpecificLanguageServerWrapper getLSWrapper(@NonNull IFile file, @Nullable Predicate<ServerCapabilities> request, @Nullable String serverId) throws IOException {
		IProject project = file.getProject();
		if (project == null) {
			return null;
		}

		// look for running language server via connected document
		ProjectSpecificLanguageServerWrapper wrapper = getMatchingStartedWrapper(file, request, serverId);
		if (wrapper != null) {
			return wrapper;
		}

		// look for running language servers via content-type
		IContentType[] fileContentTypes = null;
		try (InputStream contents = file.getContents()) {
			fileContentTypes = Platform.getContentTypeManager().findContentTypesFor(contents, file.getName()); //TODO consider using document as inputstream
		} catch (CoreException e) {
			LanguageServerPlugin.logError(e);
			return null;
		}

		wrapper = getMatchingStartedWrapper(project, fileContentTypes, request, serverId);
		if (wrapper != null) {
			return wrapper;
		}

		for (IContentType contentType : fileContentTypes) {
			if (contentType == null) {
				continue;
			}
			for (LanguageServerDefinition serverDefinition : LanguageServersRegistry.getInstance().findProviderFor(contentType)) {
				if (serverDefinition != null && (serverId == null || serverDefinition.getId().equals(serverId))) {
					wrapper = getLSWrapperForConnection(project, contentType, serverDefinition);
					if (request == null
						|| wrapper.getServerCapabilities() == null /* null check is workaround for https://github.com/TypeFox/ls-api/issues/47 */
						|| request.test(wrapper.getServerCapabilities())) {
						return wrapper;
					}
				}
			}
		}
		return null;
	}

	@NonNull private static Collection<ProjectSpecificLanguageServerWrapper> getLSWrappers(@NonNull IFile file, @Nullable Predicate<ServerCapabilities> request) throws IOException {
		LinkedHashSet<ProjectSpecificLanguageServerWrapper> res = new LinkedHashSet<>();
		IProject project = file.getProject();
		if (project == null) {
			return res;
		}

		ProjectSpecificLanguageServerWrapper wrapper = getMatchingStartedWrapper(file, request, null);
		if (wrapper != null) {
			res.add(wrapper);
		}

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
					wrapper = getLSWrapperForConnection(project, contentType, serverDefinition);
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
	 * @param contentType
	 * @param serverDefinition
	 * @return
	 * @throws IOException
	 */
	public static ProjectSpecificLanguageServerWrapper getLSWrapperForConnection(@NonNull IProject project, @NonNull IContentType contentType, @NonNull LanguageServerDefinition serverDefinition) throws IOException {
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

			WrapperEntryKey key = new WrapperEntryKey(project, contentType);
			if (!projectServers.containsKey(key)) {
				projectServers.put(key, new ArrayList<>());
			}

			List<ProjectSpecificLanguageServerWrapper> wrapperList = projectServers.get(key);
			if (!wrapperList.contains(wrapper)) {
				wrapperList.add(wrapper);
			}
		}
		return wrapper;
	}

	private static @NonNull List<ProjectSpecificLanguageServerWrapper> getStartedLSWrappers(@NonNull IProject project) {
		return projectServers.values().stream().flatMap(List::stream).filter(wrapper -> wrapper.project.equals(project)).collect(Collectors.toList());
	}

	private static ProjectSpecificLanguageServerWrapper getMatchingStartedWrapper(IProject project,
	        IContentType[] fileContentTypes, Predicate<ServerCapabilities> request, @Nullable String serverId) {
		for (IContentType contentType : fileContentTypes) {
			WrapperEntryKey key = new WrapperEntryKey(project, contentType);

			synchronized(projectServers) {
				if (!projectServers.containsKey(key)) {
					projectServers.put(key, new ArrayList<>());
				}
				for (ProjectSpecificLanguageServerWrapper aWrapper : projectServers.get(key)) {
					if (aWrapper != null && (serverId == null || aWrapper.serverDefinition.getId().equals(serverId)) && (request == null
							|| aWrapper.getServerCapabilities() == null /* null check is workaround for https://github.com/TypeFox/ls-api/issues/47 */
							|| request.test(aWrapper.getServerCapabilities())
						)) {
						return aWrapper;
					}
				}
			}
		}
		return null;
	}

	private static ProjectSpecificLanguageServerWrapper getMatchingStartedWrapper(IFile file,
	        Predicate<ServerCapabilities> request, @Nullable String serverId) {
		IProject project = file.getProject();

		synchronized(projectServers) {
			Set<WrapperEntryKey> servers = projectServers.keySet();
			for (WrapperEntryKey server : servers) {
				if (server.project == project) {
					List<ProjectSpecificLanguageServerWrapper> wrappers = projectServers.get(server);
					for (ProjectSpecificLanguageServerWrapper wrapper : wrappers) {
						if (wrapper.isConnectedTo(file.getLocation())) {
							return wrapper;
						}
					}
				}
			}
		}
		return null;
	}

	/**
	 * Gets list of LS initialized for given project.
	 *
	 * @param project
	 * @param request
	 * @return list of servers info
	 */
	@NonNull public static List<LSPServerInfo> getLSPServerInfos(@NonNull IProject project, Predicate<ServerCapabilities> request) {
		List<LSPServerInfo> serverInfos = new ArrayList<>();
		for (Entry<WrapperEntryKey, List<ProjectSpecificLanguageServerWrapper>> entry : projectServers.entrySet()) {
			WrapperEntryKey wrapperEntryKey = entry.getKey();
			if (project.equals(wrapperEntryKey.project)) {
				for (ProjectSpecificLanguageServerWrapper wrapper : entry.getValue()) {
					@Nullable
					LanguageServer server = wrapper.getServer();
					if (server == null) {
						continue;
					}
					if ((request == null
					    || wrapper.getServerCapabilities() == null /* null check is workaround for https://github.com/TypeFox/ls-api/issues/47 */
					    || request.test(wrapper.getServerCapabilities()))) {
						serverInfos.add(new LSPServerInfo(project, server, wrapper.getServerCapabilities()));
					}
				}
			}
		}
		return serverInfos;
	}

	protected static LanguageServerDefinition getLSDefinition(@NonNull StreamConnectionProvider provider) {
		return providersToLSDefinitions.get(provider);
	}

	@NonNull public static List<LSPDocumentInfo> getLSPDocumentInfosFor(@NonNull IDocument document, Predicate<ServerCapabilities> capabilityRequest) {
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
