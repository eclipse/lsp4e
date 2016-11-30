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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
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

	public static class ContentTypeToLSPLaunchConfigEntry {
		public IContentType contentType;
		public ILaunchConfiguration launchConfiguration;
		public Set<String> launchModes;
		
		public ContentTypeToLSPLaunchConfigEntry(@NonNull IContentType contentType,
		        @NonNull ILaunchConfiguration launchConfig, @NonNull Set<String> launchMode) {
			this.contentType = contentType;
			this.launchConfiguration = launchConfig;
			this.launchModes = Collections.unmodifiableSet(launchMode);
		}
		
		private ContentTypeToLSPLaunchConfigEntry() {
		}

		static ContentTypeToLSPLaunchConfigEntry readFromPreference(String preferenceEntry) {
			ContentTypeToLSPLaunchConfigEntry res = new ContentTypeToLSPLaunchConfigEntry();
			String[] parts = preferenceEntry.split(":"); //$NON-NLS-1$
			String contentTypeId = parts[0];
			String[] launchParts = parts[1].split("/"); //$NON-NLS-1$
			String launchType = launchParts[0];
			String launchName = launchParts[1];
			res.launchModes = Collections.singleton(ILaunchManager.RUN_MODE);
			if (launchParts.length > 2) {
				res.launchModes = new HashSet<>(Arrays.asList(launchParts[2].split("\\+"))); //$NON-NLS-1$
			}
			res.contentType = Platform.getContentTypeManager().getContentType(contentTypeId);
			if (res.contentType != null) {
				res.launchConfiguration = LaunchConfigurationStreamProvider.findLaunchConfiguration(launchType, launchName);
			}
			return res;
		}

		void appendTo(StringBuilder builder) {
			builder.append(contentType.getId());
			builder.append(':');
			try {
				builder.append(launchConfiguration.getType().getIdentifier());
			} catch (CoreException e) {
				e.printStackTrace();
			}
			builder.append('/');
			builder.append(launchConfiguration.getName());
			builder.append('/');
			for (String launchMode : launchModes) {
				builder.append(launchMode);
				builder.append('+');
			}
			builder.deleteCharAt(builder.length() - 1);
		}
	}
	
	private static LSPStreamConnectionProviderRegistry INSTANCE = null;
	public static LSPStreamConnectionProviderRegistry getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new LSPStreamConnectionProviderRegistry();
		}
		return INSTANCE;
	}

	private List<ContentTypeToLSPLaunchConfigEntry> connections = new ArrayList<>();
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
				ContentTypeToLSPLaunchConfigEntry mapping = ContentTypeToLSPLaunchConfigEntry.readFromPreference(entry);
				if (mapping.contentType != null && mapping.launchConfiguration != null && mapping.launchModes != null) {
					connections.add(mapping);
				}
			}
		}
	}
	
	private void persist() {
		StringBuilder builder = new StringBuilder();
		for (ContentTypeToLSPLaunchConfigEntry entry : connections) {
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
			.filter(entry -> { return entry.contentType.equals(contentType); })
			.map(entry -> { return new LaunchConfigurationStreamProvider(entry.launchConfiguration, entry.launchModes); })
			.toArray(StreamConnectionProvider[]::new));
	}
	
	public void registerAssociation(@NonNull IContentType contentType, @NonNull ILaunchConfiguration launchConfig, @NonNull Set<String> launchMode) {
		connections.add(new ContentTypeToLSPLaunchConfigEntry(contentType, launchConfig, launchMode));
		persist();
	}

	public List<ContentTypeToLSPLaunchConfigEntry> getContentTypeToLSPLaunches() {
		return Collections.unmodifiableList(this.connections);
	}

	public void setAssociations(List<ContentTypeToLSPLaunchConfigEntry> wc) {
		this.connections.clear();
		this.connections.addAll(wc);
		persist();
	}

}
