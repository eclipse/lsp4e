/*******************************************************************************
 * Copyright (c) 2017, 2023 TypeFox and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Jan Koehnlein (TypeFox) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.outline;

import static org.junit.Assert.*;

import org.eclipse.lsp4e.outline.SymbolsLabelProvider;
import org.eclipse.lsp4e.outline.SymbolsModel;
import org.eclipse.lsp4e.test.AllCleanRule;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.junit.Rule;
import org.junit.Test;

public class SymbolsLabelProviderTest {

	@Rule public AllCleanRule clear = new AllCleanRule();
	private static final Location LOCATION = new Location("path/to/foo", new Range(new Position(0,0), new Position(1,1)));
	private static final Location INVALID_LOCATION = new Location("file:://///invalid_location_uri", new Range(new Position(0,0), new Position(1,1)));

	@Test
	public void testShowKind() {
		SymbolsLabelProvider labelProvider = new SymbolsLabelProvider(false, true);
		SymbolInformation info = new SymbolInformation("Foo", SymbolKind.Class, LOCATION);
		assertEquals("Foo :Class", labelProvider.getText(info));
	}

	@Test
	public void testShowKindLocation() {
		SymbolsLabelProvider labelProvider = new SymbolsLabelProvider(true, true);
		SymbolInformation info = new SymbolInformation("Foo", SymbolKind.Class, LOCATION);
		assertEquals("Foo :Class path/to/foo", labelProvider.getText(info));
	}

	@Test
	public void testShowLocation() {
		SymbolsLabelProvider labelProvider = new SymbolsLabelProvider(true, false);
		SymbolInformation info = new SymbolInformation("Foo", SymbolKind.Class, LOCATION);
		assertEquals("Foo path/to/foo", labelProvider.getText(info));
	}

	@Test
	public void testShowNeither() {
		SymbolsLabelProvider labelProvider = new SymbolsLabelProvider(false, false);
		SymbolInformation info = new SymbolInformation("Foo", SymbolKind.Class, LOCATION);
		assertEquals("Foo", labelProvider.getText(info));
	}

	@Test
	public void testGetStyledTextInalidLocationURI() {
		SymbolsLabelProvider labelProvider = new SymbolsLabelProvider(false, false);
		SymbolInformation info = new SymbolInformation("Foo", SymbolKind.Class, INVALID_LOCATION);
		assertEquals("Foo", labelProvider.getStyledText(info).getString());
	}

	@Test
	public void testDocumentSymbolDetail () {
		SymbolsLabelProvider labelProvider = new SymbolsLabelProvider(false, false);
		DocumentSymbol info = new DocumentSymbol("Foo", SymbolKind.Class,
				new Range(new Position(1, 0), new Position(1, 2)),
				new Range(new Position(1, 0), new Position(1, 2)),
				": additional detail");
		assertEquals("Foo : additional detail", labelProvider.getStyledText(info).getString());
	}

	@Test
	public void testDocumentSymbolDetailWithKind () {
		SymbolsLabelProvider labelProvider = new SymbolsLabelProvider(false, true);
		DocumentSymbol info = new DocumentSymbol("Foo", SymbolKind.Class,
				new Range(new Position(1, 0), new Position(1, 2)),
				new Range(new Position(1, 0), new Position(1, 2)),
				": additional detail");
		assertEquals("Foo : additional detail :Class", labelProvider.getStyledText(info).getString());
	}

	@Test
	public void testDocumentSymbolWithUriDetail () {
		SymbolsLabelProvider labelProvider = new SymbolsLabelProvider(false, false);
		DocumentSymbol info = new DocumentSymbol("Foo", SymbolKind.Class,
				new Range(new Position(1, 0), new Position(1, 2)),
				new Range(new Position(1, 0), new Position(1, 2)),
				": additional detail");
		final var symbolWithURI = new SymbolsModel.DocumentSymbolWithURI(info, null);
		assertEquals("Foo : additional detail", labelProvider.getStyledText(symbolWithURI).getString());
	}

	@Test
	public void testDocumentSymbolDetailWithFileWithKind () {
		SymbolsLabelProvider labelProvider = new SymbolsLabelProvider(false, true);
		DocumentSymbol info = new DocumentSymbol("Foo", SymbolKind.Class,
				new Range(new Position(1, 0), new Position(1, 2)),
				new Range(new Position(1, 0), new Position(1, 2)),
				": additional detail");
		final var symbolWithURI = new SymbolsModel.DocumentSymbolWithURI(info, null);
		assertEquals("Foo : additional detail :Class", labelProvider.getStyledText(symbolWithURI).getString());
	}

	@Test
	public void testDocumentSymbolDetailWithFileWithKindDeprecated () {
		SymbolsLabelProvider labelProvider = new SymbolsLabelProvider(false, true);
		DocumentSymbol info = new DocumentSymbol("Foo", SymbolKind.Class,
				new Range(new Position(1, 0), new Position(1, 2)),
				new Range(new Position(1, 0), new Position(1, 2)),
				": additional detail");
		info.setDeprecated(true);
		final var symbolWithURI = new SymbolsModel.DocumentSymbolWithURI(info, null);
		assertEquals("Foo : additional detail :Class", labelProvider.getStyledText(symbolWithURI).getString());
		assertTrue(labelProvider.getStyledText(symbolWithURI).getStyleRanges()[0].strikeout);
	}
}
