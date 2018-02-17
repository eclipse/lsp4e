/*******************************************************************************
 * Copyright (c) 2017, 2018 Pivotal Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Martin Lippert (Pivotal Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.lsp4e.ContentTypeToLanguageServerDefinition;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.tests.mock.MockLanguageSever;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


public class RunningLanguageServerTest {

	private IProject project;

	@Before
	public void setUp() throws CoreException {
		MockLanguageSever.reset();
		project =  TestUtils.createProject("StartStopServerTest"+System.currentTimeMillis());
	}

	@After
	public void tearDown() throws CoreException {
		project.delete(true, true, new NullProgressMonitor());
		MockLanguageSever.INSTANCE.shutdown();
	}
	
	/**
	 * checks if language servers get started and shutdown correctly if opening and
	 * closing the same file/editor multiple times
	 */
	@Test
	public void testOpenCloseLanguageServer() throws Exception {
		IFile testFile = TestUtils.createUniqueTestFile(project, "");
		Display display = Display.getCurrent();
		
		// open and close the editor several times
		for(int i = 0; i < 10; i++) {
			IEditorPart editor = TestUtils.openEditor(testFile);
			LanguageServiceAccessor.getInitializedLanguageServers(testFile, capabilities -> Boolean.TRUE).iterator()
					.next();
			assertTrue("language server is started for iteration #" + i,
					new LSDisplayHelper(() -> MockLanguageSever.INSTANCE.isRunning()).waitForCondition(display, 5000));

			((AbstractTextEditor)editor).close(false);
			assertTrue("language server is closed for iteration #" + i,
					new LSDisplayHelper(() -> !MockLanguageSever.INSTANCE.isRunning()).waitForCondition(display, 5000));
		}
	}
	
	@Test
	public void testDisabledLanguageServer() throws Exception {
		IFile testFile = TestUtils.createUniqueTestFile(project, "lspt-disabled", "");
		Display display = Display.getCurrent();

		ContentTypeToLanguageServerDefinition lsDefinition = TestUtils.getDisabledLS();
		lsDefinition.setEnabled(false);
		LanguageServiceAccessor.disableLanguageServerContentType(lsDefinition);

		TestUtils.openEditor(testFile);
		List<CompletableFuture<LanguageServer>> initializedLanguageServers = LanguageServiceAccessor
				.getInitializedLanguageServers(testFile, capabilities -> Boolean.TRUE);
		assertNotNull(initializedLanguageServers);
		assertEquals("language server should not be started because it is disabled", 0,
				initializedLanguageServers.size());

		lsDefinition.setEnabled(true);
		LanguageServiceAccessor.enableLanguageServerContentType(lsDefinition, TestUtils.getEditors());

		assertTrue("language server should be started",
				new LSDisplayHelper(() -> MockLanguageSever.INSTANCE.isRunning()).waitForCondition(display, 5000));
	}

}
