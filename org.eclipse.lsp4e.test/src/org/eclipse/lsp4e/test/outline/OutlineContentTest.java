/*******************************************************************************
 * Copyright (c) 2020 Red Hat Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.lsp4e.test.outline;

import static org.eclipse.lsp4e.test.TestUtils.*;
import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.ui.tests.harness.util.DisplayHelper;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.outline.CNFOutlinePage;
import org.eclipse.lsp4e.outline.SymbolsModel.DocumentSymbolWithFile;
import org.eclipse.lsp4e.test.AllCleanRule;
import org.eclipse.lsp4e.test.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.texteditor.ITextEditor;
import org.junit.Rule;
import org.junit.Test;

public class OutlineContentTest {

	@Rule
	public AllCleanRule rule = new AllCleanRule();

	@Test
	public void testOutlineSorting() throws CoreException {
		IProject project = TestUtils
				.createProject("OutlineContentTest_testOutlineSorting" + System.currentTimeMillis());
		try {

			IFile testFile = TestUtils.createUniqueTestFile(project, "content does not matter");

			DocumentSymbol symbolCow = new DocumentSymbol("cow", SymbolKind.Constant,
					new Range(new Position(0, 0), new Position(0, 2)),
					new Range(new Position(0, 0), new Position(0, 2)));
			DocumentSymbol symbolFox = new DocumentSymbol("fox", SymbolKind.Constant,
					new Range(new Position(1, 0), new Position(1, 2)),
					new Range(new Position(1, 0), new Position(1, 2)));
			DocumentSymbol symbolCat = new DocumentSymbol("cat", SymbolKind.Constant,
					new Range(new Position(2, 0), new Position(2, 2)),
					new Range(new Position(2, 0), new Position(2, 2)));

			MockLanguageServer.INSTANCE.setDocumentSymbols(symbolCow, symbolFox, symbolCat);

			// ensure outline sorting is disabled
			IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(LanguageServerPlugin.PLUGIN_ID);
			prefs.putBoolean(CNFOutlinePage.SORT_OUTLINE_PREFERENCE, false);

			ITextEditor editor = (ITextEditor) TestUtils.openEditor(testFile);
			CNFOutlinePage outlinePage = new CNFOutlinePage(MockLanguageServer.INSTANCE, editor);
			Shell shell = new Shell(editor.getEditorSite().getWorkbenchWindow().getShell());
			shell.setLayout(new FillLayout());
			outlinePage.createControl(shell);
			shell.open();
			Tree tree = (Tree) outlinePage.getControl();

			// wait for tree to render
			assertTrue(waitForCondition(5_000, tree.getDisplay(), //
					() -> Arrays.asList(symbolCow, symbolFox, symbolCat) //
							.equals(Arrays.stream(tree.getItems())
									.map(e -> ((DocumentSymbolWithFile) e.getData()).symbol)
									.collect(Collectors.toList())) //
			));

			// enable outline sorting
			prefs.putBoolean(CNFOutlinePage.SORT_OUTLINE_PREFERENCE, true);

			// wait for tree being sorted
			assertTrue(waitForCondition(5_000, tree.getDisplay(), //
					() -> Arrays.asList(symbolCat, symbolCow, symbolFox) //
							.equals(Arrays.stream(tree.getItems())
									.map(e -> ((DocumentSymbolWithFile) e.getData()).symbol)
									.collect(Collectors.toList())) //
			));

			shell.close();
		} finally {
			TestUtils.delete(project);
		}
	}

	@Test
	public void testNodeRemainExpandedUponSelection() throws CoreException {
		IProject project = TestUtils
				.createProject("OutlineContentTest_testNodeRemainExpandedUponSelection" + System.currentTimeMillis());
		try {
			IFile testFile = TestUtils.createUniqueTestFile(project, "a(b())");
			MockLanguageServer.INSTANCE.setDocumentSymbols(
					new DocumentSymbol("a", SymbolKind.Constant, new Range(new Position(0, 0), new Position(0, 6)),
							new Range(new Position(0, 0), new Position(0, 1)), "",
							Collections.singletonList(new DocumentSymbol("b", SymbolKind.Constant,
									new Range(new Position(0, 2), new Position(0, 5)),
									new Range(new Position(0, 2), new Position(0, 3))))));
			ITextEditor editor = (ITextEditor) TestUtils.openEditor(testFile);
			CNFOutlinePage outlinePage = new CNFOutlinePage(MockLanguageServer.INSTANCE, editor);
			Shell shell = new Shell(editor.getEditorSite().getWorkbenchWindow().getShell());
			shell.setLayout(new FillLayout());
			outlinePage.createControl(shell);
			shell.open();
			Tree tree = (Tree) outlinePage.getControl();
			DisplayHelper.sleep(tree.getDisplay(), 500);

			editor.getSelectionProvider().setSelection(new TextSelection(4, 0));
			assertTrue(waitForCondition(2_000, tree.getDisplay(), //
					() -> itemBselectedAndVisibile(tree) //
			));

			editor.getSelectionProvider().setSelection(new TextSelection(3, 0));

			// ensure that selection remains intact for 2 seconds at least (i.e. not lost)
			assertFalse(waitForCondition(2_000, tree.getDisplay(), //
					() -> !itemBselectedAndVisibile(tree) //
			));

			shell.close();
		} finally {
			TestUtils.delete(project);
		}
	}

	@Test
	public void testNodeRemainExpandedUponModification() throws CoreException, BadLocationException {
		IProject project = TestUtils.createProject(
				"OutlineContentTest_testNodeRemainExpandedUponModification" + System.currentTimeMillis());
		try {
			IFile testFile = TestUtils.createUniqueTestFile(project, "a(b())");
			MockLanguageServer.INSTANCE.setDocumentSymbols(
					new DocumentSymbol("a", SymbolKind.Constant, new Range(new Position(0, 0), new Position(0, 6)),
							new Range(new Position(0, 0), new Position(0, 1)), "",
							Collections.singletonList(new DocumentSymbol("b", SymbolKind.Constant,
									new Range(new Position(0, 2), new Position(0, 5)),
									new Range(new Position(0, 2), new Position(0, 3))))));
			ITextEditor editor = (ITextEditor) TestUtils.openEditor(testFile);
			CNFOutlinePage outlinePage = new CNFOutlinePage(MockLanguageServer.INSTANCE, editor);
			Shell shell = new Shell(editor.getEditorSite().getWorkbenchWindow().getShell());
			shell.setLayout(new FillLayout());
			outlinePage.createControl(shell);
			shell.open();
			Tree tree = (Tree) outlinePage.getControl();
			DisplayHelper.sleep(tree.getDisplay(), 500);

			editor.getSelectionProvider().setSelection(new TextSelection(4, 0));
			assertTrue(waitForCondition(2_000, tree.getDisplay(), //
					() -> tree.getItems().length > 0 && tree.getItem(0).getExpanded() //
			));

			IDocument document = editor.getDocumentProvider().getDocument(editor.getEditorInput());
			editor.selectAndReveal(document.getLength(), 0);
			document.replace(document.getLength(), 0, "   ");

			// ensure that tree remains expanded (for at least 2 seconds)
			assertFalse(waitForCondition(2_000, tree.getDisplay(), //
					() -> !tree.getItem(0).getExpanded() //
			));

			shell.close();
		} finally {
			TestUtils.delete(project);
		}
	}

	private boolean itemBselectedAndVisibile(Tree tree) {
		if (tree.getSelection().length == 0) {
			return false;
		}
		TreeItem selection = tree.getSelection()[0];
		return selection != null && selection.getText().equals("b") && selection.getParentItem().getExpanded();
	}
}
