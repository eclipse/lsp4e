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
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.test.utils.AbstractTest;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeKind;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.tests.harness.util.DisplayHelper;
import org.junit.Test;

public class FoldingTest extends AbstractTest {

	private static final String CONTENT = """
		import
		import
		import
		visible
		""";

	@Test
	public void testImportsFoldedByDefaultEnabled() throws CoreException {
		collapseImports(true);
		IEditorPart editor = createEditor();
		DisplayHelper.waitAndAssertCondition(editor.getSite().getShell().getDisplay(), () -> assertEquals("import\nvisible", ((StyledText)editor.getAdapter(Control.class)).getText().trim()));
	}

	@Test
	public void testImportsFoldedByDefaultDisabled() throws CoreException {
		collapseImports(false);
		IEditorPart editor = createEditor();
		DisplayHelper.waitAndAssertCondition(editor.getSite().getShell().getDisplay(), () -> assertEquals(CONTENT, ((StyledText)editor.getAdapter(Control.class)).getText()));
	}

	private IEditorPart createEditor() throws CoreException, PartInitException {
		IFile file = TestUtils.createUniqueTestFile(null, CONTENT);
		FoldingRange foldingRange = new FoldingRange(0, 2);
		foldingRange.setKind(FoldingRangeKind.Imports);
		MockLanguageServer.INSTANCE.setFoldingRanges(List.of(foldingRange));
		IEditorPart editor = TestUtils.openEditor(file);
		return editor;
	}
	
	private void collapseImports(boolean collapseImports) {
		IPreferenceStore store = LanguageServerPlugin.getDefault().getPreferenceStore();
		store.setValue("foldingReconcilingStrategy.collapseImports", true); //$NON-NLS-1$
	}

}
