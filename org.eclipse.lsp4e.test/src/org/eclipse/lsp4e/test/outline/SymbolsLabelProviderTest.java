/*******************************************************************************
 * Copyright (c) 2017 TypeFox and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Jan Koehnlein (TypeFox) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.outline;

import static org.junit.Assert.assertEquals;

import org.eclipse.lsp4e.outline.SymbolsLabelProvider;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.junit.Test;

public class SymbolsLabelProviderTest {

	private static final Location LOCATION = new Location("path/to/foo", new Range(new Position(0,0), new Position(1,1)));
	
	@Test
	public void testShowKind() {
		SymbolsLabelProvider labelProvider = new SymbolsLabelProvider();
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
}
