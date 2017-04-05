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
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.server.StreamConnectionProvider;

/**
 * Startup the language servers that can be used by the document.
 *
 */
public class ConnectDocumentToLanguageServerSetupParticipant implements IDocumentSetupParticipant {

	@Override
	public void setup(IDocument document) {
		ITextFileBuffer buffer = ITextFileBufferManager.DEFAULT.getTextFileBuffer(document);
		if (buffer == null || buffer.getLocation() == null) {
			return;
		}
		IResource resource = ResourcesPlugin.getWorkspace().getRoot().findMember(buffer.getLocation());
		if (!resource.exists() || resource.getType() != IResource.FILE) {
			// only support workspace file so far
			return;
		}
		IFile file = (IFile) resource;
		IProject project = file.getProject();
		IContentType[] fileContentTypes = new IContentType[0];
		try (InputStream contents = file.getContents()) {
			fileContentTypes = Platform.getContentTypeManager().findContentTypesFor(contents, file.getName()); //TODO consider using document as inputstream
		} catch (CoreException e) {
			LanguageServerPlugin.logError(e);
		} catch (IOException e) {
			LanguageServerPlugin.logError(e);
		}

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
	}

}
