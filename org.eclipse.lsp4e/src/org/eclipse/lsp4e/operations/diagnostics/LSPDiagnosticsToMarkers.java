/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.diagnostics;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;

import com.google.common.base.Objects;

public class LSPDiagnosticsToMarkers implements Consumer<PublishDiagnosticsParams> {

	public static final String LSP_DIAGNOSTIC = "lspDiagnostic"; //$NON-NLS-1$
	private static final String LS_DIAGNOSTIC_MARKER_TYPE = "org.eclipse.lsp4e.diagnostic"; //$NON-NLS-1$
	private final IProject project;

	public LSPDiagnosticsToMarkers(IProject project) {
		this.project = project;
	}

	@Override
	public void accept(PublishDiagnosticsParams diagnostics) {
		try {
			// fix issue with file:/// vs file:/
			String uri = diagnostics.getUri();
			IResource resource = LSPEclipseUtils.findResourceFor(uri);
			if (resource == null || !resource.exists()) {
				resource = project;
			}
			Set<IMarker> remainingMarkers = new HashSet<>(Arrays.asList(resource.findMarkers(LS_DIAGNOSTIC_MARKER_TYPE, false, IResource.DEPTH_ONE)));
			for (Diagnostic diagnostic : diagnostics.getDiagnostics()) {
				IMarker associatedMarker = getExistingMarkerFor(resource, diagnostic, remainingMarkers);
				if (associatedMarker == null) {
					createMarkerForDiagnostic(resource, diagnostic);
				} else {
					remainingMarkers.remove(associatedMarker);
				}
			}
			for (IMarker marker : remainingMarkers) {
				marker.delete();
			}
		} catch (CoreException ex) {
			LanguageServerPlugin.logError(ex);
		}
	}

	private void createMarkerForDiagnostic(IResource resource, Diagnostic diagnostic) {
		try {
			IMarker marker = resource.createMarker(LS_DIAGNOSTIC_MARKER_TYPE);
			marker.setAttribute(LSP_DIAGNOSTIC, diagnostic);
			marker.setAttribute(IMarker.MESSAGE, diagnostic.getMessage());
			marker.setAttribute(IMarker.SEVERITY, LSPEclipseUtils.toEclipseMarkerSeverity(diagnostic.getSeverity())); // TODO mapping Eclipse <-> LS severity
			if (resource.getType() == IResource.FILE) {
				IFile file = (IFile)resource;
				IDocument document = FileBuffers.getTextFileBufferManager().getTextFileBuffer(file.getFullPath(), LocationKind.IFILE).getDocument();
				marker.setAttribute(IMarker.CHAR_START, LSPEclipseUtils.toOffset(diagnostic.getRange().getStart(), document));
				marker.setAttribute(IMarker.CHAR_END, LSPEclipseUtils.toOffset(diagnostic.getRange().getEnd(), document));
				marker.setAttribute(IMarker.LINE_NUMBER, diagnostic.getRange().getStart().getLine());
			}
		} catch (Exception ex) {
			LanguageServerPlugin.logError(ex);
		}
	}

	private IMarker getExistingMarkerFor(IResource resource, Diagnostic diagnostic, Set<IMarker> remainingMarkers) {
		ITextFileBuffer textFileBuffer = FileBuffers.getTextFileBufferManager().getTextFileBuffer(resource.getFullPath(), LocationKind.IFILE);
		if (textFileBuffer == null) {
			return null;
		}
		IDocument document = textFileBuffer.getDocument();
		for (IMarker marker : remainingMarkers) {
			int startOffset = marker.getAttribute(IMarker.CHAR_START, -1);
			int endOffset = marker.getAttribute(IMarker.CHAR_END, -1);
			try {
				if (marker.getResource().getProjectRelativePath().toString().equals(diagnostic.getSource()) 
						&& LSPEclipseUtils.toOffset(diagnostic.getRange().getStart(), document) == startOffset + 1
						&& LSPEclipseUtils.toOffset(diagnostic.getRange().getEnd(), document) == endOffset + 1
						&& Objects.equal(marker.getAttribute(IMarker.MESSAGE), diagnostic.getMessage())) {
					return marker;
				}
			} catch (Exception e) {
				LanguageServerPlugin.logError(e);
			}
		}
		return null;
	}
}