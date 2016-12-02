/*******************************************************************************
 * Copyright (c) 2016 Rogue Wave Software Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.symbols;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4e.outline.SymbolsModel;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.junit.Test;

public class SymbolsModelTest {

	@Test
	public void test() {
		List<SymbolInformation> items = new ArrayList<>();
		Range range = new Range(new Position(0, 0), new Position(10, 0));
		items.add(createSymbolInformation("Namespace", SymbolKind.Namespace, range));

		range = new Range(new Position(1, 0), new Position(9, 0));
		items.add(createSymbolInformation("Class", SymbolKind.Class, range));

		range = new Range(new Position(2, 0), new Position(8, 0));
		items.add(createSymbolInformation("Method", SymbolKind.Method, range));

		SymbolsModel symbolsModel = new SymbolsModel();
		symbolsModel.update(items);

		assertEquals(1, symbolsModel.getElements().length);
		assertEquals(items.get(0), symbolsModel.getElements()[0]);
		Object[] children = symbolsModel.getChildren(symbolsModel.getElements()[0]);
		assertEquals(1, children.length);
		assertEquals(items.get(1), children[0]);
		children = symbolsModel.getChildren(children[0]);
		assertEquals(1, children.length);
		assertEquals(items.get(2), children[0]);

		Object parent = symbolsModel.getParent(children[0]);
		assertEquals(items.get(1), parent);
		parent = symbolsModel.getParent(parent);
		assertEquals(items.get(0), parent);
	}

	private SymbolInformation createSymbolInformation(String name, SymbolKind kind, Range range) {
		SymbolInformation symbolInformation = new SymbolInformation();
		symbolInformation.setName(name);
		symbolInformation.setKind(kind);
		symbolInformation.setLocation(new Location("file://test", range));
		return symbolInformation;
	}

}
