/*******************************************************************************
 * Copyright (c) 2016, 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Miro Spoenemann (TypeFox) - added clientImpl and serverInterface attributes
 *******************************************************************************/
package org.eclipse.lsp4e;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.core.runtime.content.IContentTypeManager;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.preference.IPersistentPreferenceStore;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.lsp4e.server.StreamConnectionProvider;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.statushandlers.StatusManager;
import org.osgi.framework.Bundle;

/**
 * This registry aims at providing a good language server connection (as {@link StreamConnectionProvider}
 * for a given input.
 * At the moment, registry content are hardcoded but we'll very soon need a way
 * to contribute to it via plugin.xml (for plugin developers) and from Preferences
 * (for end-users to directly register a new server).
 *
 */
public class LanguageServersRegistry {

	private static final String CONTENT_TYPE_TO_LSP_LAUNCH_PREF_KEY = "contentTypeToLSPLauch"; //$NON-NLS-1$

	private static final String EXTENSION_POINT_ID = LanguageServerPlugin.PLUGIN_ID + ".languageServer"; //$NON-NLS-1$

	private static final String LS_ELEMENT = "server"; //$NON-NLS-1$
	private static final String MAPPING_ELEMENT = "contentTypeMapping"; //$NON-NLS-1$

	private static final String ID_ATTRIBUTE = "id"; //$NON-NLS-1$
	private static final String CONTENT_TYPE_ATTRIBUTE = "contentType"; //$NON-NLS-1$
	private static final String LANGUAGE_ID_ATTRIBUTE = "languageId"; //$NON-NLS-1$
	private static final String CLASS_ATTRIBUTE = "class"; //$NON-NLS-1$
	private static final String CLIENT_IMPL_ATTRIBUTE = "clientImpl"; //$NON-NLS-1$
	private static final String SERVER_INTERFACE_ATTRIBUTE = "serverInterface"; //$NON-NLS-1$
	private static final String LABEL_ATTRIBUTE = "label"; //$NON-NLS-1$

	public static abstract class LanguageServerDefinition {
		public final @NonNull String id;
		public final @NonNull String label;
		public final @NonNull Map<IContentType, String> langugeIdMappings;

		public LanguageServerDefinition(@NonNull String id, @NonNull String label) {
			this.id = id;
			this.label = label;
			this.langugeIdMappings = new ConcurrentHashMap<>();
		}

		public void registerAssociation(@NonNull IContentType contentType, @NonNull String languageId) {
			this.langugeIdMappings.put(contentType, languageId);
		}

		public abstract StreamConnectionProvider createConnectionProvider();

		public LanguageClientImpl createLanguageClient() {
			return new LanguageClientImpl();
		}

		public Class<? extends LanguageServer> getServerInterface() {
			return LanguageServer.class;
		}
	}

	static class ExtensionLanguageServerDefinition extends LanguageServerDefinition {
		private IConfigurationElement extension;

		public ExtensionLanguageServerDefinition(IConfigurationElement element) {
			super(element.getAttribute(ID_ATTRIBUTE), element.getAttribute(LABEL_ATTRIBUTE));
			this.extension = element;
		}

		@Override
		public StreamConnectionProvider createConnectionProvider() {
			try {
				return (StreamConnectionProvider) extension.createExecutableExtension(CLASS_ATTRIBUTE);
			} catch (CoreException e) {
				StatusManager.getManager().handle(e, LanguageServerPlugin.PLUGIN_ID);
				return null;
			}
		}

		@Override
		public LanguageClientImpl createLanguageClient() {
			String clientImpl = extension.getAttribute(CLIENT_IMPL_ATTRIBUTE);
			if (clientImpl != null && !clientImpl.isEmpty()) {
				try {
					return (LanguageClientImpl) extension.createExecutableExtension(CLIENT_IMPL_ATTRIBUTE);
				} catch (CoreException e) {
					StatusManager.getManager().handle(e, LanguageServerPlugin.PLUGIN_ID);
				}
			}
			return super.createLanguageClient();
		}

