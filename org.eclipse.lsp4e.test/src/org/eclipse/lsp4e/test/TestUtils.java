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
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.function.BooleanSupplier;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.ITextViewer;
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
import org.eclipse.ui.tests.harness.util.DisplayHelper;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.ui.texteditor.ITextEditor;

import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;

public class TestUtils {

	private static Set<File> tempFiles = new HashSet<>();

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

	public static void delete(IProject project) throws CoreException {
		if (project != null) {
			project.delete(true, new NullProgressMonitor());
		}
	}

	public static void delete(IProject... projects) throws CoreException {
		if (projects != null && projects.length > 0) {
			for (IProject project : projects) {
				delete(project);
			}
		}
	}

	public static void delete(Path path) throws IOException {
		if (path != null && Files.exists(path)) {
			MoreFiles.deleteRecursively(path, RecursiveDeleteOption.ALLOW_INSECURE);
		}
	}

	public static void delete(Path... paths) throws IOException {
		if (paths != null && paths.length > 0) {
			for (Path path : paths) {
				delete(path);
			}
		}
	}
	
	public static File createTempFile(String prefix, String suffix) throws IOException {
		File tmp = File.createTempFile(prefix, suffix);
		tempFiles.add(tmp);
		return tmp;
	}
	
	public static void addManagedTempFile(File file) {
		tempFiles.add(file);
	}
	
	public static void tearDown() {
		tempFiles.forEach(file -> {
			try {
				Files.deleteIfExists(file.toPath());
			} catch (IOException e) {
				// Trying to have the tests run quieter but I suppose if there's an actual
				// problem we'd better find out about it
				e.printStackTrace();
			}
		});
		tempFiles.clear();
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
			return (Table) control;
		} else if (control instanceof Composite) {
			for (Widget child : ((Composite) control).getChildren()) {
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

	public static boolean waitForCondition(int timeout_ms, BooleanSupplier condition) {
		return waitForCondition(timeout_ms, PlatformUI.getWorkbench().getDisplay(), condition);
	}

	public static boolean waitForCondition(int timeout_ms, Display display, BooleanSupplier condition) {
		return new DisplayHelper() {
			@Override
			protected boolean condition() {
				return condition.getAsBoolean();
			}
		}.waitForCondition(display, timeout_ms);
	}

	public void waitForLanguageServerNotRunning(MockLanguageServer server) {
		assertTrue(new DisplayHelper() {
			@Override
			protected boolean condition() {
				return !server.isRunning();
			}
		}.waitForCondition(PlatformUI.getWorkbench().getDisplay(), 1000));
	}
	
	public static class JobSynchronizer extends NullProgressMonitor {
		private final CountDownLatch latch = new CountDownLatch(1);
		
		@Override
		public void done() {
			latch.countDown();

		}
		@Override
		public void setCanceled(boolean cancelled) {
			super.setCanceled(cancelled);
			if (cancelled) {
				latch.countDown();
			}
		}
		
		public void await() throws InterruptedException, BrokenBarrierException {
			latch.await();
		}
		
		@Override
		public void worked(int work) {
			latch.countDown();
		}
	}
	
	public static BooleanSupplier numberOfChangesIs(int changes) {
		return () -> MockLanguageServer.INSTANCE.getDidChangeEvents().size() == changes;
	}
}
