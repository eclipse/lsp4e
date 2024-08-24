/*******************************************************************************
 * Copyright (c) 2023 Bachmann electronic GmbH and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * Gesa Hentschke (Bachmann electronic GmbH) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.typeHierarchy;


import static org.eclipse.lsp4e.internal.ArrayUtil.NO_OBJECTS;

import java.net.URI;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.lsp4e.outline.SymbolsModel.DocumentSymbolWithURI;
import org.eclipse.lsp4j.DocumentSymbol;

public class TypeMemberContentProvider implements IStructuredContentProvider {

	@Override
	public Object[] getElements(@Nullable Object inputElement) {
		if (inputElement instanceof DocumentSymbolWithURI symbolContainer) {
			return toContainer(symbolContainer.symbol.getChildren(), symbolContainer.uri);
		}
		return NO_OBJECTS;
	}

	private Object[] toContainer(@Nullable List<DocumentSymbol> symbols, URI uri) {
		if (symbols != null) {
			var container = new DocumentSymbolWithURI[symbols.size()];
			for (int i = 0; i < symbols.size(); i++) {
				container[i] = new DocumentSymbolWithURI(symbols.get(i), uri);
			}
			return container;
		}
		return NO_OBJECTS;
	}

}
