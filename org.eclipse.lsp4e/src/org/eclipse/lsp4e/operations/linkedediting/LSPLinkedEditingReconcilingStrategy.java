/*******************************************************************************
 * Copyright (c) 2021 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Victor Rubezhny (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.linkedediting;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.runtime.ICoreRunnable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.PreferenceChangeEvent;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
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
import org.eclipse.lsp4j.LinkedEditingRanges;
import org.eclipse.lsp4j.Range;
import org.eclipse.swt.custom.StyledText;

public class LSPLinkedEditingReconcilingStrategy extends LSPLinkedEditingBase implements IReconcilingStrategy, IReconcilingStrategyExtension, IDocumentListener {
	public static final String LINKEDEDITING_ANNOTATION_TYPE = "org.eclipse.lsp4e.linkedediting"; //$NON-NLS-1$

	private ISourceViewer sourceViewer;
	private IDocument fDocument;
	private EditorSelectionChangedListener editorSelectionChangedListener;
	private Job highlightJob;

	/**
	 * Holds the current linkedEditing annotations.
	 */
	private Annotation[] fLinkedEditingAnnotations = null;

	public LSPLinkedEditingReconcilingStrategy() {
	}

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
			updateLinkedEditingHighlights(event.getSelection());
		}
	}

	public void install(ITextViewer viewer) {
		if (!(viewer instanceof ISourceViewer)) {
			return;
		}

		super.install();
		this.sourceViewer = (ISourceViewer) viewer;
		editorSelectionChangedListener = new EditorSelectionChangedListener();
		editorSelectionChangedListener.install(sourceViewer.getSelectionProvider());
	}

	@Override
	public void uninstall() {
		if (sourceViewer != null) {
			editorSelectionChangedListener.uninstall(sourceViewer.getSelectionProvider());
		}
		super.uninstall();
	}

	@Override
	public void preferenceChange(PreferenceChangeEvent event) {
		super.preferenceChange(event);
		if (event.getKey().equals(LINKED_EDITING_PREFERENCE)) {
			if (fEnabled) {
				initialReconcile();
			} else {
				removeLinkedEditingAnnotations();
			}
		}
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
						updateLinkedEditingHighlights(selectionProvider.getSelection());
					}
				});
			}
		}
	}

	@Override
	public void setDocument(IDocument document) {
		if (this.fDocument != null) {
			this.fDocument.removeDocumentListener(this);
			fLinkedEditingRanges = null;
		}

		this.fDocument = document;

		if (this.fDocument != null) {
			this.fDocument.addDocumentListener(this);
		}
	}

	@Override
	public void reconcile(DirtyRegion dirtyRegion, IRegion subRegion) {
	}

	@Override
	public void reconcile(IRegion partition) {
	}

	@Override
	public void documentAboutToBeChanged(DocumentEvent event) {
	}

	@Override
	public void documentChanged(DocumentEvent event) {
		updateLinkedEditingHighlights(event.getOffset());
	}

	private void updateLinkedEditingHighlights(ISelection selection) {
		if (selection instanceof ITextSelection) {
			updateLinkedEditingHighlights(((ITextSelection) selection).getOffset());
		}
	}

	private void updateLinkedEditingHighlights(int offset) {
		if (sourceViewer != null  && fDocument != null  && fEnabled) {
			collectLinkedEditingRanges(fDocument, offset)
				.thenAcceptAsync(theVoid -> updateLinkedEditingHighlights());
		}
	}

	private void updateLinkedEditingHighlights() {
		if (highlightJob != null) {
			highlightJob.cancel();
		}
		highlightJob = Job.createSystem("LSP4E Linked Editing Highlight", //$NON-NLS-1$
				(ICoreRunnable)(monitor -> {
					updateLinkedEditingAnnotations(
							sourceViewer.getAnnotationModel(), monitor);
					}));
		highlightJob.schedule();
	}

	/**
	 * Update the UI annotations with the given list of LinkedEditing.
	 *
	 * @param annotationModel
	 *            annotation model to update.
	 * @param monitor
	 * 			  a progress monitor
	 */
	private void updateLinkedEditingAnnotations(IAnnotationModel annotationModel, IProgressMonitor monitor) {
		if (monitor.isCanceled() || annotationModel == null) {
			return;
		}

		LinkedEditingRanges ranges = fLinkedEditingRanges;
		Map<Annotation, org.eclipse.jface.text.Position> annotationMap = new HashMap<>(ranges == null ? 0 : ranges.getRanges().size());
		if (ranges != null) {
			for (Range r : ranges.getRanges()) {
				try {
					int start = LSPEclipseUtils.toOffset(r.getStart(), fDocument);
					int end = LSPEclipseUtils.toOffset(r.getEnd(), fDocument);
					annotationMap.put(new Annotation(LINKEDEDITING_ANNOTATION_TYPE, false, null),
							new org.eclipse.jface.text.Position(start, end - start));
				} catch (BadLocationException e) {
					LanguageServerPlugin.logError(e);
				}
			}
		}

		synchronized (getLockObject(annotationModel)) {
			if (annotationModel instanceof IAnnotationModelExtension) {
				((IAnnotationModelExtension) annotationModel).replaceAnnotations(fLinkedEditingAnnotations, annotationMap);
			} else {
				removeLinkedEditingAnnotations();
				Iterator<Entry<Annotation, org.eclipse.jface.text.Position>> iter = annotationMap.entrySet().iterator();
				while (iter.hasNext()) {
					Entry<Annotation, org.eclipse.jface.text.Position> mapEntry = iter.next();
					annotationModel.addAnnotation(mapEntry.getKey(), mapEntry.getValue());
				}
			}
			fLinkedEditingAnnotations = annotationMap.keySet().toArray(new Annotation[annotationMap.size()]);
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

	void removeLinkedEditingAnnotations() {
		IAnnotationModel annotationModel = sourceViewer.getAnnotationModel();
		if (annotationModel == null || fLinkedEditingAnnotations == null)
			return;

		synchronized (getLockObject(annotationModel)) {
			if (annotationModel instanceof IAnnotationModelExtension) {
				((IAnnotationModelExtension) annotationModel).replaceAnnotations(fLinkedEditingAnnotations, null);
			} else {
				for (Annotation fOccurrenceAnnotation : fLinkedEditingAnnotations)
					annotationModel.removeAnnotation(fOccurrenceAnnotation);
			}
			fLinkedEditingAnnotations = null;
		}
	}
}