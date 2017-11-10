/*******************************************************************************
 * Copyright (c) 2017 Pivotal Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Alex Boyko (Pivotal Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.edit;

import static org.junit.Assert.assertTrue;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.tests.util.DisplayHelper;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.test.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageSever;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DocumentRevertAndCloseTest {

	private IProject project;

	@Before
	public void setUp() throws CoreException {
		MockLanguageSever.reset();
		project =  TestUtils.createProject(getClass().getName() + System.currentTimeMillis());
	}

	@After
	public void tearDown() throws CoreException {
		project.delete(true, true, new NullProgressMonitor());
		MockLanguageSever.INSTANCE.shutdown();
	}
	
	@Test
	public void testShutdownLsp() throws Exception {
		IFile testFile = TestUtils.createUniqueTestFile(project, "Hello!");
		IEditorPart editor = TestUtils.openEditor(testFile);
		ITextViewer viewer = TestUtils.getTextViewer(editor);

		// make sure that timestamp after save will differ from creation time (no better idea at the moment)
		testFile.setLocalTimeStamp(0);

		// Force LS to initialize and open file
		LanguageServiceAccessor.getInitializedLanguageServers(testFile, capabilites -> Boolean.TRUE);
		
		viewer.getDocument().replace(0, 0, "Bye!");
		((AbstractTextEditor)editor).doRevertToSaved();
		((AbstractTextEditor)editor).getSite().getPage().closeEditor(editor, false);
		
		Display display = PlatformUI.getWorkbench().getDisplay();
		assertTrue(new DisplayHelper() {
			@Override
			protected boolean condition() {
				return !MockLanguageSever.INSTANCE.isRunning();
			}
			
		}.waitForCondition(display, 3000));
	}

}
