/*******************************************************************************
 * Copyright (c) 2024 Advantest Europe GmbH. All rights reserved.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Dietrich Travkin (Solunar GmbH) - initial implementation (issue #1073)
 *******************************************************************************/
package org.eclipse.lsp4e.outline;

import java.util.Optional;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.PreferenceChangeEvent;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.handlers.HandlerUtil;

public class ToggleHideFieldsOutlineHandler extends AbstractHandler implements IPreferenceChangeListener {

	private static final String COMMAND_ID_HIDE_FIELDS = "org.eclipse.lsp4e.toggleHideFieldsOutline"; //$NON-NLS-1$

	private final IEclipsePreferences preferences;
	private final Optional<Command> toggleHideFieldsCommand;

	public ToggleHideFieldsOutlineHandler() {
		preferences = InstanceScope.INSTANCE.getNode(LanguageServerPlugin.PLUGIN_ID);
		preferences.addPreferenceChangeListener(this);

		ICommandService commandService = PlatformUI.getWorkbench().getService(ICommandService.class);
		if (commandService != null) {
			toggleHideFieldsCommand = Optional.of(commandService.getCommand(COMMAND_ID_HIDE_FIELDS));
		} else {
			toggleHideFieldsCommand = Optional.empty();
		}
	}

	@Override
	public @Nullable Object execute(final ExecutionEvent event) throws ExecutionException {
		OutlineViewHideSymbolKindMenuContributor.toggleHideSymbolKind(SymbolKind.Field);
		return null;
	}

	@Override
	public void dispose() {
		super.dispose();
		preferences.removePreferenceChangeListener(this);
	}

	@Override
	public void preferenceChange(PreferenceChangeEvent event) {
		if (toggleHideFieldsCommand.isPresent()
				&& event.getKey().startsWith(CNFOutlinePage.HIDE_DOCUMENT_SYMBOL_KIND_PREFERENCE_PREFIX)
				&& event.getKey().endsWith(SymbolKind.Field.name())) {
			try {
				HandlerUtil.toggleCommandState(toggleHideFieldsCommand.get());
			} catch (ExecutionException e) {
				LanguageServerPlugin.logError(e);
			}
		}
	}
}
