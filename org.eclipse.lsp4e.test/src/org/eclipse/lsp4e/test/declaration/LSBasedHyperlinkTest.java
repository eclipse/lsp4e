/*******************************************************************************
 * Copyright (c) 2020, 2023 Pivotal Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Martin Lippert (Pivotal Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.declaration;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.operations.declaration.LSBasedHyperlink;
import org.eclipse.lsp4e.test.utils.AllCleanRule;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class LSBasedHyperlinkTest {

	private static String locationType = "Open Declaration";
	
	@Rule public AllCleanRule clear = new AllCleanRule();
	private IProject project;

	@Before
	public void setUp() throws CoreException {
		project = TestUtils.createProject("HyperlinkLabelTest");
	}

	@Test
	public void testHyperlinkLabelNoLocation() {
		Location location = new Location();
		LSBasedHyperlink hyperlink = new LSBasedHyperlink(location, null, locationType);

		assertEquals(locationType, hyperlink.getHyperlinkText());
	}

	@Test
	public void testHyperlinkLabelForFileLocation() throws URISyntaxException {
		Location location = new Location();
		location.setUri("file:///Users/someuser/testfile");
		LSBasedHyperlink hyperlink = new LSBasedHyperlink(location, null, locationType);

		assertEquals("Open Declaration - testfile - " + Path.of(new URI(location.getUri())),
				hyperlink.getHyperlinkText());
	}

	@Test
	public void testHyperlinkLabelForFileLocationLink() throws URISyntaxException {
		LocationLink location = new LocationLink();
		location.setTargetUri("file:///Users/someuser/testfile");
		LSBasedHyperlink hyperlink = new LSBasedHyperlink(location, null, locationType);

		assertEquals("Open Declaration - testfile - " + Path.of(new URI(location.getTargetUri())), hyperlink.getHyperlinkText());
	}

	@Test
	public void testHyperlinkLabelForIntroBasedLocationWithoutLabel() {
		Location location = new Location();
		location.setUri("http://org.eclipse.ui.intro/execute?command=mycommand%28bindingKey%3DLorg%2Ftest%2Fmvctest%2FMyComponent%3B%2CprojectName%3Dmvctest%29");
		LSBasedHyperlink hyperlink = new LSBasedHyperlink(location, null, locationType);

		assertEquals("Open Declaration", hyperlink.getHyperlinkText());
	}

	@Test
	public void testHyperlinkLabelForIntroBasedLocationLinkWithLabel() {
		LocationLink location = new LocationLink();
		location.setTargetUri("http://org.eclipse.ui.intro/execute?command=org.springframework.tooling.ls.eclipse.commons.commands.OpenJavaElementInEditor%28bindingKey%3DLorg%2Ftest%2Fmvctest%2FMyComponent%3B%2CprojectName%3Dmvctest%29&label=MyComponent+-+org.test.mvctest");
		LSBasedHyperlink hyperlink = new LSBasedHyperlink(location, null, locationType);

		assertEquals("Open Declaration - MyComponent - org.test.mvctest", hyperlink.getHyperlinkText());
	}

	@Test
	public void testHyperlinkLabelForRandomURLLocation() {
		Location location = new Location();
		location.setUri("http://eclipse.org");
		LSBasedHyperlink hyperlink = new LSBasedHyperlink(location, null, locationType);

		assertEquals("Open Declaration - http://eclipse.org", hyperlink.getHyperlinkText());
	}

	@Test
	public void testHyperlinkLabelForRandomURLLocationLink() {
		LocationLink location = new LocationLink();
		location.setTargetUri("http://eclipse.org");
		LSBasedHyperlink hyperlink = new LSBasedHyperlink(location, null, locationType);

		assertEquals("Open Declaration - http://eclipse.org", hyperlink.getHyperlinkText());
	}

	@Test
	public void testHyperlinkLabelForFileInProject() throws Exception {
		IFile file = TestUtils.createFile(project, "my-test.txt", "Example Text");
		LocationLink location = new LocationLink();
		location.setTargetUri(LSPEclipseUtils.toUri(new File(file.getLocation().toOSString())).toASCIIString());
		LSBasedHyperlink hyperlink = new LSBasedHyperlink(location, null, locationType);

		assertEquals("Open Declaration - my-test.txt - HyperlinkLabelTest", hyperlink.getHyperlinkText());
	}
}
