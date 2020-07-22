/*******************************************************************************
 * Copyright (c) 2019 SAP SE and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Markus Ofterdinger (SAP SE) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collection;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.ui.IEditorPart;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class LanguageServerWrapperTest {

	private IProject project1;
	private IProject project2;

	@Rule
	public AllCleanRule clear = new AllCleanRule();

	@Before
	public void setUp() throws CoreException {
		project1 = TestUtils.createProject("LanguageServerWrapperTestProject1" + System.currentTimeMillis());
		project2 = TestUtils.createProject("LanguageServerWrapperTestProject2" + System.currentTimeMillis());
	}

	@Test
	public void testConnect() throws Exception {
		IFile testFile1 = TestUtils.createFile(project1, "shouldUseExtension.lsptWithMultiRoot", "");
		IFile testFile2 = TestUtils.createFile(project2, "shouldUseExtension.lsptWithMultiRoot", "");

		IEditorPart editor1 = TestUtils.openEditor(testFile1);
		IEditorPart editor2 = TestUtils.openEditor(testFile2);

		@NonNull Collection<LanguageServerWrapper> wrappers = LanguageServiceAccessor.getLSWrappers(testFile1, request -> true);

		assertEquals(1, wrappers.size());

		LanguageServerWrapper wrapper = wrappers.iterator().next();
		for(int i = 0; i < 10 && !wrapper.isActive(); i++) {
			Thread.sleep(100);
		}
		
		assertTrue(wrapper.isConnectedTo(testFile1.getLocation()));
		assertTrue(wrapper.isConnectedTo(testFile2.getLocation()));

		TestUtils.closeEditor(editor1, false);
		TestUtils.closeEditor(editor2, false);
	}
}
