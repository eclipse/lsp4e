/*******************************************************************************
 * Copyright (c) 2016, 2018 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *  Martin Lippert (Pivotal Inc.) - fixed instability
 *******************************************************************************/
package org.eclipse.lsp4e.test.edit;

import static org.eclipse.lsp4e.test.TestUtils.waitForAndAssertCondition;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.test.AllCleanRule;
import org.eclipse.lsp4e.test.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ide.IDE;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class DocumentDidSaveTest {

	@Rule public AllCleanRule clear = new AllCleanRule();
	private IProject project;

	@Before
	public void setUp() throws CoreException {
		project =  TestUtils.createProject(getClass().getName() + System.currentTimeMillis());
	}

	@Test
	public void testSave() throws Exception {
		IFile testFile = TestUtils.createUniqueTestFile(project, "");
		IEditorPart editor = TestUtils.openEditor(testFile);
		ITextViewer viewer = LSPEclipseUtils.getTextViewer(editor);

		// make sure that timestamp after save will differ from creation time (no better idea at the moment)
		testFile.setLocalTimeStamp(0);

		// Force LS to initialize and open file
		IDocument document = LSPEclipseUtils.getDocument(testFile);
		assertNotNull(document);
		LanguageServiceAccessor.getLanguageServers(document, capabilites -> Boolean.TRUE);
		CompletableFuture<DidSaveTextDocumentParams> didSaveExpectation = new CompletableFuture<DidSaveTextDocumentParams>();
		MockLanguageServer.INSTANCE.setDidSaveCallback(didSaveExpectation);

		// simulate change in file
		viewer.getDocument().replace(0, 0, "Hello");
		editor.doSave(new NullProgressMonitor());

		waitForAndAssertCondition(2_000, () -> {
			DidSaveTextDocumentParams lastChange = didSaveExpectation.get(10, TimeUnit.MILLISECONDS);
			assertNotNull(lastChange.getTextDocument());
			assertEquals(LSPEclipseUtils.toUri(testFile).toString(), lastChange.getTextDocument().getUri());
			return true;
		});
	}

	@Test
	public void testSaveExternalFile() throws Exception {
		File file = TestUtils.createTempFile("testSaveExternalFile", ".lspt");
		IEditorPart editor = IDE.openEditorOnFileStore(UI.getActivePage(), EFS.getStore(file.toURI()));
		ITextViewer viewer = LSPEclipseUtils.getTextViewer(editor);

		// make sure that timestamp after save will differ from creation time (no better idea at the moment)
//			testFile.setLocalTimeStamp(0);

		// Force LS to initialize and open file
		LanguageServiceAccessor.getLanguageServers(LSPEclipseUtils.getDocument(editor.getEditorInput()), capabilites -> Boolean.TRUE);
		CompletableFuture<DidSaveTextDocumentParams> didSaveExpectation = new CompletableFuture<DidSaveTextDocumentParams>();
		MockLanguageServer.INSTANCE.setDidSaveCallback(didSaveExpectation);

		// simulate change in file
		viewer.getDocument().replace(0, 0, "Hello");
		editor.doSave(new NullProgressMonitor());

		waitForAndAssertCondition(2_000, () -> {
			DidSaveTextDocumentParams lastChange = didSaveExpectation.get(10, TimeUnit.MILLISECONDS);
			assertNotNull(lastChange.getTextDocument());
			assertEquals(LSPEclipseUtils.toUri(file).toString(), lastChange.getTextDocument().getUri());
			return true;
		});
	}
}
