/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others.
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

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.file.Files;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.refactoring.LSPTextChange;
import org.eclipse.lsp4e.test.AllCleanRule;
import org.eclipse.lsp4e.test.TestUtils;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.ltk.core.refactoring.PerformChangeOperation;
import org.junit.Rule;
import org.junit.Test;

public class LSPTextChangeTest {

	@Rule public AllCleanRule clear = new AllCleanRule();

	@Test
	public void testPerformOperationWorkspaceFile() throws Exception {
		IProject project = TestUtils.createProject("blah");
		IFile file = TestUtils.createUniqueTestFile(project, "old");
		TextEdit edit = new TextEdit(new Range(new Position(0, 0), new Position(0, 3)), "new");
		PerformChangeOperation operation = new PerformChangeOperation(new LSPTextChange("test", LSPEclipseUtils.toUri(file), edit));
		operation.run(new NullProgressMonitor());
		assertEquals(edit.getNewText(), LSPEclipseUtils.getDocument(file).get());
	}

	@Test
	public void testPerformOperationExternalFile() throws Exception {
		File file = File.createTempFile("testPerformOperationExternalFile", ".lspt");
		try {
			Files.write(file.toPath(), "old".getBytes());
			TextEdit edit = new TextEdit(new Range(new Position(0, 0), new Position(0, 3)), "new");
			PerformChangeOperation operation = new PerformChangeOperation(new LSPTextChange("test", LSPEclipseUtils.toUri(file), edit));
			operation.run(new NullProgressMonitor());
			assertEquals(edit.getNewText(), new String(Files.readAllBytes(file.toPath())));
		} finally {
			Files.deleteIfExists(file.toPath());
		}
	}

}
