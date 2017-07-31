/*******************************************************************************
 * Copyright (c) 2017 Rogue Wave Software Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.highlight;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.reconciler.AbstractReconciler;
import org.eclipse.jface.text.reconciler.DirtyRegion;
import org.eclipse.jface.text.reconciler.IReconcilingStrategy;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.swt.custom.CaretEvent;
import org.eclipse.swt.custom.CaretListener;

public class HighlightReconciler extends AbstractReconciler implements CaretListener {

	public static final String READ_ANNOTATION_TYPE = "org.eclipse.lsp4e.read"; //$NON-NLS-1$
	public static final String WRITE_ANNOTATION_TYPE = "org.eclipse.lsp4e.write"; //$NON-NLS-1$
	public static final String TEXT_ANNOTATION_TYPE = "org.eclipse.lsp4e.text"; //$NON-NLS-1$

	private HighlightsFinder finder;
	private IAnnotationModel annotationModel;
	private ISourceViewer sourceViewer;
	private IDocument document;

	@Override
	public void install(ITextViewer viewer) {
		super.install(viewer);
		if (!(viewer instanceof ISourceViewer)) {
			return;
		}

		this.sourceViewer = (ISourceViewer) viewer;
	}

	@Override
	public void uninstall() {
		super.uninstall();
		if (sourceViewer != null) {
			sourceViewer.getTextWidget().removeCaretListener(this);
		}
	}

	@Override
	protected void reconcilerDocumentChanged(IDocument newDocument) {
		if (this.sourceViewer == null || newDocument == null) {
			return;
		}
		this.document = newDocument;
		List<LSPDocumentInfo> infos = LanguageServiceAccessor.getLSPDocumentInfosFor(newDocument,
				capabilities -> Boolean.TRUE.equals(capabilities.getDocumentHighlightProvider()));
		if (infos.isEmpty()) {
			sourceViewer = null;
			return;
		}

		this.annotationModel = sourceViewer.getAnnotationModel();
		this.finder = new HighlightsFinder(infos);
		this.sourceViewer.getTextWidget().addCaretListener(this);
	}

	@Override
	public void caretMoved(CaretEvent event) {
		collectHighlights(event.caretOffset);
	}

	public void collectHighlights(int caretOffset) {
		if (finder == null) {
			return;
		}
		finder.doCancel();
		try {
			Position position = LSPEclipseUtils.toPosition(caretOffset, document);
			finder.setPosition(position);
			finder.addJobChangeListener(new JobChangeAdapter() {
				@Override
				public void done(IJobChangeEvent event) {
					if (event.getResult() == Status.OK_STATUS) {
						updateAnnotations(finder.getHighlights());
					}
				}
			});
			finder.setSystem(true);
			finder.setUser(false);
			finder.schedule(100);
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
		}
	}

	private void updateAnnotations(List<? extends DocumentHighlight> highlights) {
		// TODO optimize with IAnnotationModelExtension
		Map<org.eclipse.jface.text.Position, Annotation> annotations = new HashMap<>();
		for (DocumentHighlight h : highlights) {
			if (h != null) {
				try {
					int start = LSPEclipseUtils.toOffset(h.getRange().getStart(), document);
					int end = LSPEclipseUtils.toOffset(h.getRange().getEnd(), document);
					annotations.put(new org.eclipse.jface.text.Position(start, end - start),
							new Annotation(kindToAnnotationType(h.getKind()), false, null));
				} catch (BadLocationException e) {
					LanguageServerPlugin.logError(e);
				}
			}
		}
		Iterator<Annotation> iterator = annotationModel.getAnnotationIterator();
		while (iterator.hasNext()) {
			Annotation annotation = iterator.next();
			org.eclipse.jface.text.Position position = annotationModel.getPosition(annotation);

			Annotation newAnnotation = annotations.get(position);
			if (newAnnotation == null || !newAnnotation.getType().equals(annotation.getType())) {
				annotationModel.removeAnnotation(annotation);
			} else {
				annotations.remove(position);
			}
		}

		for (org.eclipse.jface.text.Position position : annotations.keySet()) {
			Annotation annotation = annotations.get(position);
			annotationModel.addAnnotation(annotation, position);
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
	public IReconcilingStrategy getReconcilingStrategy(String contentType) {
		return null;
	}

	@Override
	protected void process(DirtyRegion dirtyRegion) {
	}

}
