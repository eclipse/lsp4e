/*******************************************************************************
 * Copyright (c) 2022 Red Hat, Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.debug.breakpoints;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.text.IDocument;

/*
 * All this is mostly copied from LSPEclipseUtils.
 */
final class DocumentUtils {

	private static ITextFileBuffer toBuffer(IDocument document) {
		ITextFileBufferManager bufferManager = FileBuffers.getTextFileBufferManager();
		if (bufferManager == null)
			return null;
		return bufferManager.getTextFileBuffer(document);
	}

	public static URI toUri(IDocument document) {
		IFile file = getFile(document);
		if (file != null) {
			return toUri(file);
		} else {
			ITextFileBuffer buffer = toBuffer(document);
			if (buffer != null) {
				IPath path = toPath(buffer);
				if (path != null) {
					return toUri(path.toFile());
				} else {
					return buffer.getFileStore().toURI();
				}
			}
		}
		return null;
	}

	private static IPath toPath(ITextFileBuffer buffer) {
		if (buffer != null) {
			return buffer.getLocation();
		}
		return null;
	}

	private static IPath toPath(IDocument document) {
		return toPath(toBuffer(document));
	}

	private static URI toUri(IPath absolutePath) {
		return toUri(absolutePath.toFile());
	}

	private static URI toUri(IResource resource) {
		URI adaptedURI = Adapters.adapt(resource, URI.class, true);
		if (adaptedURI != null) {
			return adaptedURI;
		}
		IPath location = resource.getLocation();
		if (location != null) {
			return toUri(location);
		}
		return resource.getLocationURI();
	}

	private static URI toUri(File file) {
		// URI scheme specified by language server protocol and LSP
		try {
			return new URI("file", "", file.getAbsoluteFile().toURI().getPath(), null); //$NON-NLS-1$ //$NON-NLS-2$
		} catch (URISyntaxException e) {
			return file.getAbsoluteFile().toURI();
		}
	}

	private static IFile getFile(IDocument document) {
		IPath path = toPath(document);
		return getFile(path);
	}

	private static IFile getFile(IPath path) {
		if (path == null) {
			return null;
		}
		IFile res = ResourcesPlugin.getWorkspace().getRoot().getFile(path);
		if (res != null && res.exists()) {
			return res;
		} else {
			return null;
		}
	}

	private DocumentUtils() {
	}
}
