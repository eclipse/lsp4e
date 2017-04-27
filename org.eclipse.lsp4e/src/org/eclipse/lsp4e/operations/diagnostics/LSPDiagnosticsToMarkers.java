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
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.ui.texteditor.MarkerUtilities;

public class LSPDiagnosticsToMarkers implements Consumer<PublishDiagnosticsParams> {

	public static final String LSP_DIAGNOSTIC = "lspDiagnostic"; //$NON-NLS-1$
	public static final String LS_DIAGNOSTIC_MARKER_TYPE = "org.eclipse.lsp4e.diagnostic"; //$NON-NLS-1$
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
			Set<IMarker> remainingMarkers = new HashSet<>(
					Arrays.asList(resource.findMarkers(LS_DIAGNOSTIC_MARKER_TYPE, false, IResource.DEPTH_ONE)));
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
			// TODO mapping Eclipse <-> LS severity
			marker.setAttribute(IMarker.SEVERITY, LSPEclipseUtils.toEclipseMarkerSeverity(diagnostic.getSeverity()));
			if (resource.getType() != IResource.FILE) {
				return;
			}
			IFile file = (IFile) resource;
			ITextFileBufferManager manager = FileBuffers.getTextFileBufferManager();
			ITextFileBuffer textFileBuffer = manager.getTextFileBuffer(file.getFullPath(), LocationKind.IFILE);

			if (textFileBuffer == null) {
				manager.connect(file.getFullPath(), LocationKind.IFILE, new NullProgressMonitor());
				textFileBuffer = manager.getTextFileBuffer(file.getFullPath(), LocationKind.IFILE);
			}

			IDocument document = textFileBuffer.getDocument();
			int start = Math.min(LSPEclipseUtils.toOffset(diagnostic.getRange().getStart(), document),
					document.getLength());
			int end = Math.min(LSPEclipseUtils.toOffset(diagnostic.getRange().getEnd(), document),
					document.getLength());

			marker.setAttribute(IMarker.CHAR_START, start);
			marker.setAttribute(IMarker.CHAR_END, end);
			marker.setAttribute(IMarker.LINE_NUMBER, document.getLineOfOffset(start) + 1);
		} catch (CoreException | BadLocationException e) {
			LanguageServerPlugin.logError(e);
		}
	}

	private IMarker getExistingMarkerFor(IResource resource, Diagnostic diagnostic, Set<IMarker> remainingMarkers) {
		ITextFileBuffer textFileBuffer = FileBuffers.getTextFileBufferManager()
				.getTextFileBuffer(resource.getFullPath(), LocationKind.IFILE);
		if (textFileBuffer == null) {
			return null;
		}

		IDocument document = textFileBuffer.getDocument();
		for (IMarker marker : remainingMarkers) {
			int startOffset = MarkerUtilities.getCharStart(marker);
			int endOffset = MarkerUtilities.getCharEnd(marker);
			try {
				if (marker.getResource().getProjectRelativePath().toString().equals(diagnostic.getSource())
						&& LSPEclipseUtils.toOffset(diagnostic.getRange().getStart(), document) == startOffset + 1
						&& LSPEclipseUtils.toOffset(diagnostic.getRange().getEnd(), document) == endOffset + 1
						&& Objects.equals(marker.getAttribute(IMarker.MESSAGE), diagnostic.getMessage())) {
					return marker;
				}
			} catch (CoreException | BadLocationException e) {
				LanguageServerPlugin.logError(e);
			}
		}
		return null;
	}
}