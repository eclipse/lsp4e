/*******************************************************************************
 * Copyright (c) 2022 Avaloq Group AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.test.semanticTokens;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.Arrays;
import java.util.List;

import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextEvent;
import org.eclipse.lsp4e.operations.semanticTokens.StyleRangeHolder;
import org.eclipse.lsp4e.test.AllCleanRule;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.graphics.Color;
import org.junit.Rule;
import org.junit.Test;

public class StyleRangeHolderTest {
	@Rule
	public AllCleanRule clear = new AllCleanRule();

	private static final Color RED = new Color(255, 0, 0);
	private List<StyleRange> originalStyleRanges = Arrays.asList(new StyleRange(0, 4, RED, null), new StyleRange(15, 4, RED, null), new StyleRange(24, 7, RED, null));

	@Test
	public void testAllDocumentRanges() {
		StyleRangeHolder holder = new StyleRangeHolder();
		holder.saveStyles(originalStyleRanges);

		StyleRange[] allDocumentRanges = holder.overlappingRanges(new Region(0, 50));

		assertNotEquals(originalStyleRanges, allDocumentRanges); // styles must be copied
		assertEquals(originalStyleRanges.size(), allDocumentRanges.length);
	}

	@Test
	public void testPartialDocumentRanges() {
		StyleRangeHolder holder = new StyleRangeHolder();
		holder.saveStyles(originalStyleRanges);

		StyleRange[] allDocumentRanges = holder.overlappingRanges(new Region(0, 20)); // only two ranges overlap this region

		assertEquals(2, allDocumentRanges.length);
	}

	@Test
	public void testDocumentChange() {
		StyleRangeHolder holder = new StyleRangeHolder();
		holder.saveStyles(originalStyleRanges);

		TextEvent textEvent = new TextEvent(0, 1, " ", null, new DocumentEvent(), false) {};

		// this will remove the first style and shift the last two
		holder.textChanged(textEvent);

		StyleRange[] noOverlappingRanges = holder.overlappingRanges(new Region(0, 10)); // only one range overlap this region

		assertEquals(0, noOverlappingRanges.length);

		StyleRange[] twoShiftedOverlappingRanges = holder.overlappingRanges(new Region(10, 50)); // only one range overlap this region

		assertEquals(2, twoShiftedOverlappingRanges.length);
		assertEquals(16, twoShiftedOverlappingRanges[0].start);
		assertEquals(4, twoShiftedOverlappingRanges[0].length);
		assertEquals(25, twoShiftedOverlappingRanges[1].start);
		assertEquals(7, twoShiftedOverlappingRanges[1].length);
	}
}
