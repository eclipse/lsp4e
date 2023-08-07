/*******************************************************************************
 * Copyright (c) 2022 Avaloq Group AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.test.semanticTokens;

import static org.junit.Assert.assertArrayEquals;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.lsp4e.test.utils.AllCleanRule;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.tests.harness.util.DisplayHelper;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class SemanticHighlightReconcilerStrategyTest {
	@Rule
	public AllCleanRule clear = new AllCleanRule();

	private IProject project;
	private Shell shell;

	@Before
	public void setUp() throws CoreException {
		project = TestUtils.createProject(getClass().getName() + System.currentTimeMillis());
		shell = new Shell();

		// Setup Server Capabilities
		List<String> tokenTypes = Arrays.asList("keyword");
		List<String> tokenModifiers = Arrays.asList("obsolete");
		SemanticTokensTestUtil.setSemanticTokensLegend(tokenTypes, tokenModifiers);
	}

	@Test
	public void testKeyword() throws InterruptedException, ExecutionException, CoreException {
		SemanticTokens semanticTokens = new SemanticTokens();
		semanticTokens.setData(SemanticTokensTestUtil.keywordSemanticTokens());

		MockLanguageServer.INSTANCE.getTextDocumentService().setSemanticTokens(semanticTokens);

		IFile file = TestUtils.createUniqueTestFile(project, "lspt", SemanticTokensTestUtil.keywordText);
		ITextViewer textViewer = TestUtils.openTextViewer(file);

		Display display = shell.getDisplay();
		DisplayHelper.sleep(display, 2_000); // Give some time to the editor to update

		StyleRange[] styleRanges = textViewer.getTextWidget().getStyleRanges();

		List<StyleRange> expectedStyleRanges = Arrays.asList(//
				new StyleRange(0, 4, SemanticTokensTestUtil.GREEN, null), //
				new StyleRange(15, 4, SemanticTokensTestUtil.GREEN, null), //
				new StyleRange(24, 7, SemanticTokensTestUtil.GREEN, null)//
		);
		assertArrayEquals(expectedStyleRanges.toArray(), styleRanges);
	}
}
