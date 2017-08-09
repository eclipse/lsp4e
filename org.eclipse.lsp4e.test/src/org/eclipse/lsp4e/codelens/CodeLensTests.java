/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Lucas Bullen (Red Hat Inc.) - Bug 508458 - Add support for codelens
 *******************************************************************************/
package org.eclipse.lsp4e.codelens;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.lsp4e.operations.hover.LSBasedHover;
import org.eclipse.lsp4e.test.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageSever;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.ui.PlatformUI;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CodeLensTests {
	private IProject project;
	private LSBasedHover hover;

	@Before
	public void setUp() throws CoreException {
		project = TestUtils.createProject("CodeLensTest" + System.currentTimeMillis());
		hover = new LSBasedHover();
	}

	@After
	public void tearDown() throws CoreException {
		PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().closeAllEditors(false);
		project.delete(true, true, new NullProgressMonitor());
		MockLanguageSever.INSTANCE.shutdown();
	}

	@Test
	public void testCodeLensInfo() throws CoreException, InvocationTargetException {
		List<CodeLens> codeLenses = new ArrayList<>();
		codeLenses.add(new CodeLens(new Range(new Position(0, 0), new Position(0, 12)), new Command("Result", null), null));
		MockLanguageSever.INSTANCE.setCodeLens(codeLenses);

		IFile file = TestUtils.createUniqueTestFile(project, "CodeLensRange Other Text");
		ITextViewer viewer = TestUtils.openTextViewer(file);

		assertTrue(hover.getHoverInfo(viewer, new Region(0, 12)).contains("Result"));
	}

	@Test
	public void testCodeLensInfoEmptyList() throws CoreException, InvocationTargetException {
		List<CodeLens> codeLenses = new ArrayList<>();
		MockLanguageSever.INSTANCE.setCodeLens(codeLenses);

		IFile file = TestUtils.createUniqueTestFile(project, "CodeLensRange Other Text");
		ITextViewer viewer = TestUtils.openTextViewer(file);

		assertEquals(null, hover.getHoverInfo(viewer, new Region(0, 12)));
	}

	@Test
	public void testCodeLensInfoInvalidOffset() throws CoreException, InvocationTargetException {
		MockLanguageSever.INSTANCE.setCodeLens(null);

		IFile file = TestUtils.createUniqueTestFile(project, "CodeLensRange Other Text");
		ITextViewer viewer = TestUtils.openTextViewer(file);

		assertEquals(null, hover.getHoverInfo(viewer, new Region(0, 12)));
	}

	@Test
	public void testCodeLensEmptyContentItem() throws CoreException, InvocationTargetException {
		List<CodeLens> codeLenses = new ArrayList<>();
		codeLenses.add(new CodeLens(new Range(new Position(0, 0), new Position(0, 12)), null, null));
		MockLanguageSever.INSTANCE.setCodeLens(codeLenses);

		IFile file = TestUtils.createUniqueTestFile(project, "CodeLensRange Other Text");
		ITextViewer viewer = TestUtils.openTextViewer(file);

		assertEquals(null, hover.getHoverInfo(viewer, new Region(0, 12)));
	}

	@Test
	public void testMultipleCodeLens() throws Exception {
		List<CodeLens> codeLenses = new ArrayList<>();
		codeLenses.add(new CodeLens(new Range(new Position(0, 0), new Position(0, 12)), new Command("Result1", null), null));
		codeLenses.add(new CodeLens(new Range(new Position(0, 0), new Position(0, 12)), new Command("Result2", null), null));
		MockLanguageSever.INSTANCE.setCodeLens(codeLenses);

		IFile file = TestUtils.createUniqueTestFileMultiLS(project, "CodeLensRange Other Text");
		ITextViewer viewer = TestUtils.openTextViewer(file);

		assertTrue(hover.getHoverInfo(viewer, new Region(0, 12)).contains("Result1"));
		assertTrue(hover.getHoverInfo(viewer, new Region(0, 12)).contains("Result2"));
	}
}
