/*******************************************************************************
 * Copyright (c) 2016, 2017 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.tests.util.DisplayHelper;
import org.eclipse.lsp4e.ContentTypeToLanguageServerDefinition;
import org.eclipse.lsp4e.LanguageServersRegistry;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Widget;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
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
		return createUniqueTestFile(p, "lspt", content);
	}

	public static IFile createUniqueTestFileMultiLS(IProject p, String content) throws CoreException {
		return createUniqueTestFile(p, "lsptmultils", content);
	}

	public static IFile createUniqueTestFileOfUnknownType(IProject p, String content) throws CoreException {
		return createUniqueTestFile(p, "lsptunknown", content);
	}

	public static synchronized IFile createUniqueTestFile(IProject p, String extension, String content)
			throws CoreException {
		long fileNameSalt = System.currentTimeMillis();
		while (p.getFile("test" + fileNameSalt + '.' + extension).exists()) {
			fileNameSalt++;
		}
		return createFile(p, "test" + fileNameSalt + '.' + extension, content);
	}

	public static IFile createFile(IProject p, String name, String content) throws CoreException {
		IFile testFile = p.getFile(name);
		testFile.create(new ByteArrayInputStream(content.getBytes()), true, null);
		return testFile;
	}

	public static ContentTypeToLanguageServerDefinition getDisabledLS() {
		return LanguageServersRegistry.getInstance().getContentTypeToLSPExtensions().stream()
				.filter(definition -> "org.eclipse.lsp4e.test.server.disable".equals(definition.getValue().id)
						&& "org.eclipse.lsp4e.test.content-type-disabled".equals(definition.getKey().toString()))
				.findFirst().get();
	}
	
	public static Shell findNewShell(Set<Shell> beforeShells, Display display) {
		Shell[] afterShells = Arrays.stream(display.getShells())
				.filter(Shell::isVisible)
				.filter(shell -> !beforeShells.contains(shell))
				.toArray(Shell[]::new);
		assertEquals("No new shell found", 1, afterShells.length);
		return afterShells[0];
	}
	
	public static Table findCompletionSelectionControl(Widget control) {
		if (control instanceof Table) {
			return (Table)control;
		} else if (control instanceof Composite) {
			for (Widget child : ((Composite)control).getChildren()) {
				Table res = findCompletionSelectionControl(child);
				if (res != null) {
					return res;
				}
			}
		}
		return null;
	}

	public static IEditorReference[] getEditors() {
		IWorkbenchWindow wWindow = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		if (wWindow != null) {
			IWorkbenchPage wPage = wWindow.getActivePage();
			if (wPage != null) {
				return wPage.getEditorReferences();
			}
		}
		return null;
	}

	public void waitForLanguageServerNotRunning(MockLanguageServer server) {
		assertTrue(new DisplayHelper() {
			@Override
			protected boolean condition() {
				return !server.isRunning();
			}
		}.waitForCondition(PlatformUI.getWorkbench().getDisplay(), 1000));
	}

}
