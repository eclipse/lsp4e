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
import org.eclipse.core.filebuffers.IFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.debug.DSPPlugin;

/**
 * The code is copied from LSPEclipseUtils.
 */
final class DocumentUtils {

	private static @Nullable ITextFileBuffer toBuffer(@Nullable IDocument document) {
		if (document == null) {
			return null;
		}
		ITextFileBufferManager bufferManager = FileBuffers.getTextFileBufferManager();
		if (bufferManager == null)
			return null;
		return bufferManager.getTextFileBuffer(document);
	}

	public static @Nullable URI toUri(IDocument document) {
		ITextFileBuffer buffer = toBuffer(document);
		IPath path = toPath(buffer);
		IFile file = getFile(path);
		if (file != null) {
			return toUri(file);
		} else if (path != null) {
			return toUri(path.toFile());
		} else if (buffer != null && buffer.getFileStore() != null) {
			return buffer.getFileStore().toURI();
		}
		return null;
	}

	private static @Nullable IPath toPath(@Nullable IFileBuffer buffer) {
		if (buffer != null) {
			return buffer.getLocation();
		}
		return null;
	}

	private static URI toUri(IPath absolutePath) {
		return toUri(absolutePath.toFile());
	}

	private static @Nullable URI toUri(IResource resource) {
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
			DSPPlugin.logError(e);
			return file.getAbsoluteFile().toURI();
		}
	}

	/**
	 * @return null if no file exists at the given path or the given path points to
	 *         a file outside the workspace
	 */
	private static @Nullable IFile getFile(@Nullable IPath path) {
		if (path == null) {
			return null;
		}

		final IFile res = path.segmentCount() == 1 //
				? ResourcesPlugin.getWorkspace().getRoot().getFileForLocation(path)
				: ResourcesPlugin.getWorkspace().getRoot().getFile(path);
		if (res != null && res.exists()) {
			return res;
		}
		return null;
	}

	private DocumentUtils() {
	}
}
