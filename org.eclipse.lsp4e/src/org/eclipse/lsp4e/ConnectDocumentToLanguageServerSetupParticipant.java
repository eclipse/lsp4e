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
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LanguageServersRegistry.LanguageServerDefinition;

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
					char res = document.getChar(index);
					index++;
					return res;
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
		Job job = new Job("Initialize Language Servers for " + location.toFile().getName()) { //$NON-NLS-1$
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				String fileName = location.segment(location.segmentCount() - 1);
				IContentType[] fileContentTypes = new IContentType[0];
				try (InputStream contents = new DocumentInputStream(document)) {
					fileContentTypes = Platform.getContentTypeManager().findContentTypesFor(contents, fileName);
				} catch (IOException e) {
					LanguageServerPlugin.logError(e);
				}

				if (locationKind == LocationKind.IFILE) {
					IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(location);
					if (!file.exists()) { // file probably deleted in the meantime.
						return Status.OK_STATUS;
					}
					IProject project = file.getProject();

					// create servers one for available content type
					for (IContentType contentType : fileContentTypes) {
						for (LanguageServerDefinition serverDefinition : LanguageServersRegistry.getInstance().findProviderFor(contentType)) {
							if (serverDefinition != null) {
								try {
									ProjectSpecificLanguageServerWrapper lsWrapperForConnection = LanguageServiceAccessor.getLSWrapperForConnection(project, contentType, serverDefinition);
									if (lsWrapperForConnection != null) {
										IPath fileLocation = file.getLocation();
										if (fileLocation != null) {
											lsWrapperForConnection.connect(fileLocation, document);
										}
									}
								} catch (IOException e) {
									return new Status(IStatus.ERROR, LanguageServerPlugin.PLUGIN_ID, e.getMessage(), e);
								}
							}
						}
					}
				} else {
					// only support workspace file so far
				};
				return Status.OK_STATUS;
			}
		};
		job.setUser(true);
		job.setPriority(Job.INTERACTIVE);
		job.schedule(100); // give some time to populate doc
	}

}
