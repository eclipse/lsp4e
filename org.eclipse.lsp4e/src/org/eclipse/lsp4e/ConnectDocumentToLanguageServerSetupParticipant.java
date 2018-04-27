/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
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
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;

/**
 * Startup the language servers that can be used by the document.
 *
 */
public class ConnectDocumentToLanguageServerSetupParticipant implements IDocumentSetupParticipant, IDocumentSetupParticipantExtension {

	private static final class DocumentInputStream extends InputStream {
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
				// connect to LS so they start receiving notifications and pushing diagnostics
				LanguageServiceAccessor.getLSPDocumentInfosFor(document, capabilities -> Boolean.TRUE);
				return Status.OK_STATUS;
			}
		};
		job.setUser(true);
		job.setPriority(Job.INTERACTIVE);
		job.schedule(100); // give some time to populate doc and associate it with the IFile
	}

}
