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
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.IAnnotationModelExtension;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.lsp4e.IMarkerAttributeComputer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.ui.texteditor.MarkerUtilities;

public class LSPDiagnosticsToMarkers implements Consumer<PublishDiagnosticsParams> {

	public static final String LSP_DIAGNOSTIC = "lspDiagnostic"; //$NON-NLS-1$
	public static final String LANGUAGE_SERVER_ID = "languageServerId"; //$NON-NLS-1$
	public static final String LS_DIAGNOSTIC_MARKER_TYPE = "org.eclipse.lsp4e.diagnostic"; //$NON-NLS-1$
	private final @NonNull String languageServerId;
	private final @NonNull String markerType;
	private final Optional<IMarkerAttributeComputer> markerAttributeComputer;

	public LSPDiagnosticsToMarkers(@NonNull String serverId, @Nullable String markerType, @Nullable IMarkerAttributeComputer markerAttributeComputer) {
		this.languageServerId = serverId;
		this.markerType = markerType != null ? markerType : LS_DIAGNOSTIC_MARKER_TYPE;
		this.markerAttributeComputer = Optional.ofNullable(markerAttributeComputer);
	}

	public LSPDiagnosticsToMarkers(@NonNull String serverId) {
		this(serverId, null, null);
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
				Arrays.asList(resource.findMarkers(markerType, false, IResource.DEPTH_ONE)));
		toDeleteMarkers
				.removeIf(marker -> !Objects.equals(marker.getAttribute(LANGUAGE_SERVER_ID, ""), languageServerId)); //$NON-NLS-1$
		List<Diagnostic> newDiagnostics = new ArrayList<>();
		Map<IMarker, Diagnostic> toUpdate = new HashMap<>();
		IDocument document = diagnostics.getDiagnostics().isEmpty() ? null : LSPEclipseUtils.getDocument(resource);
		for (Diagnostic diagnostic : diagnostics.getDiagnostics()) {
			IMarker associatedMarker = getExistingMarkerFor(document, diagnostic, toDeleteMarkers);
			if (associatedMarker == null) {
				newDiagnostics.add(diagnostic);
			} else {
				toDeleteMarkers.remove(associatedMarker);
				toUpdate.put(associatedMarker, diagnostic);
			}
		}
		IWorkspaceRunnable runnable = monitor -> {
			try {
				for (Diagnostic diagnostic : newDiagnostics) {
					Map<String, Object> markerAttributes = computeMarkerAttributes(document, diagnostic, resource);
					resource.createMarker(markerType, markerAttributes);
				}
				for (Entry<IMarker, Diagnostic> entry : toUpdate.entrySet()) {
					Map<String, Object> markerAttributes = computeMarkerAttributes(document, entry.getValue(), resource);
					updateMarker(markerAttributes, entry.getKey());
				}
				toDeleteMarkers.forEach(t -> {
					try {
						t.delete();
					} catch (CoreException e) {
						LanguageServerPlugin.logError(e);
					}
				});
			} catch (CoreException e) {
				if (resource.exists()) {
					LanguageServerPlugin.logError(e);
				}
			}
		};
		IWorkspace ws = resource.getWorkspace();
		ws.run(runnable, ws.getRuleFactory().markerRule(resource), IWorkspace.AVOID_UPDATE, new NullProgressMonitor());
	}

	protected void updateMarker(@NonNull Map<String, Object> targetAttributes, @NonNull IMarker marker) {
		try {
			if (!targetAttributes.equals(marker.getAttributes())) {
				marker.setAttributes(targetAttributes);
			}
		} catch (CoreException e) {
			LanguageServerPlugin.logError(e);
		}
	}

	private IMarker getExistingMarkerFor(IDocument document, Diagnostic diagnostic, Set<IMarker> remainingMarkers) {
		if (document == null) {
			return null;
		}

		for (IMarker marker : remainingMarkers) {
			try {
				if (LSPEclipseUtils.toOffset(diagnostic.getRange().getStart(), document) == MarkerUtilities.getCharStart(marker)
						&& LSPEclipseUtils.toOffset(diagnostic.getRange().getEnd(), document) == MarkerUtilities.getCharEnd(marker)
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

	private @NonNull Map<String, Object> computeMarkerAttributes(@Nullable IDocument document,
			@NonNull Diagnostic diagnostic, @NonNull IResource resource) {
		Map<String, Object> attributes = new HashMap<>(8);
		attributes.put(LSP_DIAGNOSTIC, diagnostic);
		attributes.put(LANGUAGE_SERVER_ID, languageServerId);
		attributes.put(IMarker.MESSAGE, diagnostic.getMessage());
		attributes.put(IMarker.SEVERITY, LSPEclipseUtils.toEclipseMarkerSeverity(diagnostic.getSeverity()));

		if (document != null) {
			Range range = diagnostic.getRange();
			int documentLength = document.getLength();
			try {
				int start = Math.min(LSPEclipseUtils.toOffset(range.getStart(), document), documentLength);
				int end = Math.min(LSPEclipseUtils.toOffset(range.getEnd(), document), documentLength);
				int lineOfStartOffset = document.getLineOfOffset(start);
				if (start == end && documentLength > end) {
					end++;
					if (document.getLineOfOffset(end) != lineOfStartOffset) {
						start--;
						end--;
					}
				}
				attributes.put(IMarker.CHAR_START, start);
				attributes.put(IMarker.CHAR_END, end);
				attributes.put(IMarker.LINE_NUMBER, lineOfStartOffset + 1);
			} catch (BadLocationException ex) {
				LanguageServerPlugin.logError(ex);
			}
		}


		markerAttributeComputer
				.ifPresent(c -> c.addMarkerAttributesForDiagnostic(diagnostic, document, resource, attributes));

		return attributes;
	}
}
