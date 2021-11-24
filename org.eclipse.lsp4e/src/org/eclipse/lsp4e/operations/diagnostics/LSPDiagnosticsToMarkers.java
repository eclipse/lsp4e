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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.IAnnotationModelExtension;
import org.eclipse.jface.text.source.ISourceViewer;
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
			String uri = diagnostics.getUri();
			IResource resource = LSPEclipseUtils.findResourceFor(uri);
			if (resource != null && resource.exists()) {
				updateMarkers(diagnostics, resource);
			} else {
				LSPEclipseUtils.findOpenEditorsFor(LSPEclipseUtils.toUri(uri)).stream()
					.map(reference -> reference.getEditor(true))
					.filter(Objects::nonNull)
					.map(LSPEclipseUtils::getTextViewer)
					.filter(Objects::nonNull)
					.filter(ISourceViewer.class::isInstance)
					.map(ISourceViewer.class::cast)
					.forEach(sourceViewer -> updateEditorAnnotations(sourceViewer, diagnostics));
			}
		} catch (CoreException ex) {
			LanguageServerPlugin.logError(ex);
		}
	}

	private void updateEditorAnnotations(@NonNull ISourceViewer sourceViewer, PublishDiagnosticsParams diagnostics) {
		IAnnotationModel annotationModel = sourceViewer.getAnnotationModel();
		if (annotationModel == null) {
			return;
		}
		if (annotationModel instanceof IAnnotationModelExtension) {
			Set<Annotation> toRemove = new HashSet<>();
			annotationModel.getAnnotationIterator().forEachRemaining(annotation -> {
				if (annotation instanceof DiagnosticAnnotation) {
					toRemove.add(annotation);
				}
			});
			Map<Annotation, Position> toAdd = new HashMap<>(diagnostics.getDiagnostics().size(), 1.f);
			diagnostics.getDiagnostics().forEach(diagnostic -> {
				try {
					int startOffset = LSPEclipseUtils.toOffset(diagnostic.getRange().getStart(), sourceViewer.getDocument());
					int endOffset = LSPEclipseUtils.toOffset(diagnostic.getRange().getEnd(), sourceViewer.getDocument());
					toAdd.put(new DiagnosticAnnotation(diagnostic), new Position(startOffset, endOffset - startOffset));
				} catch (BadLocationException ex) {
					LanguageServerPlugin.logError(ex);
				}
			});
			((IAnnotationModelExtension)annotationModel).replaceAnnotations(toRemove.toArray(new Annotation[toRemove.size()]), toAdd);
		}
	}

	private void updateMarkers(PublishDiagnosticsParams diagnostics, IResource resource) throws CoreException {
		Set<IMarker> toDeleteMarkers = new HashSet<>(
				Arrays.asList(resource.findMarkers(LS_DIAGNOSTIC_MARKER_TYPE, false, IResource.DEPTH_ONE)));
		toDeleteMarkers
				.removeIf(marker -> !Objects.equals(marker.getAttribute(LANGUAGE_SERVER_ID, ""), languageServerId)); //$NON-NLS-1$
		List<Diagnostic> newDiagnostics = new ArrayList<>();
		Map<IMarker, Diagnostic> toUpdate = new HashMap<>();
		for (Diagnostic diagnostic : diagnostics.getDiagnostics()) {
			IMarker associatedMarker = getExistingMarkerFor(resource, diagnostic, toDeleteMarkers);
			if (associatedMarker == null) {
				newDiagnostics.add(diagnostic);
			} else {
				toDeleteMarkers.remove(associatedMarker);
				toUpdate.put(associatedMarker, diagnostic);
			}
		}
		IWorkspaceRunnable runnable = monitor -> {
			if (resource.exists()) {
				for (Diagnostic diagnostic : newDiagnostics) {
					resource.createMarker(LS_DIAGNOSTIC_MARKER_TYPE, computeMarkerAttributes(resource, diagnostic));
				}
				for (Entry<IMarker, Diagnostic> entry : toUpdate.entrySet()) {
					updateMarker(resource, entry.getValue(), entry.getKey());
				}
				toDeleteMarkers.forEach(t -> {
					try {
						t.delete();
					} catch (CoreException e) {
						LanguageServerPlugin.logError(e);
					}
				});
			}
		};
		ResourcesPlugin.getWorkspace().run(runnable, new NullProgressMonitor());
	}

	protected void updateMarker(IResource resource, Diagnostic diagnostic, IMarker marker) {
		try {
			Map<String, Object> targetAttributes = computeMarkerAttributes(resource, diagnostic);
			if (!targetAttributes.equals(marker.getAttributes())) {
				marker.setAttributes(targetAttributes);
			}
		} catch (CoreException e) {
			LanguageServerPlugin.logError(e);
		}
	}

	private Map<String, Object> computeMarkerAttributes(IResource resource, Diagnostic diagnostic)
			throws CoreException {
		Map<String, Object> targetAttributes = new HashMap<>(7);
		targetAttributes.put(LSP_DIAGNOSTIC, diagnostic);
		targetAttributes.put(LANGUAGE_SERVER_ID, this.languageServerId);
		targetAttributes.put(IMarker.MESSAGE, diagnostic.getMessage());
		targetAttributes.put(IMarker.SEVERITY, LSPEclipseUtils.toEclipseMarkerSeverity(diagnostic.getSeverity()));
		if (resource.getType() == IResource.FILE) {
			try {
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
				targetAttributes.put(IMarker.CHAR_START, start);
				targetAttributes.put(IMarker.CHAR_END, end);
				targetAttributes.put(IMarker.LINE_NUMBER, document.getLineOfOffset(start) + 1);
			} catch (BadLocationException ex) {
				LanguageServerPlugin.logError(ex);
			}
		}
		return targetAttributes;
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