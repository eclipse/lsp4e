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

import java.net.URI;

import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.filesystem.provider.FileSystem;

/**
 * A file system to test custom filesystems
 *
 */
public class OtherFileSystem extends FileSystem {

	@Override
	public IFileStore getStore(URI uri) {
		return new OtherFileStore(uri);
	}

}
