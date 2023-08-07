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
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.operations.semanticTokens.SemanticHighlightReconcilerStrategy;
import org.eclipse.lsp4e.test.utils.AllCleanRule;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class SemanticTokensLegendProviderTest {

	@Rule
	public AllCleanRule clear = new AllCleanRule();

	private IProject project;

	@Before
	public void setUp() throws CoreException {
		project = TestUtils.createProject(getClass().getName() + System.currentTimeMillis());
	}

	@Test
	public void testSemanticTokensLegendProvider() throws BadLocationException, CoreException, IOException, InterruptedException, ExecutionException {
		// Setup Server Capabilities
		List<String> tokenTypes = Arrays.asList("keyword","other");
		List<String> tokenModifiers = Arrays.asList("obsolete");
		SemanticTokensTestUtil.setSemanticTokensLegend(tokenTypes, tokenModifiers);

		// Setup test data
		IFile file = TestUtils.createUniqueTestFile(project, "lspt", "test content");
		// start the LS
		LanguageServerWrapper wrapper = LanguageServiceAccessor.getLSWrappers(file, c -> Boolean.TRUE).iterator()
		.next();

		SemanticTokensLegend semanticTokensLegend = new SemanticHighlightReconcilerStrategy().getSemanticTokensLegend(wrapper);
		assertNotNull(semanticTokensLegend);
		assertEquals(tokenTypes, semanticTokensLegend.getTokenTypes());
		assertEquals(tokenModifiers, semanticTokensLegend.getTokenModifiers());
	}
}
