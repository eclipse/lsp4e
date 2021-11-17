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

import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.lsp4e.outline.SymbolsModel.DocumentSymbolWithFile;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class OutlineSorter extends ViewerComparator {

	public static final OutlineSorter INSTANCE = new OutlineSorter();

	@Override
	public int compare(final Viewer viewer, Object o1, Object o2) {

		if (o1 instanceof Either && o2 instanceof Either) {
			o1 = ((Either<?, ?>) o1).get();
			o2 = ((Either<?, ?>) o2).get();
		}

		String name1 = null;
		String name2 = null;

		if (o1 instanceof DocumentSymbolWithFile) {
			name1 = ((DocumentSymbolWithFile) o1).symbol.getName();
		} else if (o1 instanceof DocumentSymbol) {
			name1 = ((DocumentSymbol) o1).getName();
		} else if (o1 instanceof SymbolInformation) {
			name1 = ((SymbolInformation) o1).getName();
		}
		if (o2 instanceof DocumentSymbolWithFile) {
			name2 = ((DocumentSymbolWithFile) o2).symbol.getName();
		} else if (o2 instanceof DocumentSymbol) {
			name2 = ((DocumentSymbol) o2).getName();
		} else if (o2 instanceof SymbolInformation) {
			name2 = ((SymbolInformation) o2).getName();
		}

		if (name1 == null || name2 == null)
			return 0;
		return name1.compareTo(name2);
	}
}
