/*******************************************************************************
 * Copyright (c) 2017 Pivotal Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Martin Lippert (Pivotal Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test;

import static org.junit.Assert.assertTrue;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.tests.util.DisplayHelper;
import org.eclipse.lsp4e.ContentTypeToLanguageServerDefinition;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.tests.mock.MockLanguageSever;
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
		
		// open and close the editor several times
		for(int i = 0; i < 10; i++) {
			IEditorPart editor = TestUtils.openEditor(testFile);
			LanguageServiceAccessor.getInitializedLanguageServers(testFile, capabilities -> Boolean.TRUE).iterator()
					.next();
			assertTrue("language server is started for iteration #" + i, new StartedDisplayHelper().waitForCondition(Display.getCurrent(), 5000, 300));

			((AbstractTextEditor)editor).close(false);
			assertTrue("language server is closed for iteration #" + i, new StoppedDisplayHelper().waitForCondition(Display.getCurrent(), 5000, 300));
		}
	}
	
	@Test
	public void testDisabledLanguageServer() throws Exception {
		IFile testFile = TestUtils.createUniqueTestFile(project, "lspt-disabled", "");

		TestUtils.openEditor(testFile);
		assertTrue("language server should not be started because it is disabled",
				new StoppedDisplayHelper().waitForCondition(Display.getCurrent(), 5000, 300));

		ContentTypeToLanguageServerDefinition lsDefinition = TestUtils.getDisabledLS();
		lsDefinition.setEnabled(true);
		LanguageServiceAccessor.enableLanguageServerContentType(lsDefinition, TestUtils.getEditors());

		assertTrue("language server should be started",
				new StartedDisplayHelper().waitForCondition(Display.getCurrent(), 5000, 300));
	}

	protected static class StartedDisplayHelper extends DisplayHelper {
		@Override
		protected boolean condition() {
			return MockLanguageSever.INSTANCE.isRunning();
		}
	};

	protected static class StoppedDisplayHelper extends DisplayHelper {
		@Override
		protected boolean condition() {
			return !MockLanguageSever.INSTANCE.isRunning();
		}
	};

	
}
