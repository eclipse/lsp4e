/*******************************************************************************
 * Copyright (c) 2021 Avaloq.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Rub�n Porras Campo (Avaloq) - Bug 576425 - Support Remote Files
 *******************************************************************************/
package org.eclipse.lsp4e.test.utils;

import java.io.InputStream;
import java.net.URI;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileInfo;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.filesystem.provider.FileInfo;
import org.eclipse.core.filesystem.provider.FileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;

/**
 * A file store to be used together with the {@link OtherFileSystem}
 *
 */
public class OtherFileStore  extends FileStore {

	private final URI location;

	public OtherFileStore(URI location) {
		this.location = location;
	}

	@Override
	public String[] childNames(int options, IProgressMonitor monitor) throws CoreException {
		return FileStore.EMPTY_STRING_ARRAY;
	}

	@Override
	public IFileInfo fetchInfo(int options, IProgressMonitor monitor) throws CoreException {
		final var result = new FileInfo();
		result.setDirectory(false);
		result.setExists(true);
		result.setLastModified(1);//last modified of zero indicates non-existence
		return result;
	}

	@Override
	public IFileStore getChild(String name) {
		return EFS.getNullFileSystem().getStore(new Path(name).makeAbsolute());
	}

	@Override
	public void delete(int options, IProgressMonitor monitor) {
		//nothing to do - virtual resources don't exist in any physical file system
	}

	@Override
	public String getName() {
		return "other";
	}

	@Override
	public IFileStore getParent() {
		return null;
	}

	@Override
	public InputStream openInputStream(int options, IProgressMonitor monitor) throws CoreException {
		return null;
	}

	@Override
	public URI toURI() {
		return location;
	}
}
