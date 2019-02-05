/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - Added some suites
 *******************************************************************************/
package org.eclipse.lsp4e.test.rename;

import static org.junit.Assert.assertTrue;

import org.eclipse.core.commands.Command;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.lsp4e.test.AllCleanRule;
import org.eclipse.lsp4e.test.TestUtils;
import org.eclipse.ui.IWorkbenchCommandConstants;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.texteditor.ITextEditor;
import org.junit.Rule;
import org.junit.Test;

public class RenameTest {

	@Rule public AllCleanRule clear = new AllCleanRule();

	@Test
	public void testRenameHandled() throws Exception {
		IProject project = TestUtils.createProject("blah");
		try {
			IFile file = TestUtils.createUniqueTestFile(project, "old");
			ITextEditor editor = (ITextEditor) TestUtils.openEditor(file);
			editor.selectAndReveal(1, 0);
			ICommandService commandService = PlatformUI.getWorkbench().getService(ICommandService.class);
			Command command = commandService.getCommand(IWorkbenchCommandConstants.FILE_RENAME);
			assertTrue(command.isEnabled() && command.isHandled());
		} finally {
			project.delete(true, null);
		}
	}
}
