/*******************************************************************************
 * Copyright (c) 2016 Rogue Wave Software Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
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
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.test.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageSever;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.ui.IEditorPart;
import org.junit.Test;

public class DocumentDidCloseTest {

	@Test
	public void testClose() throws Exception {
		IProject project = TestUtils.createProject("DocumentDidCloseTest" + System.currentTimeMillis());

		IFile testFile = TestUtils.createUniqueTestFile(project, "");
		IEditorPart editor = TestUtils.openEditor(testFile);

		// Force LS to initialize and open file
		LanguageServiceAccessor.getLanguageServer(testFile, null);

		CompletableFuture<DidCloseTextDocumentParams> didCloseExpectation = new CompletableFuture<DidCloseTextDocumentParams>();
		MockLanguageSever.INSTANCE.setDidCloseCallback(didCloseExpectation);

		TestUtils.closeEditor(editor, false);

		DidCloseTextDocumentParams lastChange = didCloseExpectation.get(1000, TimeUnit.MILLISECONDS);
		assertNotNull(lastChange.getTextDocument());
		assertEquals(testFile.getLocationURI().toString(), lastChange.getTextDocument().getUri());

		project.delete(true, true, new NullProgressMonitor());
		
		MockLanguageSever.INSTANCE.shutdown();
	}

}
