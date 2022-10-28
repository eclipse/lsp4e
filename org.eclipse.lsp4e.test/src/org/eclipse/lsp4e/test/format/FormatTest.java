/*******************************************************************************
 * Copyright (c) 2017 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.format;

import static org.eclipse.lsp4e.test.TestUtils.numberOfChangesIs;
import static org.eclipse.lsp4e.test.TestUtils.waitForAndAssertCondition;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.VersionedEdits;
import org.eclipse.lsp4e.operations.format.LSPFormatter;
import org.eclipse.lsp4e.test.AllCleanRule;
import org.eclipse.lsp4e.test.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.texteditor.ITextEditor;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class FormatTest {

	@Rule public AllCleanRule clear = new AllCleanRule();
	private IProject project;

	@Before
	public void setUp() throws CoreException {
		project = TestUtils.createProject(getClass().getName() + System.currentTimeMillis());
	}

	@Test
	public void testFormattingInvalidDocument() throws Exception {
		LSPFormatter formatter = new LSPFormatter();
		ITextSelection selection = TextSelection.emptySelection();

		Optional<VersionedEdits> edits = formatter.requestFormatting(new Document(), selection, null).get();
		assertTrue(edits.isEmpty());
	}

	@Test
	public void testFormattingNoChanges() throws Exception {
		MockLanguageServer.INSTANCE.setFormattingTextEdits(Collections.emptyList());

		IFile file = TestUtils.createUniqueTestFile(project, "Formatting Other Text");
		ITextEditor textEditor = (ITextEditor) TestUtils.openEditor(file);
		ITextViewer viewer = LSPEclipseUtils.getTextViewer(textEditor);

		LSPFormatter formatter = new LSPFormatter();
		ISelection selection = viewer.getSelectionProvider().getSelection();

		Optional<VersionedEdits> edits = formatter.requestFormatting(viewer.getDocument(), (ITextSelection) selection, textEditor).get();
		assertTrue(textEditor.isPresent());
		textEditor.getSite().getShell().getDisplay().syncExec(() -> {
			try {
				edits.get().apply();
			} catch (ConcurrentModificationException | BadLocationException e) {
				fail(e.getMessage());
			}
		});
		
		textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
		assertEquals("Formatting Other Text", viewer.getDocument().get());

		TestUtils.closeEditor(textEditor, false);
	}

	@Test
	public void testFormatting()
			throws Exception {
		List<TextEdit> formattingTextEdits = new ArrayList<>();
		formattingTextEdits.add(new TextEdit(new Range(new Position(0, 0), new Position(0, 1)), "MyF"));
		formattingTextEdits.add(new TextEdit(new Range(new Position(0, 10), new Position(0, 11)), ""));
		formattingTextEdits.add(new TextEdit(new Range(new Position(0, 21), new Position(0, 21)), " Second"));
		MockLanguageServer.INSTANCE.setFormattingTextEdits(formattingTextEdits);

		IFile file = TestUtils.createUniqueTestFile(project, "Formatting Other Text");
		ITextEditor textEditor = (ITextEditor) TestUtils.openEditor(file);
		ITextViewer viewer = LSPEclipseUtils.getTextViewer(textEditor);

		LSPFormatter formatter = new LSPFormatter();
		ISelection selection = viewer.getSelectionProvider().getSelection();

		Optional<VersionedEdits> edits = formatter.requestFormatting(viewer.getDocument(), (ITextSelection) selection, textEditor).get();
		assertTrue(edits.isPresent());
		textEditor.getSite().getShell().getDisplay().syncExec(() -> {
			try {
				edits.get().apply();
			} catch (ConcurrentModificationException | BadLocationException e) {
				fail(e.getMessage());
			}
		});

		textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
		assertEquals("MyFormattingOther Text Second", viewer.getDocument().get());

		TestUtils.closeEditor(textEditor, false);
	}

	@Test
	public void testOutdatedFormatting()
			throws CoreException, InterruptedException, ExecutionException, BadLocationException {
		MockLanguageServer.INSTANCE.setFormattingTextEdits(Collections.emptyList());

		IFile file = TestUtils.createUniqueTestFile(project, "Formatting Other Text");
		ITextEditor textEditor = (ITextEditor) TestUtils.openEditor(file);
		ITextViewer viewer = LSPEclipseUtils.getTextViewer(textEditor);

		LSPFormatter formatter = new LSPFormatter();
		ISelection selection = viewer.getSelectionProvider().getSelection();

		Optional<VersionedEdits> edits = formatter.requestFormatting(viewer.getDocument(), (ITextSelection) selection, textEditor).get();
		assertTrue(edits.isPresent());
		viewer.getDocument().replace(0, 0, "Hello");
		waitForAndAssertCondition(1_000,  numberOfChangesIs(1));

		assertThrows(ConcurrentModificationException.class, () -> edits.get().apply());

		TestUtils.closeEditor(textEditor, false);
	}

}
