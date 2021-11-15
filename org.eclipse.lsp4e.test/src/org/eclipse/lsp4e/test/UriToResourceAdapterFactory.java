/*******************************************************************************
 * Copyright (c) 2021 Avaloq.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Rubén Porras Campo (Avaloq) - Bug 576425 - Support Remote Files
 *******************************************************************************/
package org.eclipse.lsp4e.test;

import java.net.URI;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IAdapterFactory;
import org.eclipse.lsp4e.test.edit.LSPEclipseUtilsTest;

/**
 * A custom URI to Resource Mapper to test mapping of remote URIs by stripping the segment "/a/",
 * it is tightly coupled with {@link LSPEclipseUtilsTest#testCustomURIToResourceMapping()}
 * and {@link LSPEclipseUtilsTest#testCustomResourceToURIMapping()}
 */
public class UriToResourceAdapterFactory implements IAdapterFactory{


	private static final String A_SEGMENT = "/a/";

	@SuppressWarnings("unchecked")
	  @Override
	  public <T> T getAdapter(final Object adaptableObject, final Class<T> adapterType) {
	    if (adaptableObject instanceof String) {
	        URI uri = URI.create(((String) adaptableObject).replace(A_SEGMENT, ""));
	        String path = uri.getPath();
	        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(LSPEclipseUtilsTest.class.getSimpleName() + uri.getScheme());
	        if (path != null) {
	          if (adapterType == IResource.class) {
	            return (T) project.findMember(path);
	          } else if (adapterType == IFile.class) {
	            return (T) project.getFile(path);
	          }
	        }
	    } else if (adaptableObject instanceof IFile) {
	    	URI uri = ((IResource)adaptableObject).getLocationURI();
	        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(LSPEclipseUtilsTest.class.getSimpleName() + uri.getScheme());
	        if (project != null && uri.getScheme().equals("other")) {
	        	return (T) URI.create(uri.toString().replaceAll("//", "/" + A_SEGMENT));
	        }
	    }
	    return null;
	  }

	  @Override
	  public Class<?>[] getAdapterList() {
	    return new Class<?>[] {IResource.class, IFile.class, URI.class};
	  }
}
