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
package org.eclipse.languageserver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.preference.IPersistentPreferenceStore;
import org.eclipse.jface.preference.IPreferenceStore;

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

	private static final String EXTENSION_POINT_ID = LanguageServerPluginActivator.PLUGIN_ID + ".languageServer"; //$NON-NLS-1$

	private static final String LS_ELEMENT = "server"; //$NON-NLS-1$
	private static final String MAPPING_ELEMENT = "contentTypeMapping"; //$NON-NLS-1$

	private static final String ID_ATTRIBUTE = "id"; //$NON-NLS-1$
	private static final String CONTENT_TYPE_ATTRIBUTE = "contentType"; //$NON-NLS-1$
	private static final String CLASS_ATTRIBUTE = "class"; //$NON-NLS-1$

	private static LSPStreamConnectionProviderRegistry INSTANCE = null;
	public static LSPStreamConnectionProviderRegistry getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new LSPStreamConnectionProviderRegistry();
		}
		return INSTANCE;
	}

	private List<ContentTypeToStreamProvider> connections = new ArrayList<>();
	private IPreferenceStore preferenceStore;

	private LSPStreamConnectionProviderRegistry() {
		this.preferenceStore = LanguageServerPluginActivator.getDefault().getPreferenceStore();
		initialize();
	}

	private void initialize() {
		String prefs = preferenceStore.getString(CONTENT_TYPE_TO_LSP_LAUNCH_PREF_KEY);
		if (prefs != null && !prefs.isEmpty()) {
			String[] entries = prefs.split(","); //$NON-NLS-1$
			for (String entry : entries) {
				ContentTypeToStreamProvider mapping = ContentTypeToLSPLaunchConfigEntry.readFromPreference(entry);
				if (mapping != null) {
					connections.add(mapping);
				}
			}
		}

		Map<String, StreamConnectionProvider> servers = new HashMap<>();
		Map<IContentType, String> contentTypes = new HashMap<>();
		for (IConfigurationElement extension : Platform.getExtensionRegistry().getConfigurationElementsFor(EXTENSION_POINT_ID)) {
			String id = extension.getAttribute(ID_ATTRIBUTE);
			if (id != null && !id.isEmpty()) {
				if (extension.getName().equals(LS_ELEMENT)) {
					try {
						StreamConnectionProvider scp = (StreamConnectionProvider) extension.createExecutableExtension(CLASS_ATTRIBUTE);
						if (scp != null) {
							servers.put(id, scp);
						}
					} catch (CoreException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} else if (extension.getName().equals(MAPPING_ELEMENT)) {
					IContentType contentType = Platform.getContentTypeManager().getContentType(extension.getAttribute(CONTENT_TYPE_ATTRIBUTE));
					if (contentType != null) {
						contentTypes.put(contentType, id);
					}
				}
			}
		}
		for (IContentType contentType : contentTypes.keySet()) {
			StreamConnectionProvider scp = servers.get(contentTypes.get(contentType));
			if (scp != null) {
				registerAssociation(contentType, scp);
			} else {
				// TODO log message about missing server
			}
		}
	}

	private void persist() {
		StringBuilder builder = new StringBuilder();
		for (ContentTypeToStreamProvider entry : connections) {
			entry.appendTo(builder);
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
				e.printStackTrace();
			}
		}
	}

	public List<StreamConnectionProvider> findProviderFor(final IContentType contentType) {
		return Arrays.asList(connections
			.stream()
			.filter(entry -> { return entry.getContentType().equals(contentType); })
			.map(entry -> { return entry.getStreamConnectionProvider(); })
			.toArray(StreamConnectionProvider[]::new));
	}

	public void registerAssociation(@NonNull IContentType contentType, @NonNull ILaunchConfiguration launchConfig, @NonNull Set<String> launchMode) {
		connections.add(new ContentTypeToLSPLaunchConfigEntry(contentType, launchConfig, launchMode));
		persist();
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
		persist();
	}

}
