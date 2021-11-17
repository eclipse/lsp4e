/*******************************************************************************
 * Copyright (c) 2021 Vegard IT GmbH and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Sebastian Thomschke (Vegard IT GmbH) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.outline;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.ui.handlers.HandlerUtil;

public class ToggleSortOutlineHandler extends AbstractHandler {

	@Override
	public Object execute(final ExecutionEvent event) throws ExecutionException {
		final boolean oldValue = HandlerUtil.toggleCommandState(event.getCommand());
		final IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(LanguageServerPlugin.PLUGIN_ID);
		prefs.putBoolean(CNFOutlinePage.SORT_OUTLINE_PREFERENCE, !oldValue);
		return null;
	}
}
