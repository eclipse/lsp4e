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

public class ContentTypeToLSPLaunchConfigEntry extends ContentTypeToStreamProvider {

	private IContentType contentType;
	private ILaunchConfiguration launchConfiguration;
	private Set<String> launchModes;

	public ContentTypeToLSPLaunchConfigEntry(@NonNull IContentType contentType, @NonNull ILaunchConfiguration launchConfig,
			@NonNull Set<String> launchMode) {
		super(contentType, null);
		this.contentType = contentType;
		this.launchConfiguration = launchConfig;
		this.launchModes = Collections.unmodifiableSet(launchMode);
	}

	public void appendTo(StringBuilder builder) {
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

	@Override
	public IContentType getContentType() {
		return contentType;
	}

	@Override
	public StreamConnectionProvider getStreamConnectionProvider() {
		return new LaunchConfigurationStreamProvider(launchConfiguration, launchModes);
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
		if (launchModes == null) {
			return null;
		}
		if (launchParts.length > 2) {
			launchModes = new HashSet<>(Arrays.asList(launchParts[2].split("\\+"))); //$NON-NLS-1$
		}
		IContentType contentType = Platform.getContentTypeManager().getContentType(contentTypeId);
		if (contentType == null) {
			return null;
		}
		ILaunchConfiguration launchConfiguration = null;
		if (contentType != null) {
			launchConfiguration = LaunchConfigurationStreamProvider.findLaunchConfiguration(launchType, launchName);
		}
		if (launchConfiguration == null) {
			return null;
		}
		return new ContentTypeToLSPLaunchConfigEntry(contentType, launchConfiguration, launchModes);
	}

}
