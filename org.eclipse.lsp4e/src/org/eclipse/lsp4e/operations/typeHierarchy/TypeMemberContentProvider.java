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

import java.util.List;
import java.util.Optional;

import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.lsp4e.outline.SymbolsModel.DocumentSymbolWithURI;
import org.eclipse.lsp4j.DocumentSymbol;

public class TypeMemberContentProvider implements IStructuredContentProvider {
	private static final Object[] NO_CHILDREN = new Object[0];
	DocumentSymbolWithURI symbolContainer;

	@Override
	final public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		if (newInput instanceof DocumentSymbolWithURI symbolWithURI) {
			symbolContainer = symbolWithURI;
		}
	}

	@Override
	public void dispose() {
		symbolContainer = null;
	}

	@Override
	public Object[] getElements(Object inputElement) {
		return Optional.ofNullable(symbolContainer)
				.map(container -> toContainer(container.symbol.getChildren())).orElse(NO_CHILDREN);
	}

	private Object[] toContainer(List<DocumentSymbol> symbols) {
		if (symbols != null) {
			var container = new DocumentSymbolWithURI[symbols.size()];
			for (int i = 0; i < symbols.size(); i++) {
				container[i] = new DocumentSymbolWithURI(symbols.get(i), symbolContainer.uri);
			}
			return container;
		}
		return NO_CHILDREN;
	}

}
