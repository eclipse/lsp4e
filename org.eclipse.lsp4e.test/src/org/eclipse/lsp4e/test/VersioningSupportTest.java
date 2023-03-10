/*******************************************************************************
 * Copyright (c) 2022-3 Cocotec Ltd and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Ahmed Hussain (Cocotec Ltd) - initial implementation
 *
 *******************************************************************************/
package org.eclipse.lsp4e.test;

import static org.eclipse.lsp4e.test.TestUtils.numberOfChangesIs;
import static org.eclipse.lsp4e.test.TestUtils.waitForAndAssertCondition;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.VersionedBuilder;
import org.eclipse.lsp4e.VersionedEdits;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.ui.IEditorPart;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class VersioningSupportTest {
	@Rule
	public AllCleanRule clear = new AllCleanRule();

	private IProject project;


	@Before
	public void setUp() throws CoreException {
		project = TestUtils.createProject("VersioningSupportTest"+System.currentTimeMillis());
	}


	@Test
	public void testVersionSupportSuccess() throws Exception {
		List<TextEdit> formattingTextEdits = new ArrayList<>();
		formattingTextEdits.add(new TextEdit(new Range(new Position(0, 0), new Position(0, 1)), "MyF"));
		formattingTextEdits.add(new TextEdit(new Range(new Position(0, 10), new Position(0, 11)), ""));
		formattingTextEdits.add(new TextEdit(new Range(new Position(0, 21), new Position(0, 21)), " Second"));
		MockLanguageServer.INSTANCE.setFormattingTextEdits(formattingTextEdits);

		IFile file = TestUtils.createUniqueTestFile(project, "Formatting Other Text");
		IEditorPart editor = TestUtils.openEditor(file);
		ITextViewer viewer = LSPEclipseUtils.getTextViewer(editor);

		final var doc = viewer.getDocument();
		final var docId = LSPEclipseUtils.toTextDocumentIdentifier(doc);

		final var params = new DocumentFormattingParams();
		params.setTextDocument(docId);
		params.setOptions(new FormattingOptions(4, true));

		var ex = LanguageServers.forDocument(doc).withCapability(ServerCapabilities::getDocumentFormattingProvider);

		var builder = new VersionedBuilder<List<? extends TextEdit>, @NonNull VersionedEdits>(doc, VersionedEdits::new);

		var result = ex.computeFirst(ls -> ls.getTextDocumentService().formatting(params).thenApply(builder::build));

		VersionedEdits edits = result.join().get();
		editor.getSite().getShell().getDisplay().syncExec(() -> {
			try {
				edits.apply();
			} catch (ConcurrentModificationException | BadLocationException e) {
				fail(e.getMessage());
			}
		});

		TestUtils.closeEditor(editor, false);
	}

	@Test(expected=ConcurrentModificationException.class)
	public void testVersionedEditsFailsOnModification() throws Exception {
		List<TextEdit> formattingTextEdits = new ArrayList<>();
		formattingTextEdits.add(new TextEdit(new Range(new Position(0, 0), new Position(0, 1)), "MyF"));
		formattingTextEdits.add(new TextEdit(new Range(new Position(0, 10), new Position(0, 11)), ""));
		formattingTextEdits.add(new TextEdit(new Range(new Position(0, 21), new Position(0, 21)), " Second"));
		MockLanguageServer.INSTANCE.setFormattingTextEdits(formattingTextEdits);

		IFile file = TestUtils.createUniqueTestFile(project, "Formatting Other Text");
		IEditorPart editor = TestUtils.openEditor(file);
		ITextViewer viewer = LSPEclipseUtils.getTextViewer(editor);

		final var doc = viewer.getDocument();
		final var docId = LSPEclipseUtils.toTextDocumentIdentifier(doc);

		final var params = new DocumentFormattingParams();
		params.setTextDocument(docId);
		params.setOptions(new FormattingOptions(4, true));

		var ex = LanguageServers.forDocument(doc).withCapability(ServerCapabilities::getDocumentFormattingProvider);
		var builder = new VersionedBuilder<List<? extends TextEdit>, @NonNull VersionedEdits>(doc, VersionedEdits::new);

		var result = ex.computeFirst(ls -> ls.getTextDocumentService().formatting(params).thenApply(builder::build));

		VersionedEdits edits = result.join().get();
		viewer.getDocument().replace(0, 0, "Hello");
		waitForAndAssertCondition(1_000,  numberOfChangesIs(1));

		edits.apply();
	}

}
