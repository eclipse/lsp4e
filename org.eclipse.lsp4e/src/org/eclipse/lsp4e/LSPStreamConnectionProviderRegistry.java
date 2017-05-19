/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
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
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.preference.IPersistentPreferenceStore;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.lsp4e.server.StreamConnectionProvider;

/**
 * This registry aims at providing a good language server connection (as {@link StreamConnectionProvider}
 * for a given input.
 * At the moment, registry content are hardcoded but we'll very soon need a way
 * to contribute to it via plugin.xml (for plugin developers) and from Preferences
 * (for end-users to directly register a new server).
 *
 */
public class LSPStreamConnectionProviderRegistry {

	private static final String CONTENT_TYPE_TO_LSP_LAUNCH_PREF_KEY = "contentTypeToLSPLauch"; //$NON-NLS-1$

	private static final String EXTENSION_POINT_ID = LanguageServerPlugin.PLUGIN_ID + ".languageServer"; //$NON-NLS-1$

	private static final String LS_ELEMENT = "server"; //$NON-NLS-1$
	private static final String MAPPING_ELEMENT = "contentTypeMapping"; //$NON-NLS-1$

	private static final String ID_ATTRIBUTE = "id"; //$NON-NLS-1$
	private static final String CONTENT_TYPE_ATTRIBUTE = "contentType"; //$NON-NLS-1$
	private static final String CLASS_ATTRIBUTE = "class"; //$NON-NLS-1$
	private static final String LABEL_ATTRIBUTE = "label"; //$NON-NLS-1$

	protected static final class StreamConnectionInfo {
		private final @NonNull String id;
		private final @NonNull String label;

		public StreamConnectionInfo(@NonNull String id, @NonNull String label) {
			this.id = id;
			this.label = label;
		}

		public String getId() {
			return id;
		}

		public String getLabel() {
			return label;
		}
	}

	private static LSPStreamConnectionProviderRegistry INSTANCE = null;
	public static LSPStreamConnectionProviderRegistry getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new LSPStreamConnectionProviderRegistry();
		}
		return INSTANCE;
	}

	private List<ContentTypeToStreamProvider> connections = new ArrayList<>();
	private Map<StreamConnectionProvider, StreamConnectionInfo> connectionsInfo = new HashMap<>();
	private IPreferenceStore preferenceStore;

	private LSPStreamConnectionProviderRegistry() {
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
					connectionsInfo.put(mapping.getValue(), new StreamConnectionInfo(mapping.getKey().getId(), mapping.getKey().getName()));
				}
			}
		}

		Map<String, StreamConnectionProvider> servers = new HashMap<>();
		List<Entry<IContentType, String>> contentTypes = new ArrayList<>();
		for (IConfigurationElement extension : Platform.getExtensionRegistry().getConfigurationElementsFor(EXTENSION_POINT_ID)) {
			String id = extension.getAttribute(ID_ATTRIBUTE);
			if (id != null && !id.isEmpty()) {
				if (extension.getName().equals(LS_ELEMENT)) {
					SafeRunner.run(() -> {
						String label = extension.getAttribute(LABEL_ATTRIBUTE);
						StreamConnectionProvider scp = (StreamConnectionProvider) extension.createExecutableExtension(CLASS_ATTRIBUTE);
						if (scp != null) {
							servers.put(id, scp);
							connectionsInfo.put(scp, new StreamConnectionInfo(id, label));
						}
					});
				} else if (extension.getName().equals(MAPPING_ELEMENT)) {
					IContentType contentType = Platform.getContentTypeManager().getContentType(extension.getAttribute(CONTENT_TYPE_ATTRIBUTE));
					if (contentType != null) {
						contentTypes.add(new SimpleEntry<>(contentType, id));
					}
				}
			}
		}
		for (Entry<IContentType, String> entry : contentTypes) {
			IContentType contentType = entry.getKey();
			StreamConnectionProvider scp = servers.get(entry.getValue());
			if (scp != null) {
				registerAssociation(contentType, scp);
			} else {
				LanguageServerPlugin.logWarning("server '" + entry.getValue() + "' not available", null); //$NON-NLS-1$ //$NON-NLS-2$
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

	public List<StreamConnectionProvider> findProviderFor(final IContentType contentType) {
		return Arrays.asList(connections
			.stream()
			.filter(entry -> entry.getKey().equals(contentType))
			.map(Entry::getValue)
			.toArray(StreamConnectionProvider[]::new));
	}

	protected StreamConnectionInfo getInfo(@NonNull StreamConnectionProvider provider) {
		return connectionsInfo.get(provider);
	}

	public void registerAssociation(@NonNull IContentType contentType, @NonNull ILaunchConfiguration launchConfig, @NonNull Set<String> launchMode) {
		ContentTypeToLSPLaunchConfigEntry mapping = new ContentTypeToLSPLaunchConfigEntry(contentType, launchConfig, launchMode);
		connections.add(mapping);
		connectionsInfo.put(mapping.getValue(), new StreamConnectionInfo(mapping.getKey().getId(), mapping.getKey().getName()));
		persistContentTypeToLaunchConfigurationMapping();
	}

	public void registerAssociation(@NonNull IContentType contentType, @NonNull StreamConnectionProvider provider) {
		connections.add(new ContentTypeToStreamProvider(contentType, provider));
	}

	public List<ContentTypeToLSPLaunchConfigEntry> getContentTypeToLSPLaunches() {
		return Arrays.asList(this.connections.stream().filter(element -> element instanceof ContentTypeToLSPLaunchConfigEntry).toArray(size -> new ContentTypeToLSPLaunchConfigEntry[size]));
	}

	public void setAssociations(List<ContentTypeToLSPLaunchConfigEntry> wc) {
		this.connections.removeIf(entry -> entry instanceof ContentTypeToLSPLaunchConfigEntry);
		this.connections.addAll(wc);
		persistContentTypeToLaunchConfigurationMapping();
	}

}