		@SuppressWarnings("unchecked")
		@Override
		public Class<? extends LanguageServer> getServerInterface() {
			String serverInterface = extension.getAttribute(SERVER_INTERFACE_ATTRIBUTE);
			if (serverInterface != null && !serverInterface.isEmpty()) {
				Bundle bundle = Platform.getBundle(extension.getContributor().getName());
				if (bundle != null) {
					try {
						return (Class<? extends LanguageServer>) bundle.loadClass(serverInterface);
					} catch (ClassNotFoundException exception) {
						StatusManager.getManager().handle(new Status(IStatus.ERROR, LanguageServerPlugin.PLUGIN_ID,
								exception.getMessage(), exception));
					}
				}
			}
			return super.getServerInterface();
		}
	}

	static class LaunchConfigurationLanguageServerDefinition extends LanguageServerDefinition {
		final ILaunchConfiguration launchConfiguration;
		final Set<String> launchModes;

		public LaunchConfigurationLanguageServerDefinition(ILaunchConfiguration launchConfiguration,
				Set<String> launchModes) {
			super(launchConfiguration.getName(), launchConfiguration.getName());
			this.launchConfiguration = launchConfiguration;
			this.launchModes = launchModes;
		}

		@Override
		public StreamConnectionProvider createConnectionProvider() {
			return new LaunchConfigurationStreamProvider(this.launchConfiguration, launchModes);
		}
	}

