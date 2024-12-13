/*******************************************************************************
 * Copyright (c) 2024 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.lsp4e.jdt;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

public class LspJdtPrefsInitializer extends AbstractPreferenceInitializer {
	
	public LspJdtPrefsInitializer() {
	}

	@Override
	public void initializeDefaultPreferences() {
		IPreferenceStore store = LanguageServerJdtPlugin.getDefault().getPreferenceStore();
		
		store.setDefault(LspJdtConstants.PREF_SEMANTIC_TOKENS_SWITCH, true);
	}

}
