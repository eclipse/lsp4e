/*******************************************************************************
 * Copyright (c) 2016, 2018 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Lucas Bullen (Red Hat Inc.) - [Bug 528333] Performance problem with diagnostics
 *******************************************************************************/
package org.eclipse.lsp4e.operations.diagnostics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
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
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.ui.texteditor.MarkerUtilities;

public class LSPDiagnosticsToMarkers implements Consumer<PublishDiagnosticsParams> {

	public static final String LSP_DIAGNOSTIC = "lspDiagnostic"; //$NON-NLS-1$
	public static final String LANGUAGE_SERVER_ID = "languageServerId"; //$NON-NLS-1$
	public static final String LS_DIAGNOSTIC_MARKER_TYPE = "org.eclipse.lsp4e.diagnostic"; //$NON-NLS-1$
	private final @NonNull String languageServerId;

	public LSPDiagnosticsToMarkers(@NonNull String serverId) {
		this.languageServerId = serverId;
	}

	/**
	 *
	 * @param project
	 * @param serverId
	 *            ignored
	 * @deprecated
	 */
	@Deprecated
	public LSPDiagnosticsToMarkers(IProject project, @NonNull String serverId) {
		this(serverId);
	}

	@Override
	public void accept(PublishDiagnosticsParams diagnostics) {
		try {
			// fix issue with file:/// vs file:/
			String uri = diagnostics.getUri();
			IResource resource = LSPEclipseUtils.findResourceFor(uri);
			if (resource == null || !resource.exists()) {
				return;
			}
			Set<IMarker> remainingMarkers = new HashSet<>(
					Arrays.asList(resource.findMarkers(LS_DIAGNOSTIC_MARKER_TYPE, false, IResource.DEPTH_ONE)));
			remainingMarkers
					.removeIf(marker -> !Objects.equals(marker.getAttribute(LANGUAGE_SERVER_ID, ""), languageServerId)); //$NON-NLS-1$
			List<Diagnostic> newDiagnostics = new ArrayList<>();
			for (Diagnostic diagnostic : diagnostics.getDiagnostics()) {
				IMarker associatedMarker = getExistingMarkerFor(resource, diagnostic, remainingMarkers);
				if (associatedMarker == null) {
					newDiagnostics.add(diagnostic);
				} else {
					remainingMarkers.remove(associatedMarker);
				}
			}
			IWorkspaceRunnable runnable = new IWorkspaceRunnable() {
				@Override
				public void run(IProgressMonitor monitor) throws CoreException {
					Iterator<IMarker> markerIterator = remainingMarkers.iterator();
					for (Diagnostic diagnostic : newDiagnostics) {
						IMarker marker;
						if (markerIterator.hasNext()) {
							marker = markerIterator.next();
						} else {
							marker = resource.createMarker(LS_DIAGNOSTIC_MARKER_TYPE);
						}
						updateMarker(resource, diagnostic, marker);
					}
					while (markerIterator.hasNext()) {
						markerIterator.next().delete();
					}
				}
			};
			ResourcesPlugin.getWorkspace().run(runnable, new NullProgressMonitor());
		} catch (CoreException ex) {
			LanguageServerPlugin.logError(ex);
		}
	}

	protected void updateMarker(IResource resource, Diagnostic diagnostic, IMarker marker) {
		try {
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
			if (start == end && document.getLength() > end) {
				end++;
				if (document.getLineOfOffset(end) != document.getLineOfOffset(start)) {
					start--;
					end--;
				}
			}
			marker.setAttributes(
					new String[] {
							LSP_DIAGNOSTIC,
							LANGUAGE_SERVER_ID,
							IMarker.MESSAGE,
							IMarker.SEVERITY,
							IMarker.CHAR_START,
							IMarker.CHAR_END,
							IMarker.LINE_NUMBER
					},
					new Object[] {
							diagnostic,
							this.languageServerId,
							diagnostic.getMessage(),
							LSPEclipseUtils.toEclipseMarkerSeverity(diagnostic.getSeverity()),
							start,
							end,
							document.getLineOfOffset(start) + 1
					});
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
				if (LSPEclipseUtils.toOffset(diagnostic.getRange().getStart(), document) == startOffset
						&& LSPEclipseUtils.toOffset(diagnostic.getRange().getEnd(), document) == endOffset
						&& Objects.equals(marker.getAttribute(IMarker.MESSAGE), diagnostic.getMessage())
						&& Objects.equals(marker.getAttribute(LANGUAGE_SERVER_ID), this.languageServerId)) {
					return marker;
				}
			} catch (CoreException | BadLocationException e) {
				LanguageServerPlugin.logError(e);
			}
		}
		return null;
	}
}