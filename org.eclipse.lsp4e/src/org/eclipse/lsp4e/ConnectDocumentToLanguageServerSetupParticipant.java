/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e;

import java.io.IOException;
import java.io.InputStream;

import org.eclipse.core.filebuffers.IDocumentSetupParticipant;
import org.eclipse.core.filebuffers.IDocumentSetupParticipantExtension;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.server.StreamConnectionProvider;

/**
 * Startup the language servers that can be used by the document.
 *
 */
public class ConnectDocumentToLanguageServerSetupParticipant implements IDocumentSetupParticipant, IDocumentSetupParticipantExtension {

	private class DocumentInputStream extends InputStream {
		private int index = 0;
		private IDocument document;

		DocumentInputStream(IDocument document) {
			this.document = document;
		}

		@Override
		public int read() throws IOException {
			if (index < document.getLength()) {
				try {
					return document.getChar(index);
				} catch (BadLocationException e) {
					throw new IOException(e);
				}
			}
			return -1;
		}

	}

	@Override
	public void setup(IDocument document) {
		ITextFileBuffer buffer = ITextFileBufferManager.DEFAULT.getTextFileBuffer(document);
		if (buffer == null || buffer.getLocation() == null) {
			return;
		}
		setup(document, buffer.getLocation(), LocationKind.IFILE);
	}

	@Override
	public void setup(IDocument document, IPath location, LocationKind locationKind) {
		String fileName = location.segment(location.segmentCount() - 1);
		IContentType[] fileContentTypes = new IContentType[0];
		try (InputStream contents = new DocumentInputStream(document)) {
			fileContentTypes = Platform.getContentTypeManager().findContentTypesFor(contents, fileName);
		} catch (IOException e) {
			LanguageServerPlugin.logError(e);
		}

		if (locationKind == LocationKind.IFILE) {
			IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(location);
			IProject project = file.getProject();

			// create servers one for available content type
			for (IContentType contentType : fileContentTypes) {
				for (StreamConnectionProvider connection : LSPStreamConnectionProviderRegistry.getInstance().findProviderFor(contentType)) {
					if (connection != null) {
						try {
							LanguageServiceAccessor.getLSWrapperForConnection(project, contentType, connection);
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			}
		} else {
			// only support workspace file so far
			return;
		}
	}

}
