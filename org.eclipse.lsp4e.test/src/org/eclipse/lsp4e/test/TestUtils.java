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
package org.eclipse.lsp4e.test;

import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.ui.texteditor.ITextEditor;

public class TestUtils {

	private TestUtils() {
		// this class shouldn't be instantiated
	}

	public static ITextViewer openTextViewer(IFile file) throws InvocationTargetException, PartInitException {
		IEditorPart editor = openEditor(file);
		return getTextViewer(editor);
	}

	public static ITextViewer getTextViewer(IEditorPart part) throws InvocationTargetException {
		try {			
			if (part instanceof ITextEditor) {
				ITextEditor textEditor = (ITextEditor) part;

				Method getSourceViewerMethod = AbstractTextEditor.class.getDeclaredMethod("getSourceViewer"); //$NON-NLS-1$
				getSourceViewerMethod.setAccessible(true);
				return (ITextViewer) getSourceViewerMethod.invoke(textEditor);
			} else {
				fail("Unable to open editor");
				return null;
			}
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			throw new InvocationTargetException(e);
		}
	}
	
	public static IEditorPart openEditor(IFile file) throws PartInitException {
		IWorkbenchWindow workbenchWindow = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		IWorkbenchPage page = workbenchWindow.getActivePage();
		IEditorInput input = new FileEditorInput(file);

		IEditorPart part = page.openEditor(input, "org.eclipse.ui.genericeditor.GenericEditor", false);
		part.setFocus();
		return part;
	}

	public static boolean closeEditor(IEditorPart editor, boolean save) {
		IWorkbenchWindow workbenchWindow = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		IWorkbenchPage page = workbenchWindow.getActivePage();
		return page.closeEditor(editor, save);
	}

	public static IProject createProject(String projectName) throws CoreException {
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
		if (project.exists()) {
			return project;
		}
		project.create(null);
		project.open(null);
		// configure nature
		return project;
	}

	public static IFile createUniqueTestFile(IProject p, String content) throws CoreException {
		return createFile(p, "test" + (System.currentTimeMillis()) + ".lspt", content);
	}

	public static IFile createUniqueTestFileMultiLS(IProject p, String content) throws CoreException {
		return createFile(p, "test" + (System.currentTimeMillis()) + ".lsptmultils", content);
	}

	public static IFile createFile(IProject p, String name, String content) throws CoreException {
		IFile testFile = p.getFile(name);
		testFile.create(new ByteArrayInputStream(content.getBytes()), true, null);
		return testFile;
	}

}
