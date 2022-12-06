/*******************************************************************************
 * Copyright (c) 2016, 2017 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Max Bureck (Fraunhofer FOKUS) - Moved command execution to CommandExecutor
 *******************************************************************************/
package org.eclipse.lsp4e.operations.codeactions;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.command.CommandExecutor;
import org.eclipse.lsp4e.operations.diagnostics.LSPDiagnosticsToMarkers;
import org.eclipse.lsp4j.Command;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.IMarkerResolution;
import org.eclipse.ui.views.markers.WorkbenchMarkerResolution;

public class CommandMarkerResolution extends WorkbenchMarkerResolution implements IMarkerResolution {

	private final @NonNull Command command;

	public CommandMarkerResolution(@NonNull Command command) {
		this.command = command;
	}

	@Override
	public String getLabel() {
		return this.command.getTitle();
	}

	@Override
	public void run(IMarker marker) {
		IResource resource = marker.getResource();
		IDocument document = LSPEclipseUtils.getExistingDocument(resource);
		boolean temporaryLoadDocument = document == null;
		if (temporaryLoadDocument) {
			document = LSPEclipseUtils.getDocument(resource);
		}
		if(document != null) {
			String languageServerId = marker.getAttribute(LSPDiagnosticsToMarkers.LANGUAGE_SERVER_ID, null);
			CommandExecutor.executeCommand(command, document, languageServerId);
			if (temporaryLoadDocument) {
				try {
					FileBuffers.getTextFileBufferManager().disconnect(resource.getFullPath(), LocationKind.IFILE, new NullProgressMonitor());
				} catch (CoreException e) {
					LanguageServerPlugin.logError(e);
				}
			}
		}
	}

	@Override
	public String getDescription() {
		return command.getTitle();
	}

	@Override
	public Image getImage() {
		return null;
	}

	@Override
	public IMarker[] findOtherMarkers(IMarker[] markers) {
		return new IMarker[0];
	}

}
