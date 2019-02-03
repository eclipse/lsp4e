/*******************************************************************************
 * Copyright (c) 2016 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.definition;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.lsp4e.operations.declaration.OpenDeclarationHyperlinkDetector;
import org.eclipse.lsp4e.test.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageSever;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.ITextEditor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DefinitionTest {

	private IProject project;
	private OpenDeclarationHyperlinkDetector hyperlinkDetector;

	@Before
	public void setUp() throws CoreException {
		project = TestUtils.createProject("DefinitionTest" + System.currentTimeMillis());
		hyperlinkDetector = new OpenDeclarationHyperlinkDetector();
	}

	@After
	public void tearDown() throws CoreException {
		PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().closeAllEditors(false);
		project.delete(true, true, new NullProgressMonitor());
		MockLanguageSever.INSTANCE.shutdown();
	}

	@Test
	public void testDefinitionOneLocation() throws Exception {
		Location location = new Location("file://test", new Range(new Position(0, 0), new Position(0, 10)));
		MockLanguageSever.INSTANCE.setDefinition(Collections.singletonList(location));

		IFile file = TestUtils.createUniqueTestFile(project, "Example Text");
		ITextViewer viewer = TestUtils.openTextViewer(file);

		IHyperlink[] hyperlinks = hyperlinkDetector.detectHyperlinks(viewer, new Region(1, 0), true);
		assertEquals(1, hyperlinks.length);		
		// TODO add location check
	}

	@Test
	public void testDefinitionOneLocationExternalFile() throws Exception {
		Location location = new Location("file://test", new Range(new Position(0, 0), new Position(0, 10)));
		MockLanguageSever.INSTANCE.setDefinition(Collections.singletonList(location));

		File file = File.createTempFile("testDocumentLinkExternalFile", ".lspt");
		try {
			ITextEditor editor = (ITextEditor) IDE.openInternalEditorOnFileStore(
					PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(), EFS.getStore(file.toURI()));
			ITextViewer viewer = TestUtils.getTextViewer(editor);

			IHyperlink[] hyperlinks = hyperlinkDetector.detectHyperlinks(viewer, new Region(0, 0), true);
			assertEquals(1, hyperlinks.length);
		} finally {
			Files.deleteIfExists(file.toPath());
		}
	}
	
	@Test
	public void testDefinitionManyLocation() throws Exception {
		List<Location> locations = new ArrayList<>();
		locations.add(new Location("file://test0", new Range(new Position(0, 0), new Position(0, 10))));
		locations.add(new Location("file://test1", new Range(new Position(1, 0), new Position(1, 10))));
		locations.add(new Location("file://test2", new Range(new Position(2, 0), new Position(2, 10))));
		MockLanguageSever.INSTANCE.setDefinition(locations);

		IFile file = TestUtils.createUniqueTestFile(project, "Example Text");
		ITextViewer viewer = TestUtils.openTextViewer(file);

		IHyperlink[] hyperlinks = hyperlinkDetector.detectHyperlinks(viewer, new Region(1, 0), true);
		assertEquals(3, hyperlinks.length);
		// TODO add location check
	}

	@Test
	public void testDefinitionNoLocations() throws Exception {
		MockLanguageSever.INSTANCE.setDefinition(null);

		IFile file = TestUtils.createUniqueTestFile(project, "Example Text");
		ITextViewer viewer = TestUtils.openTextViewer(file);

		IHyperlink[] hyperlinks = hyperlinkDetector.detectHyperlinks(viewer, new Region(1, 0), true);
		assertEquals(true, hyperlinks == null);
	}
	
	@Test
	public void testDefinitionEmptyLocations() throws Exception {
		MockLanguageSever.INSTANCE.setDefinition(Collections.emptyList());

		IFile file = TestUtils.createUniqueTestFile(project, "Example Text");
		ITextViewer viewer = TestUtils.openTextViewer(file);

		IHyperlink[] hyperlinks = hyperlinkDetector.detectHyperlinks(viewer, new Region(1, 0), true);
		assertEquals(true, hyperlinks == null);
	}
}
