/*******************************************************************************
 * Copyright (c) 2017 Ericsson and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.lsp4e.test;

import java.io.File;

import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.junit.Test;

/**
 * Tests for the LSPEclipseUtils class.
 */
public class LSPEclipseUtilsTest {

	@Test
	public void testOpenInEditorExternalFile() throws Exception {
		IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		File externalFile = File.createTempFile("externalFile", ".txt");
		Location location = new Location(LSPEclipseUtils.toUri(externalFile).toString(), new Range(new Position(0, 0), new Position(0, 0)));
		LSPEclipseUtils.openInEditor(location, page);
		page.closeEditor(page.getActiveEditor(), false);
	}
}
