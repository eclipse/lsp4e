/*******************************************************************************
 * Copyright (c) 2020 Red Hat Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.lsp4e.test.outline;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.text.tests.util.DisplayHelper;
import org.eclipse.lsp4e.outline.CNFOutlinePage;
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
	public void testNodeRemainExpandedUponSelection() throws CoreException {
		IProject project = TestUtils.createProject("EditorToOutlineAdapterFactoryTest" + System.currentTimeMillis());
		IFile testFile = TestUtils.createUniqueTestFile(project, "a(b())");
		MockLanguageServer.INSTANCE.setDocumentSymbols(new DocumentSymbol("a", SymbolKind.Constant, new Range(new Position(0, 0), new Position(0, 6)), new Range(new Position(0, 0), new Position(0, 1)), "", Collections.singletonList(
				new DocumentSymbol("b", SymbolKind.Constant, new Range(new Position(0, 2), new Position(0, 5)), new Range(new Position(0, 2), new Position(0, 3)))
			)));
		ITextEditor editor = (ITextEditor)TestUtils.openEditor(testFile);
		CNFOutlinePage outlinePage = new CNFOutlinePage(MockLanguageServer.INSTANCE, editor);
		Shell shell = new Shell(editor.getEditorSite().getWorkbenchWindow().getShell());
		shell.setLayout(new FillLayout());
		outlinePage.createControl(shell);
		shell.open();
		Tree tree = (Tree)outlinePage.getControl();
		editor.getSelectionProvider().setSelection(new TextSelection(4, 0));
		assertTrue(new DisplayHelper() {
			@Override
			protected boolean condition() {
				return itemBselectedAndVisibile(tree);
			}
		}.waitForCondition(tree.getDisplay(), 2000));
		editor.getSelectionProvider().setSelection(new TextSelection(3, 0));
		// ensure that selection remains intact for 2 seconds at least (ie not lost)
		assertFalse(new DisplayHelper() {
			@Override
			protected boolean condition() {
				return !itemBselectedAndVisibile(tree);
			}
		}.waitForCondition(tree.getDisplay(), 2000));
	}

	@Test
	public void testNodeRemainExpandedUponModification() throws CoreException, BadLocationException {
		IProject project = TestUtils.createProject("EditorToOutlineAdapterFactoryTest2" + System.currentTimeMillis());
		IFile testFile = TestUtils.createUniqueTestFile(project, "a(b())");
		MockLanguageServer.INSTANCE.setDocumentSymbols(new DocumentSymbol("a", SymbolKind.Constant, new Range(new Position(0, 0), new Position(0, 6)), new Range(new Position(0, 0), new Position(0, 1)), "", Collections.singletonList(
				new DocumentSymbol("b", SymbolKind.Constant, new Range(new Position(0, 2), new Position(0, 5)), new Range(new Position(0, 2), new Position(0, 3)))
			)));
		ITextEditor editor = (ITextEditor)TestUtils.openEditor(testFile);
		CNFOutlinePage outlinePage = new CNFOutlinePage(MockLanguageServer.INSTANCE, editor);
		Shell shell = new Shell(editor.getEditorSite().getWorkbenchWindow().getShell());
		shell.setLayout(new FillLayout());
		outlinePage.createControl(shell);
		shell.open();
		Tree tree = (Tree)outlinePage.getControl();
		editor.getSelectionProvider().setSelection(new TextSelection(4, 0));
		assertTrue(new DisplayHelper() {
			@Override
			protected boolean condition() {
				return tree.getItems().length > 0 && tree.getItem(0).getExpanded();
			}
		}.waitForCondition(tree.getDisplay(), 2000));
		IDocument document = editor.getDocumentProvider().getDocument(editor.getEditorInput());
		editor.selectAndReveal(document.getLength(), 0);
		document.replace(document.getLength(), 0, "   ");
		// ensure that tree remains expanded (for at least 2 seconds)
		assertFalse(new DisplayHelper() {
			@Override
			protected boolean condition() {
				return !tree.getItem(0).getExpanded();
			}
		}.waitForCondition(tree.getDisplay(), 2000));
	}


	private boolean itemBselectedAndVisibile(Tree tree) {
		if (tree.getSelection().length == 0) {
			return false;
		}
		TreeItem selection = tree.getSelection()[0];
		return selection != null && selection.getText().equals("b") && selection.getParentItem().getExpanded();
	}
}