	private static LanguageServersRegistry INSTANCE = null;
	public static LanguageServersRegistry getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new LanguageServersRegistry();
		}
		return INSTANCE;
	}

	private List<ContentTypeToLanguageServerDefinition> connections = new ArrayList<>();
	private IPreferenceStore preferenceStore;

	private LanguageServersRegistry() {
		this.preferenceStore = LanguageServerPlugin.getDefault().getPreferenceStore();
		initialize();
	}

	private void initialize() {
		String prefs = preferenceStore.getString(CONTENT_TYPE_TO_LSP_LAUNCH_PREF_KEY);
		if (prefs != null && !prefs.isEmpty()) {
			String[] entries = prefs.split(","); //$NON-NLS-1$
			for (String entry : entries) {
				ContentTypeToLSPLaunchConfigEntry mapping = ContentTypeToLSPLaunchConfigEntry.readFromPreference(entry);
				if (mapping != null) {
					connections.add(mapping);
				}
			}
		}

		Map<String, LanguageServerDefinition> servers = new HashMap<>();
		List<ContentTypeMapping> contentTypes = new ArrayList<>();
		for (IConfigurationElement extension : Platform.getExtensionRegistry().getConfigurationElementsFor(EXTENSION_POINT_ID)) {
			String id = extension.getAttribute(ID_ATTRIBUTE);
			if (id != null && !id.isEmpty()) {
				if (extension.getName().equals(LS_ELEMENT)) {
					servers.put(id, new ExtensionLanguageServerDefinition(extension));
				} else if (extension.getName().equals(MAPPING_ELEMENT)) {
					IContentType contentType = Platform.getContentTypeManager().getContentType(extension.getAttribute(CONTENT_TYPE_ATTRIBUTE));
					String languageId = extension.getAttribute(LANGUAGE_ID_ATTRIBUTE);

					if (contentType != null) {
						contentTypes.add(new ContentTypeMapping(contentType, id, languageId));
					}
				}
			}
		}

		for (ContentTypeMapping mapping : contentTypes) {
			LanguageServerDefinition lsDefinition = servers.get(mapping.id);
			if (lsDefinition != null) {
				registerAssociation(mapping.contentType, lsDefinition, mapping.languageId);
			} else {
				LanguageServerPlugin.logWarning("server '" + mapping.id + "' not available", null); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
	}

	private void persistContentTypeToLaunchConfigurationMapping() {
		StringBuilder builder = new StringBuilder();
		for (ContentTypeToLSPLaunchConfigEntry entry : getContentTypeToLSPLaunches()) {
			entry.appendPreferenceTo(builder);
			builder.append(',');
		}
		if (builder.length() > 0) {
			builder.deleteCharAt(builder.length() - 1);
		}
		this.preferenceStore.setValue(CONTENT_TYPE_TO_LSP_LAUNCH_PREF_KEY, builder.toString());
		if (this.preferenceStore instanceof IPersistentPreferenceStore) {
			try {
				((IPersistentPreferenceStore) this.preferenceStore).save();
			} catch (IOException e) {
				LanguageServerPlugin.logError(e);
			}
		}
	}

	/**
	 * @param contentType
	 * @return the {@link LanguageServerDefinition}s <strong>directly</strong> associated to the given content-type.
	 * This does <strong>not</strong> include the one that match transitively as per content-type hierarchy
	 */
	List<ContentTypeToLanguageServerDefinition> findProviderFor(final @NonNull IContentType contentType) {
		return connections.stream()
			.filter(entry -> entry.getKey().equals(contentType))
			.sorted((mapping1, mapping2) -> {
				// this sort should make that the content-type hierarchy is respected
				// and the most specialized content-type are placed before the more generic ones
				if (mapping1.getKey().isKindOf(mapping2.getKey())) {
					return -1;
				} else if (mapping2.getKey().isKindOf(mapping1.getKey())) {
					return +1;
				}
				// TODO support "priority" attribute, but it's not made public
				return mapping1.getKey().getId().compareTo(mapping2.getKey().getId());
			})
			.collect(Collectors.toList());
	}

	public void registerAssociation(@NonNull IContentType contentType, @NonNull ILaunchConfiguration launchConfig, @NonNull Set<String> launchMode) {
		ContentTypeToLSPLaunchConfigEntry mapping = new ContentTypeToLSPLaunchConfigEntry(contentType, launchConfig, launchMode);
		connections.add(mapping);
		persistContentTypeToLaunchConfigurationMapping();
	}

	public void registerAssociation(@NonNull IContentType contentType, @NonNull LanguageServerDefinition serverDefinition, @Nullable String languageId) {
		if (languageId != null) {
			serverDefinition.registerAssociation(contentType, languageId);
		}

		connections.add(new ContentTypeToLanguageServerDefinition(contentType, serverDefinition));
	}

	public void setAssociations(List<ContentTypeToLSPLaunchConfigEntry> wc) {
		this.connections.removeIf(ContentTypeToLSPLaunchConfigEntry.class::isInstance);
		this.connections.addAll(wc);
		persistContentTypeToLaunchConfigurationMapping();
	}

	public List<ContentTypeToLSPLaunchConfigEntry> getContentTypeToLSPLaunches() {
		return this.connections.stream().filter(ContentTypeToLSPLaunchConfigEntry.class::isInstance).map(ContentTypeToLSPLaunchConfigEntry.class::cast).collect(Collectors.toList());
	}

	public List<ContentTypeToLanguageServerDefinition> getContentTypeToLSPExtensions() {
		return this.connections.stream().filter(mapping -> mapping.getValue() instanceof ExtensionLanguageServerDefinition).collect(Collectors.toList());
	}

	public @Nullable LanguageServerDefinition getDefinition(@NonNull String languageServerId) {
		for (ContentTypeToLanguageServerDefinition mapping : this.connections) {
			if (mapping.getValue().id.equals(languageServerId)) {
				return mapping.getValue();
			}
		}
		return null;
	}

	public boolean canUseLanguageServer(IEditorInput editorInput) {
		for (ContentTypeToLanguageServerDefinition contentType : getContentTypeToLSPExtensions()) {
			if(contentType.getKey().isAssociatedWith(editorInput.getName())) return true;
		}
		return false;
	}

	/**
	 * internal class to capture content-type mappings for language servers
	 */
	private static class ContentTypeMapping {

		@NonNull public final String id;
		@NonNull public final IContentType contentType;
		@Nullable public final String languageId;

		public ContentTypeMapping(@NonNull IContentType contentType, @NonNull String id, @Nullable String languageId) {
			this.contentType = contentType;
			this.id = id;
			this.languageId = languageId;
		}

	}

	/**
	 * @param file
	 * @param serverDefinition
	 * @return whether the given serverDefinition is suitable for the file
	 * @throws CoreException
	 * @throws IOException
	 */
	public boolean matches(@NonNull IFile file, @NonNull LanguageServerDefinition serverDefinition) throws IOException, CoreException {
		IContentTypeManager manager = Platform.getContentTypeManager();
		try (InputStream contents = file.getContents()) {
			Collection<IContentType> fileContentTypes = Arrays.asList(manager.findContentTypeFor(contents, file.getName()));
			for (ContentTypeToLanguageServerDefinition mapping : this.connections) {
				if (mapping.getValue().equals(serverDefinition) && fileContentTypes.contains(mapping.getKey())) {
					return true;
				}
			}
		}
		return false;
	}

}
