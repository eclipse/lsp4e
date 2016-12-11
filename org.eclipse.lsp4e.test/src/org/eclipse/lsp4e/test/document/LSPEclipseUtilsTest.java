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

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.test.TestUtils;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.junit.Assert;
import org.junit.Test;

public class LSPEclipseUtilsTest {
	
	@Test
	public void testURIToResourceMapping() throws CoreException { // bug 508841
		IProject project1 = null;
		IProject project2 = null;
		try {
			project1 = TestUtils.createProject("testProject" + System.currentTimeMillis());
			IFile file = project1.getFile("res");
			file.create(new ByteArrayInputStream(new byte[0]), true, new NullProgressMonitor());
			Assert.assertEquals(file, LSPEclipseUtils.findResourceFor(file.getLocationURI().toString()));
			
			project1.getFile("suffix").create(new ByteArrayInputStream(new byte[0]), true, new NullProgressMonitor());
			project2 = TestUtils.createProject(project1.getName() + "suffix");
			Assert.assertEquals(project2, LSPEclipseUtils.findResourceFor(project2.getLocationURI().toString()));
		} finally {
			if (project1 != null) project1.delete(true, new NullProgressMonitor());
			if (project2 != null) project2.delete(true, new NullProgressMonitor());
		}
	}

	@Test
	public void testApplyTextEditLongerThanOrigin() throws Exception {
		IProject project = null;
		try {
			project = TestUtils.createProject("testProject" + System.currentTimeMillis());
			IFile file = TestUtils.createUniqueTestFile(project, "line1\nlineInsertHere");
			ITextViewer viewer = TestUtils.openTextViewer(file);
			TextEdit textEdit = new TextEdit(new Range(new Position(1, 4), new Position(1, 4 + "InsertHere".length())), "Inserted");
			IDocument document = viewer.getDocument();
			LSPEclipseUtils.applyEdit(textEdit, document);
			Assert.assertEquals("line1\nlineInserted", document.get());
		} finally {
			if (project != null) project.delete(true, new NullProgressMonitor());
		}
	}
	
	@Test
	public void testApplyTextEditShortedThanOrigin() throws Exception {
		IProject project = null;
		try {
			project = TestUtils.createProject("testProject" + System.currentTimeMillis());
			IFile file = TestUtils.createUniqueTestFile(project, "line1\nlineHERE");
			ITextViewer viewer = TestUtils.openTextViewer(file);
			TextEdit textEdit = new TextEdit(new Range(new Position(1, 4), new Position(1, 4 + "HERE".length())), "Inserted");
			IDocument document = viewer.getDocument();
			LSPEclipseUtils.applyEdit(textEdit, document);
			Assert.assertEquals("line1\nlineInserted", document.get());
		} finally {
			if (project != null) project.delete(true, new NullProgressMonitor());
		}
	}
}
