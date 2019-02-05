/*******************************************************************************
 * Copyright (c) 2016 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.edit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.test.AllCleanRule;
import org.eclipse.lsp4e.test.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.ui.IEditorPart;
import org.junit.Rule;
import org.junit.Test;

public class DocumentDidCloseTest {

	@Rule public AllCleanRule clear = new AllCleanRule();

	@Test
	public void testClose() throws Exception {
		IProject project = TestUtils.createProject("DocumentDidCloseTest" + System.currentTimeMillis());

		IFile testFile = TestUtils.createUniqueTestFile(project, "");
		IEditorPart editor = TestUtils.openEditor(testFile);

		// Force LS to initialize and open file
		LanguageServiceAccessor.getLanguageServers(LSPEclipseUtils.getDocument(testFile), capabilites -> Boolean.TRUE);

		CompletableFuture<DidCloseTextDocumentParams> didCloseExpectation = new CompletableFuture<DidCloseTextDocumentParams>();
		MockLanguageServer.INSTANCE.setDidCloseCallback(didCloseExpectation);

		TestUtils.closeEditor(editor, false);

		DidCloseTextDocumentParams lastChange = didCloseExpectation.get(1000, TimeUnit.MILLISECONDS);
		assertNotNull(lastChange.getTextDocument());
		assertEquals(LSPEclipseUtils.toUri(testFile).toString(), lastChange.getTextDocument().getUri());

		project.delete(true, true, new NullProgressMonitor());
		
		MockLanguageServer.reset();
	}

}
