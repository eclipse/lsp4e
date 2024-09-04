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

import static org.eclipse.lsp4e.test.utils.TestUtils.*;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.eclipse.core.resources.IFile;
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
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.texteditor.ITextEditor;
import org.junit.Test;

public class FormatTest extends AbstractTestWithProject {

	@Test
	public void testFormattingInvalidDocument() throws Exception {
		final var formatter = new LSPFormatter();
		ITextSelection selection = TextSelection.emptySelection();

		Optional<VersionedEdits> edits = formatter.requestFormatting(new Document(), selection).get();
		assertTrue(edits.isEmpty());
	}

	@Test
	public void testFormattingNoChanges() throws Exception {
		MockLanguageServer.INSTANCE.setFormattingTextEdits(Collections.emptyList());

		IFile file = TestUtils.createUniqueTestFile(project, "Formatting Other Text");
		IEditorPart editor = TestUtils.openEditor(file);
		ITextViewer viewer = LSPEclipseUtils.getTextViewer(editor);

		final var formatter = new LSPFormatter();
		ISelection selection = viewer.getSelectionProvider().getSelection();

		Optional<VersionedEdits> edits = formatter.requestFormatting(viewer.getDocument(), (ITextSelection) selection).get();
		assertTrue(edits.isPresent());
		editor.getSite().getShell().getDisplay().syncExec(() -> {
			try {
				edits.get().apply();
			} catch (ConcurrentModificationException | BadLocationException e) {
				fail(e.getMessage());
			}
		});
		final var textEditor = (ITextEditor) editor;
		textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
		assertEquals("Formatting Other Text", viewer.getDocument().get());

		TestUtils.closeEditor(editor, false);
	}

	@Test
	public void testFormatting()
			throws Exception {
		final var formattingTextEdits = new ArrayList<TextEdit>();
		formattingTextEdits.add(new TextEdit(new Range(new Position(0, 0), new Position(0, 1)), "MyF"));
		formattingTextEdits.add(new TextEdit(new Range(new Position(0, 10), new Position(0, 11)), ""));
		formattingTextEdits.add(new TextEdit(new Range(new Position(0, 21), new Position(0, 21)), " Second"));
		MockLanguageServer.INSTANCE.setFormattingTextEdits(formattingTextEdits);

		IFile file = TestUtils.createUniqueTestFile(project, "Formatting Other Text");
		IEditorPart editor = TestUtils.openEditor(file);
		ITextViewer viewer = LSPEclipseUtils.getTextViewer(editor);

		final var formatter = new LSPFormatter();
		ISelection selection = viewer.getSelectionProvider().getSelection();

		Optional<VersionedEdits> edits = formatter.requestFormatting(viewer.getDocument(), (ITextSelection) selection).get();
		assertTrue(edits.isPresent());
		editor.getSite().getShell().getDisplay().syncExec(() -> {
			try {
				edits.get().apply();
			} catch (ConcurrentModificationException | BadLocationException e) {
				fail(e.getMessage());
			}
		});

		final var textEditor = (ITextEditor) editor;
		textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
		assertEquals("MyFormattingOther Text Second", viewer.getDocument().get());

		TestUtils.closeEditor(editor, false);
	}

	@Test
	public void testOutdatedFormatting()
			throws CoreException, InterruptedException, ExecutionException, BadLocationException {
		MockLanguageServer.INSTANCE.setFormattingTextEdits(Collections.emptyList());

		IFile file = TestUtils.createUniqueTestFile(project, "Formatting Other Text");
		IEditorPart editor = TestUtils.openEditor(file);
		ITextViewer viewer = LSPEclipseUtils.getTextViewer(editor);

		final var formatter = new LSPFormatter();
		ISelection selection = viewer.getSelectionProvider().getSelection();

		Optional<VersionedEdits> edits = formatter.requestFormatting(viewer.getDocument(), (ITextSelection) selection).get();
		assertTrue(edits.isPresent());
		viewer.getDocument().replace(0, 0, "Hello");
		waitForAndAssertCondition(1_000,  numberOfChangesIs(1));

		assertThrows(ConcurrentModificationException.class, () -> edits.get().apply());

		TestUtils.closeEditor(editor, false);
	}
}
