/*******************************************************************************
 * Copyright (c) 2023 Red Hat, Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.test.folding;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.lsp4e.test.utils.AllCleanRule;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeKind;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.tests.harness.util.DisplayHelper;
import org.junit.Rule;
import org.junit.Test;

public class FoldingTest {

	@Rule
	public final AllCleanRule clear = new AllCleanRule();

	@Test
	public void testImportsFoldedByDefault() throws CoreException {
		IFile file = TestUtils.createUniqueTestFile(null, """
			import
			import
			import
			visible
			""");
		FoldingRange foldingRange = new FoldingRange(0, 2);
		foldingRange.setKind(FoldingRangeKind.Imports);
		MockLanguageServer.INSTANCE.setFoldingRanges(List.of(foldingRange));
		IEditorPart editor = TestUtils.openEditor(file);
		DisplayHelper.waitAndAssertCondition(editor.getSite().getShell().getDisplay(), () -> assertEquals("import\nvisible", ((StyledText)editor.getAdapter(Control.class)).getText().trim()));
	}
}
