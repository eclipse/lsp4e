/*******************************************************************************
 * Copyright (c) 2016 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.symbols;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.lsp4e.outline.SymbolsModel;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
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
		List<Either<SymbolInformation, DocumentSymbol>> eitherItems = new ArrayList<>(items.size());
		items.forEach(item -> eitherItems.add(Either.forLeft(item)));
		symbolsModel.update(eitherItems);

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

	/**
	 * When a symbol and its child have matching starting points, ensure that the
	 * child is marked as such and not a new parent
	 */
	@Test
	public void testSymbolsMatchingStartingPositions() {
		List<SymbolInformation> items = new ArrayList<>();
		Range range = new Range(new Position(0, 0), new Position(10, 0));
		items.add(createSymbolInformation("Namespace", SymbolKind.Namespace, range));

		range = new Range(new Position(0, 0), new Position(9, 0));
		items.add(createSymbolInformation("Class", SymbolKind.Class, range));

		range = new Range(new Position(1, 0), new Position(8, 0));
		items.add(createSymbolInformation("Method", SymbolKind.Method, range));

		SymbolsModel symbolsModel = new SymbolsModel();
		List<Either<SymbolInformation, DocumentSymbol>> eitherItems = new ArrayList<>(items.size());
		items.forEach(item -> eitherItems.add(Either.forLeft(item)));
		symbolsModel.update(eitherItems);

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

	/**
	 * Confirms that duplicate items do not become children of themselves
	 */
	@Test
	public void testDuplicateSymbols() {
		List<SymbolInformation> items = new ArrayList<>();
		Range range = new Range(new Position(0, 0), new Position(0, 0));
		items.add(createSymbolInformation("Duplicate", SymbolKind.Namespace, range));
		items.add(createSymbolInformation("Duplicate", SymbolKind.Namespace, range));

		SymbolsModel symbolsModel = new SymbolsModel();
		List<Either<SymbolInformation, DocumentSymbol>> eitherItems = new ArrayList<>(items.size());
		items.forEach(item -> eitherItems.add(Either.forLeft(item)));
		symbolsModel.update(eitherItems);

		assertEquals(2, symbolsModel.getElements().length);
		assertEquals(0, symbolsModel.getChildren(symbolsModel.getElements()[0]).length);
		assertEquals(0, symbolsModel.getChildren(symbolsModel.getElements()[1]).length);
	}

	@Test
	public void testGetElementsEmptyResponse() {
		List<SymbolInformation> items = new ArrayList<>();

		SymbolsModel symbolsModel = new SymbolsModel();
		List<Either<SymbolInformation, DocumentSymbol>> eitherItems = new ArrayList<>(items.size());
		items.forEach(item -> eitherItems.add(Either.forLeft(item)));
		symbolsModel.update(eitherItems);

		assertEquals(0, symbolsModel.getElements().length);
	}

	@Test
	public void testGetElementsNullResponse() {
		SymbolsModel symbolsModel = new SymbolsModel();
		symbolsModel.update(null);

		assertEquals(0, symbolsModel.getElements().length);
	}

	@Test
	public void testGetParentEmptyResponse() {
		SymbolsModel symbolsModel = new SymbolsModel();
		symbolsModel.update(Collections.emptyList());

		assertEquals(null, symbolsModel.getParent(null));
	}

	@Test
	public void testGetParentNullResponse() {
		SymbolsModel symbolsModel = new SymbolsModel();
		symbolsModel.update(null);

		assertEquals(null, symbolsModel.getParent(null));
	}

	private SymbolInformation createSymbolInformation(String name, SymbolKind kind, Range range) {
		SymbolInformation symbolInformation = new SymbolInformation();
		symbolInformation.setName(name);
		symbolInformation.setKind(kind);
		symbolInformation.setLocation(new Location("file://test", range));
		return symbolInformation;
	}

}
