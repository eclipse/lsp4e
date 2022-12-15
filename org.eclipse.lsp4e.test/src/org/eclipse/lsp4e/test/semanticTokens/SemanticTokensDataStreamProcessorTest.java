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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.eclipse.jface.text.Document;
import org.eclipse.lsp4e.operations.semanticTokens.SemanticTokensDataStreamProcessor;
import org.eclipse.lsp4e.test.AllCleanRule;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.swt.custom.StyleRange;
import org.junit.Rule;
import org.junit.Test;

public class SemanticTokensDataStreamProcessorTest {
	@Rule
	public AllCleanRule clear = new AllCleanRule();

	@Test
	public void testKeyword() throws InterruptedException, ExecutionException {
		Document document = new Document(SemanticTokensTestUtil.keywordText);

		SemanticTokensDataStreamProcessor processor = new SemanticTokensDataStreamProcessor(SemanticTokensTestUtil
				.keywordTokenTypeMapper(SemanticTokensTestUtil.RED_TOKEN), SemanticTokensTestUtil.offsetMapper(document));

		List<Integer> expectedStream = SemanticTokensTestUtil.keywordSemanticTokens();
		List<StyleRange> expectedStyleRanges = Arrays.asList(//
				new StyleRange(0, 4, SemanticTokensTestUtil.RED, null), //
				new StyleRange(15, 4, SemanticTokensTestUtil.RED, null), //
				new StyleRange(24, 7, SemanticTokensTestUtil.RED, null)//
				);

		List<StyleRange> styleRanges = processor.getStyleRanges(expectedStream, getSemanticTokensLegend());

		assertEquals(expectedStyleRanges, styleRanges);
	}

	private SemanticTokensLegend getSemanticTokensLegend() {
		SemanticTokensLegend semanticTokensLegend = new SemanticTokensLegend();
		semanticTokensLegend.setTokenTypes(Arrays.asList("keyword","other"));
		semanticTokensLegend.setTokenModifiers(Arrays.asList("obsolete"));
		return semanticTokensLegend;
	}
}
