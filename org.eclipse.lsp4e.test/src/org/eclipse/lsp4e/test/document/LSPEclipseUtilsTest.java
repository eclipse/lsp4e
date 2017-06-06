/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.document;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Collections;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.test.NoErrorLoggedRule;
import org.eclipse.lsp4e.test.TestUtils;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;

public class LSPEclipseUtilsTest {

	@Rule
	public NoErrorLoggedRule rule = new NoErrorLoggedRule(LanguageServerPlugin.getDefault().getLog());

	@Test
	public void testOpenInEditorExternalFile() throws Exception {
		IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		File externalFile = File.createTempFile("externalFile", ".txt");
		try {
			Location location = new Location(LSPEclipseUtils.toUri(externalFile).toString(), new Range(new Position(0, 0), new Position(0, 0)));
			LSPEclipseUtils.openInEditor(location, page);
		} finally {
			page.closeEditor(page.getActiveEditor(), false);
			externalFile.delete();
		}
	}

	@Test
	public void testWorkspaceEdit() throws Exception {
		IProject p = TestUtils.createProject(getClass().getSimpleName() + System.currentTimeMillis());
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

	@Test
	public void testURIToResourceMapping() throws CoreException { // bug 508841
		IProject project1 = null;
		IProject project2 = null;
		try {
			project1 = TestUtils.createProject(getClass().getSimpleName() + System.currentTimeMillis());
			IFile file = project1.getFile("res");
			file.create(new ByteArrayInputStream(new byte[0]), true, new NullProgressMonitor());
			Assert.assertEquals(file, LSPEclipseUtils.findResourceFor(file.getLocationURI().toString()));
			
			project1.getFile("suffix").create(new ByteArrayInputStream(new byte[0]), true, new NullProgressMonitor());
			project2 = TestUtils.createProject(project1.getName() + "suffix");
			Assert.assertEquals(project2, LSPEclipseUtils.findResourceFor(project2.getLocationURI().toString()));
		} finally {
			if (project1 != null) { project1.delete(true, new NullProgressMonitor()); }
			if (project2 != null) { project2.delete(true, new NullProgressMonitor()); }
		}
	}

	@Test
	public void testApplyTextEditLongerThanOrigin() throws Exception {
		IProject project = null;
		IEditorPart editor = null;
		try {
			project = TestUtils.createProject(getClass().getSimpleName() + System.currentTimeMillis());
			IFile file = TestUtils.createUniqueTestFile(project, "line1\nlineInsertHere");
			editor = TestUtils.openEditor(file);
			ITextViewer viewer = TestUtils.getTextViewer(editor);
			TextEdit textEdit = new TextEdit(new Range(new Position(1, 4), new Position(1, 4 + "InsertHere".length())), "Inserted");
			IDocument document = viewer.getDocument();
			LSPEclipseUtils.applyEdit(textEdit, document);
			Assert.assertEquals("line1\nlineInserted", document.get());
		} finally {
			TestUtils.closeEditor(editor, false);
			if (project != null) { project.delete(true, new NullProgressMonitor()); }
		}
	}
	
	@Test
	public void testApplyTextEditShorterThanOrigin() throws Exception {
		IProject project = null;
		IEditorPart editor = null;
		try {
			project = TestUtils.createProject(getClass().getSimpleName() + System.currentTimeMillis());
			IFile file = TestUtils.createUniqueTestFile(project, "line1\nlineHERE");
			editor = TestUtils.openEditor(file);
			ITextViewer viewer = TestUtils.getTextViewer(editor);
			TextEdit textEdit = new TextEdit(new Range(new Position(1, 4), new Position(1, 4 + "HERE".length())), "Inserted");
			IDocument document = viewer.getDocument();
			LSPEclipseUtils.applyEdit(textEdit, document);
			Assert.assertEquals("line1\nlineInserted", document.get());
		} finally {
			TestUtils.closeEditor(editor, false);
			if (project != null) { project.delete(true, new NullProgressMonitor()); }
		}
	}
	
	@Test
	public void testURICreationUnix() {
		Assume.assumeFalse(Platform.OS_WIN32.equals(Platform.getOS()));
		Assert.assertEquals("file:///test%20with%20space", LSPEclipseUtils.toUri(new File("/test with space")).toString());
	}
}
