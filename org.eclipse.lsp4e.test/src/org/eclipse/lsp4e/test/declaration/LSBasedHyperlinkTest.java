/*******************************************************************************
 * Copyright (c) 2020 Pivotal Inc. and others.
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

import org.eclipse.lsp4e.operations.declaration.LSBasedHyperlink;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.junit.Test;

public class LSBasedHyperlinkTest {

	@Test
	public void testHyperlinkLabelNoLocation() {
		Location location = new Location();
		LSBasedHyperlink hyperlink = new LSBasedHyperlink(location, null);
		
		assertEquals("Open Declaration", hyperlink.getHyperlinkText());
	}

	@Test
	public void testHyperlinkLabelForFileLocation() {
		Location location = new Location();
		location.setUri("file:///Users/someuser/testfile");
		LSBasedHyperlink hyperlink = new LSBasedHyperlink(location, null);
		
		assertEquals("Open Declaration - /Users/someuser/testfile", hyperlink.getHyperlinkText());
	}

	@Test
	public void testHyperlinkLabelForFileLocationLink() {
		LocationLink location = new LocationLink();
		location.setTargetUri("file:///Users/someuser/testfile");
		LSBasedHyperlink hyperlink = new LSBasedHyperlink(location, null);
		
		assertEquals("Open Declaration - /Users/someuser/testfile", hyperlink.getHyperlinkText());
	}

	@Test
	public void testHyperlinkLabelForIntroBasedLocation() {
		Location location = new Location();
		location.setUri("http://org.eclipse.ui.intro/execute?command=mycommand%28bindingKey%3DLorg%2Ftest%2Fmvctest%2FMyComponent%3B%2CprojectName%3Dmvctest%29");
		LSBasedHyperlink hyperlink = new LSBasedHyperlink(location, null);
		
		assertEquals("Open Declaration", hyperlink.getHyperlinkText());
	}

	@Test
	public void testHyperlinkLabelForIntroBasedLocationLink() {
		LocationLink location = new LocationLink();
		location.setTargetUri("http://org.eclipse.ui.intro/execute?command=mycommand%28bindingKey%3DLorg%2Ftest%2Fmvctest%2FMyComponent%3B%2CprojectName%3Dmvctest%29");
		LSBasedHyperlink hyperlink = new LSBasedHyperlink(location, null);
		
		assertEquals("Open Declaration", hyperlink.getHyperlinkText());
	}

	@Test
	public void testHyperlinkLabelForRandomURLLocation() {
		Location location = new Location();
		location.setUri("http://eclipse.org");
		LSBasedHyperlink hyperlink = new LSBasedHyperlink(location, null);
		
		assertEquals("Open Declaration - http://eclipse.org", hyperlink.getHyperlinkText());
	}

	@Test
	public void testHyperlinkLabelForRandomURLLocationLink() {
		LocationLink location = new LocationLink();
		location.setTargetUri("http://eclipse.org");
		LSBasedHyperlink hyperlink = new LSBasedHyperlink(location, null);
		
		assertEquals("Open Declaration - http://eclipse.org", hyperlink.getHyperlinkText());
	}

}
