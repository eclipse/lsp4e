/*******************************************************************************
 * Copyright (c) 2016-2017 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *  Mickael Istria (Red Hat Inc.) - Introduce LanguageServerDefinition
 *******************************************************************************/
package org.eclipse.lsp4e;

import java.net.URI;
import java.util.AbstractMap.SimpleEntry;

import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4e.LanguageServersRegistry.LanguageServerDefinition;
import org.eclipse.lsp4e.enablement.EnablementTester;

public class ContentTypeToLanguageServerDefinition extends SimpleEntry<IContentType, LanguageServerDefinition> {

	private static final long serialVersionUID = 6002703726009331762L;
	private final @Nullable EnablementTester enablement;

	public ContentTypeToLanguageServerDefinition(IContentType contentType, LanguageServerDefinition provider,
			@Nullable EnablementTester enablement) {
		super(contentType, provider);
		this.enablement = enablement;
	}

	public boolean isEnabled(@Nullable URI uri) {
		return isUserEnabled() && isExtensionEnabled(uri);
	}

	public void setUserEnabled(boolean enabled) {
		LanguageServerPlugin.getDefault().getPreferenceStore().setValue(getPreferencesKey(), String.valueOf(enabled));
	}

	public boolean isUserEnabled() {
		if (LanguageServerPlugin.getDefault().getPreferenceStore().contains(getPreferencesKey())) {
			return LanguageServerPlugin.getDefault().getPreferenceStore().getBoolean(getPreferencesKey());
		}
		return true;
	}

	public boolean isExtensionEnabled(@Nullable URI uri) {
		return enablement != null ? enablement.evaluate(uri) : true;
	}

	public @Nullable EnablementTester getEnablementCondition() {
		return enablement;
	}

	private String getPreferencesKey() {
		return getValue().id + "/" + getKey().getId(); //$NON-NLS-1$
	}

}
