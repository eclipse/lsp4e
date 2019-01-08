/*******************************************************************************
 * Copyright (c) 2019 Kichwa Coders Ltd. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.debug.launcher;

public class DSPOverrideSettingsTab extends DSPMainTab {

	public DSPOverrideSettingsTab() {
		super(true);
	}

	@Override
	public String getId() {
		return "org.eclipse.lsp4e.debug.launcher.DSPOverrideSettingsTab";
	}
}
