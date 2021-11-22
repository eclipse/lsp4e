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

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.outline.SymbolsModel.DocumentSymbolWithFile;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class OutlineSorter extends ViewerComparator {

	protected final IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(LanguageServerPlugin.PLUGIN_ID);

	@Override
	public int compare(final Viewer viewer, final Object o1, final Object o2) {
		if (!isSortingEnabled())
			return 0;

		final String name1 = getName(o1);
		final String name2 = getName(o2);

		if (name1 == null)
			return name2 == null ? 0 : -1;

		if (name2 == null)
			return 1;

		return name1.compareTo(name2);
	}

	private String getName(Object element) {
		if (element instanceof Either) {
			element = ((Either<?, ?>) element).get();
		}
		if (element instanceof DocumentSymbolWithFile) {
			return ((DocumentSymbolWithFile) element).symbol.getName();
		}
		if (element instanceof DocumentSymbol) {
			return ((DocumentSymbol) element).getName();
		}
		if (element instanceof SymbolInformation) {
			return ((SymbolInformation) element).getName();
		}
		return null;
	}

	@Override
	public boolean isSorterProperty(final Object element, final String property) {
		return "name".equals(property); //$NON-NLS-1$
	}

	public boolean isSortingEnabled() {
		return prefs.getBoolean(CNFOutlinePage.SORT_OUTLINE_PREFERENCE, false);
	}
}
