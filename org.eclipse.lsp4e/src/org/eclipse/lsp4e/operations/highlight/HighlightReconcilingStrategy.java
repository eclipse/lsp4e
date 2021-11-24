/*******************************************************************************
 * Copyright (c) 2017 Rogue Wave Software Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Michal Niewrzal (Rogue Wave Software Inc.) - initial implementation
 *  Angelo Zerr <angelo.zerr@gmail.com> - fix Bug 521020
 *  Lucas Bullen (Red Hat Inc.) - fix Bug 522737, 517428, 527426
 *******************************************************************************/
package org.eclipse.lsp4e.operations.highlight;

import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.runtime.ICoreRunnable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.PreferenceChangeEvent;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ISynchronizable;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.reconciler.DirtyRegion;
import org.eclipse.jface.text.reconciler.IReconcilingStrategy;
import org.eclipse.jface.text.reconciler.IReconcilingStrategyExtension;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.IAnnotationModelExtension;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.viewers.IPostSelectionProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightKind;
import org.eclipse.lsp4j.DocumentHighlightParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.swt.custom.StyledText;

/**
 * {@link IReconcilingStrategy} implementation to Highlight Symbol (mark
 * occurrences like).
 *
 */
public class HighlightReconcilingStrategy
		implements IReconcilingStrategy, IReconcilingStrategyExtension, IPreferenceChangeListener {

	public static final String TOGGLE_HIGHLIGHT_PREFERENCE = "org.eclipse.ui.genericeditor.togglehighlight"; //$NON-NLS-1$

	public static final String READ_ANNOTATION_TYPE = "org.eclipse.lsp4e.read"; //$NON-NLS-1$
	public static final String WRITE_ANNOTATION_TYPE = "org.eclipse.lsp4e.write"; //$NON-NLS-1$
	public static final String TEXT_ANNOTATION_TYPE = "org.eclipse.lsp4e.text"; //$NON-NLS-1$

	private boolean enabled;
	private ISourceViewer sourceViewer;
	private IDocument document;

	private CompletableFuture<?> request;
	private Job highlightJob;

	/**
	 * Holds the current occurrence annotations.
	 */
	private Annotation[] fOccurrenceAnnotations = null;

	class EditorSelectionChangedListener implements ISelectionChangedListener {

		public void install(ISelectionProvider selectionProvider) {
			if (selectionProvider == null)
				return;

			if (selectionProvider instanceof IPostSelectionProvider) {
				IPostSelectionProvider provider = (IPostSelectionProvider) selectionProvider;
				provider.addPostSelectionChangedListener(this);
			} else {
				selectionProvider.addSelectionChangedListener(this);
			}
		}

		public void uninstall(ISelectionProvider selectionProvider) {
			if (selectionProvider == null)
				return;

			if (selectionProvider instanceof IPostSelectionProvider) {
				IPostSelectionProvider provider = (IPostSelectionProvider) selectionProvider;
				provider.removePostSelectionChangedListener(this);
			} else {
				selectionProvider.removeSelectionChangedListener(this);
			}
		}

		@Override
		public void selectionChanged(SelectionChangedEvent event) {
			updateHighlights(event.getSelection());
		}
	}

	private void updateHighlights(ISelection selection) {
		if (!(selection instanceof ITextSelection)) {
			return;
		}
		ITextSelection textSelection = (ITextSelection) selection;
		if (highlightJob != null) {
			highlightJob.cancel();
		}
		highlightJob = Job.createSystem("LSP4E Highlight", //$NON-NLS-1$
				(ICoreRunnable)(monitor -> collectHighlights(textSelection.getOffset(), monitor)));
		highlightJob.schedule();
	}

	private EditorSelectionChangedListener editorSelectionChangedListener;

	public void install(ITextViewer viewer) {
		if (!(viewer instanceof ISourceViewer)) {
			return;
		}
		IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode(LanguageServerPlugin.PLUGIN_ID);
		preferences.addPreferenceChangeListener(this);
		this.enabled = preferences.getBoolean(TOGGLE_HIGHLIGHT_PREFERENCE, true);
		this.sourceViewer = (ISourceViewer) viewer;
		editorSelectionChangedListener = new EditorSelectionChangedListener();
		editorSelectionChangedListener.install(sourceViewer.getSelectionProvider());
	}

	public void uninstall() {
		if (sourceViewer != null) {
			editorSelectionChangedListener.uninstall(sourceViewer.getSelectionProvider());
		}
		IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode(LanguageServerPlugin.PLUGIN_ID);
		preferences.removePreferenceChangeListener(this);
		cancel();
	}

	@Override
	public void setProgressMonitor(IProgressMonitor monitor) {

	}

	@Override
	public void initialReconcile() {
		if (sourceViewer != null) {
			ISelectionProvider selectionProvider = sourceViewer.getSelectionProvider();
			final StyledText textWidget = sourceViewer.getTextWidget();
			if (textWidget != null && selectionProvider != null) {
				textWidget.getDisplay().asyncExec(() -> {
					if (!textWidget.isDisposed()) {
						updateHighlights(selectionProvider.getSelection());
					}
				});
			}
		}
	}

	@Override
	public void setDocument(IDocument document) {
		this.document = document;
	}

	/**
	 * Collect list of highlight for the given caret offset by consuming language
	 * server 'documentHighligh't.
	 *
	 * @param caretOffset
	 * @param monitor
	 */
	private void collectHighlights(int caretOffset, IProgressMonitor monitor) {
		if (sourceViewer == null || !enabled || monitor.isCanceled()) {
			return;
		}
		cancel();
		Position position;
		try {
			position = LSPEclipseUtils.toPosition(caretOffset, document);
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
			return;
		}
		URI uri = LSPEclipseUtils.toUri(document);
		if(uri == null) {
			return;
		}
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri.toString());
		DocumentHighlightParams params = new DocumentHighlightParams(identifier, position);
		request = LanguageServiceAccessor.getLanguageServers(document,
				capabilities -> LSPEclipseUtils.hasCapability(capabilities.getDocumentHighlightProvider()))
				.thenAcceptAsync(languageServers ->
				CompletableFuture.allOf(languageServers.stream()
						.map(languageServer -> languageServer.getTextDocumentService().documentHighlight(params))
						.map(request -> request.thenAcceptAsync(result -> {
							if (!monitor.isCanceled()) {
								updateAnnotations(result, sourceViewer.getAnnotationModel());
							}
						})).toArray(CompletableFuture[]::new)));
	}

	/**
	 * Cancel the last call of 'documentHighlight'.
	 */
	private void cancel() {
		if (request != null && !request.isDone()) {
			request.cancel(true);
			request = null;
		}
	}

	/**
	 * Update the UI annotations with the given list of DocumentHighlight.
	 *
	 * @param highlights
	 *            list of DocumentHighlight
	 * @param annotationModel
	 *            annotation model to update.
	 */
	private void updateAnnotations(List<? extends DocumentHighlight> highlights, IAnnotationModel annotationModel) {
		Map<Annotation, org.eclipse.jface.text.Position> annotationMap = new HashMap<>(highlights.size());
		for (DocumentHighlight h : highlights) {
			if (h != null) {
				try {
					int start = LSPEclipseUtils.toOffset(h.getRange().getStart(), document);
					int end = LSPEclipseUtils.toOffset(h.getRange().getEnd(), document);
					annotationMap.put(new Annotation(kindToAnnotationType(h.getKind()), false, null),
							new org.eclipse.jface.text.Position(start, end - start));
				} catch (BadLocationException e) {
					LanguageServerPlugin.logError(e);
				}
			}
		}

		synchronized (getLockObject(annotationModel)) {
			if (annotationModel instanceof IAnnotationModelExtension) {
				((IAnnotationModelExtension) annotationModel).replaceAnnotations(fOccurrenceAnnotations, annotationMap);
			} else {
				removeOccurrenceAnnotations();
				Iterator<Entry<Annotation, org.eclipse.jface.text.Position>> iter = annotationMap.entrySet().iterator();
				while (iter.hasNext()) {
					Entry<Annotation, org.eclipse.jface.text.Position> mapEntry = iter.next();
					annotationModel.addAnnotation(mapEntry.getKey(), mapEntry.getValue());
				}
			}
			fOccurrenceAnnotations = annotationMap.keySet().toArray(new Annotation[annotationMap.size()]);
		}
	}

	/**
	 * Returns the lock object for the given annotation model.
	 *
	 * @param annotationModel
	 *            the annotation model
	 * @return the annotation model's lock object
	 */
	private Object getLockObject(IAnnotationModel annotationModel) {
		if (annotationModel instanceof ISynchronizable) {
			Object lock = ((ISynchronizable) annotationModel).getLockObject();
			if (lock != null)
				return lock;
		}
		return annotationModel;
	}

	void removeOccurrenceAnnotations() {
		IAnnotationModel annotationModel = sourceViewer.getAnnotationModel();
		if (annotationModel == null || fOccurrenceAnnotations == null)
			return;

		synchronized (getLockObject(annotationModel)) {
			if (annotationModel instanceof IAnnotationModelExtension) {
				((IAnnotationModelExtension) annotationModel).replaceAnnotations(fOccurrenceAnnotations, null);
			} else {
				for (Annotation fOccurrenceAnnotation : fOccurrenceAnnotations)
					annotationModel.removeAnnotation(fOccurrenceAnnotation);
			}
			fOccurrenceAnnotations = null;
		}
	}

	private String kindToAnnotationType(DocumentHighlightKind kind) {
		switch (kind) {
		case Read:
			return READ_ANNOTATION_TYPE;
		case Write:
			return WRITE_ANNOTATION_TYPE;
		default:
			return TEXT_ANNOTATION_TYPE;
		}
	}

	@Override
	public void preferenceChange(PreferenceChangeEvent event) {
		if (event.getKey().equals(TOGGLE_HIGHLIGHT_PREFERENCE)) {
			this.enabled = Boolean.valueOf(event.getNewValue().toString());
			if (enabled) {
				initialReconcile();
			} else {
				removeOccurrenceAnnotations();
			}
		}
	}

	@Override
	public void reconcile(DirtyRegion dirtyRegion, IRegion subRegion) {
		// Do nothing
	}

	@Override
	public void reconcile(IRegion partition) {
		// Do nothing
	}

}
