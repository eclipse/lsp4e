/*******************************************************************************
 * Copyright (c) 2016, 2017 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.lsp4e.LanguageServersRegistry.LaunchConfigurationLanguageServerDefinition;

public class ContentTypeToLSPLaunchConfigEntry extends ContentTypeToLanguageServerDefinition {

	private static final long serialVersionUID = 7944251280308498957L;

	private final ILaunchConfiguration launchConfiguration;
	private final Set<String> launchModes;

	public ContentTypeToLSPLaunchConfigEntry(@NonNull IContentType contentType, @NonNull ILaunchConfiguration launchConfig,
			@NonNull Set<String> launchModes) {
		super(contentType, new LaunchConfigurationLanguageServerDefinition(launchConfig, launchModes), null);
		this.launchConfiguration = launchConfig;
		this.launchModes = Collections.unmodifiableSet(launchModes);
	}

	public void appendPreferenceTo(StringBuilder builder) {
		builder.append(getKey().getId());
		builder.append(':');
		try {
			builder.append(launchConfiguration.getType().getIdentifier());
		} catch (CoreException e) {
			LanguageServerPlugin.logError(e);
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

	public ILaunchConfiguration getLaunchConfiguration() {
		return launchConfiguration;
	}

	public Set<String> getLaunchModes() {
		return launchModes;
	}

	static ContentTypeToLSPLaunchConfigEntry readFromPreference(String preferenceEntry) {
		String[] parts = preferenceEntry.split(":"); //$NON-NLS-1$
		if (parts.length != 2) {
			return null;
		}
		String contentTypeId = parts[0];
		String[] launchParts = parts[1].split("/"); //$NON-NLS-1$
		String launchType = launchParts[0];
		String launchName = launchParts[1];
		Set<String> launchModes = Collections.singleton(ILaunchManager.RUN_MODE);
		if (launchParts.length > 2) {
			launchModes = new HashSet<>(Arrays.asList(launchParts[2].split("\\+"))); //$NON-NLS-1$
		}
		IContentType contentType = Platform.getContentTypeManager().getContentType(contentTypeId);
		if (contentType == null) {
			return null;
		}
		ILaunchConfiguration launchConfiguration = LaunchConfigurationStreamProvider.findLaunchConfiguration(launchType, launchName);
		if (launchConfiguration == null) {
			return null;
		}
		return new ContentTypeToLSPLaunchConfigEntry(contentType, launchConfiguration, launchModes);
	}

}
