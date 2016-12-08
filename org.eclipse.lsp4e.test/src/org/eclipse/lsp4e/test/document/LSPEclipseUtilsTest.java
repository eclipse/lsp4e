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
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.test.TestUtils;
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
		} catch (Exception ex) {
			if (project1 != null) project1.delete(true, new NullProgressMonitor());
			if (project2 != null) project2.delete(true, new NullProgressMonitor());
			throw ex;
		}
	}

}
