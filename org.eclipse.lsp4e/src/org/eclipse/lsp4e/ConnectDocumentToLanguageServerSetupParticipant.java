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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.filebuffers.IDocumentSetupParticipant;
import org.eclipse.core.filebuffers.IDocumentSetupParticipantExtension;
import org.eclipse.core.filebuffers.IFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.text.IDocument;

/**
 * Startup the language servers that can be used by the document.
 *
 */
public class ConnectDocumentToLanguageServerSetupParticipant implements IDocumentSetupParticipant, IDocumentSetupParticipantExtension {
	private final Map<IPath, Job> locationMap = new HashMap<>();

	public ConnectDocumentToLanguageServerSetupParticipant() {

		ITextFileBufferManager.DEFAULT.addFileBufferListener(new FileBufferListenerAdapter() {
			@Override
			public void bufferDisposed(IFileBuffer buffer) {
				Job job = locationMap.remove(buffer.getLocation());
				if (job != null) {
					job.cancel();
				}
			}
		});
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
	public void setup(final IDocument document, IPath location, LocationKind locationKind) {
		if (document == null) {
			return;
		}
		Job job = new Job("Initialize Language Servers for " + location.toFile().getName()) { //$NON-NLS-1$
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				ITextFileBuffer buffer = ITextFileBufferManager.DEFAULT.getTextFileBuffer(document);
				if (buffer == null || buffer.getLocation() == null) { // document no more relevant
					return Status.OK_STATUS;
				}
				if (monitor.isCanceled()) {
					return Status.CANCEL_STATUS;
				}
				// connect to LS so they start receiving notifications and pushing diagnostics
				LanguageServiceAccessor.getLanguageServers(document, capabilities -> true);
				locationMap.remove(location);
				return Status.OK_STATUS;
			}
		};
		locationMap.put(location, job);
		job.setUser(true);
		job.setPriority(Job.INTERACTIVE);
		job.schedule(100); // give some time to populate doc and associate it with the IFile
	}

}
