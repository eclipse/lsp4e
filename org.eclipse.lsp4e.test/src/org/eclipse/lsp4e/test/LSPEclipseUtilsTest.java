/*******************************************************************************
 * Copyright (c) 2017 Ericsson and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.lsp4e.test;

import java.io.File;
import java.util.Collections;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for the LSPEclipseUtils class.
 */
public class LSPEclipseUtilsTest {

	@Rule
	public NoErrorLoggedRule rule = new NoErrorLoggedRule(LanguageServerPlugin.getDefault().getLog());

	@Test
	public void testOpenInEditorExternalFile() throws Exception {
		IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		File externalFile = File.createTempFile("externalFile", ".txt");
		Location location = new Location(LSPEclipseUtils.toUri(externalFile).toString(), new Range(new Position(0, 0), new Position(0, 0)));
		LSPEclipseUtils.openInEditor(location, page);
		page.closeEditor(page.getActiveEditor(), false);
	}
	
	@Test
	public void testWorkspaceEdit() throws Exception {
		IProject p = TestUtils.createProject("project");
		IFile f = TestUtils.createFile(p, "dummy", "Here");
		AbstractTextEditor editor = (AbstractTextEditor)TestUtils.openEditor(f);
		try {
			WorkspaceEdit workspaceEdit = new WorkspaceEdit(Collections.singletonMap(
				LSPEclipseUtils.toUri(f).toString(),
				Collections.singletonList(new TextEdit(new Range(new Position(0, 0), new Position(0, 0)), "insert"))));
			LSPEclipseUtils.applyWorkspaceEdit(workspaceEdit);
			Assert.assertEquals("insertHere", ((StyledText)editor.getAdapter(Control.class)).getText());
			Assert.assertEquals("insertHere", editor.getDocumentProvider().getDocument(editor.getEditorInput()).get());
		} finally {
			editor.close(false);
			p.delete(true, new NullProgressMonitor());
		}
	}
}
