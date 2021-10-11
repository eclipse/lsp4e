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

import java.net.URI;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.runtime.ICoreRunnable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.PreferenceChangeEvent;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentCommand;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IAutoEditStrategy;
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
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4j.LinkedEditingRanges;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.swt.custom.StyledText;

public class LSPLinkedEditingReconcilingStrategy implements IReconcilingStrategy, IReconcilingStrategyExtension, IPreferenceChangeListener, IDocumentListener,  IAutoEditStrategy {
	public static final String LINKED_EDITING_PREFERENCE = "org.eclipse.ui.genericeditor.linkedediting"; //$NON-NLS-1$
	public static final String LINKEDEDITING_ANNOTATION_TYPE = "org.eclipse.lsp4e.linkedediting"; //$NON-NLS-1$

	private boolean enabled;
	private ISourceViewer sourceViewer;
	private IDocument document;
	private EditorSelectionChangedListener editorSelectionChangedListener;
	private CompletableFuture<?> request;
	private Job highlightJob;

	/**
	 * Holds the current linkedEditing Ranges
	 */
	static Map<IDocument, LinkedEditingRanges> fLinkedEditingRanges = new HashMap<>();

	/**
	 * Holds the current linkedEditing annotations.
	 */
	private Annotation[] fLinkedEditingAnnotations = null;


	public LSPLinkedEditingReconcilingStrategy() {
	}

	private void collectLinkedEditingHighlights(int offset, IProgressMonitor monitor) {
		if (sourceViewer == null || document == null || !enabled || monitor.isCanceled()) {
			return;
		}
		cancel();
		Position position;
		try {
			position = LSPEclipseUtils.toPosition(offset, document);
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
			return;
		}
		URI uri = LSPEclipseUtils.toUri(document);
		if(uri == null) {
			return;
		}
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri.toString());
		TextDocumentPositionParams params = new TextDocumentPositionParams(identifier, position);
		request = LanguageServiceAccessor.getLanguageServers(document,
					capabilities -> LSPEclipseUtils.hasCapability(capabilities.getLinkedEditingRangeProvider()))
				.thenAcceptAsync(languageServers ->
				CompletableFuture.allOf(languageServers.stream()
						.map(ls -> ls.getTextDocumentService().linkedEditingRange(LSPEclipseUtils.toLinkedEditingRangeParams(params)))
						.map(request -> request.thenAcceptAsync(result -> {
							if (!monitor.isCanceled()) {
								fLinkedEditingRanges.put(this.document, result);
								updateLinkedEditingAnnotations(result, sourceViewer.getAnnotationModel());
							}
						})).toArray(CompletableFuture[]::new)));
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
			ISelection selection = event.getSelection();
			if (selection instanceof ITextSelection) {
				System.out.println("selectionChanged(): " + ((ITextSelection)selection).getOffset()); //$NON-NLS-1$
			} else {
				System.out.println("selectionChanged(): NO initial selection or not an ITextSelection"); //$NON-NLS-1$
			}
			updateLinkedEditingHighlights(event.getSelection());
		}
	}

	public void install(ITextViewer viewer) {
		if (!(viewer instanceof ISourceViewer)) {
			return;
		}
		IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode(LanguageServerPlugin.PLUGIN_ID);
		preferences.addPreferenceChangeListener(this);
		this.enabled = preferences.getBoolean(LINKED_EDITING_PREFERENCE, true);
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
	public void preferenceChange(PreferenceChangeEvent event) {
		if (event.getKey().equals(LINKED_EDITING_PREFERENCE)) {
			this.enabled = Boolean.valueOf(event.getNewValue().toString());
			if (enabled) {
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
						ISelection selection = selectionProvider.getSelection();
						if (selection instanceof ITextSelection) {
							System.out.println("initialReconcile: textSelection: " + ((ITextSelection)selection).getOffset()); //$NON-NLS-1$
						} else {
							System.out.println("initialReconcile: textSelection:  NO initial selection or not an ITextSelection"); //$NON-NLS-1$
						}
						updateLinkedEditingHighlights(selectionProvider.getSelection());
					}
				});
			}
		}
	}

	@Override
	public void setDocument(IDocument document) {
		if (this.document != null) {
			this.document.removeDocumentListener(this);
		}

		this.document = document;

		if (this.document != null) {
			this.document.addDocumentListener(this);
		}
	}

	@Override
	public void reconcile(DirtyRegion dirtyRegion, IRegion subRegion) {
	}

	@Override
	public void reconcile(IRegion partition) {
	}

	/*
	 * This implementation create a compound command in order to change the only required
	 * amount of characters in linked editing ranges, but in order to make it work correctly
	 * the `org.eclipse.jface.text.DocumentCommand.fillEvent(VerifyEvent, IRegion)` method is
	 * to be fixed in order to make it supporting such a compound command:
	 * ```
	 * diff --git a/org.eclipse.jface.text/src/org/eclipse/jface/text/DocumentCommand.java b/org.eclipse.jface.text/src/org/eclipse/jface/text/DocumentCommand.java
	 * index cc6958e83..522551ef2 100644
	 * --- a/org.eclipse.jface.text/src/org/eclipse/jface/text/DocumentCommand.java
	 * +++ b/org.eclipse.jface.text/src/org/eclipse/jface/text/DocumentCommand.java
	 * @@ -292,7 +292,7 @@ public class DocumentCommand {
	 *         boolean fillEvent(VerifyEvent event, IRegion modelRange) {
	 *                 event.text= text;
	 * -               event.doit= (offset == modelRange.getOffset() && length == modelRange.getLength() && doit && caretOffset == -1);
	 * +               event.doit= (offset == modelRange.getOffset() && length == modelRange.getLength() && getCommandCount() == 1 && doit && caretOffset == -1);
	 *                 return event.doit;
	 *         }
 	 * ```
	 * Otherwise, `org.eclipse.jface.text.TextViewer.handleVerifyEvent(VerifyEvent)` doesn't create a compound change based on the DocumentCommand.
	 *
	@Override
	public void customizeDocumentCommand(IDocument document, DocumentCommand command) {
		synchronized (fLinkedEditingRanges) {
			LinkedEditingRanges ranges = getLinkedEditingRanges(document);
			if (ranges == null) {
				return;
			}

			Range commandRange = null;
			int delta;
			try {
				for (Range r : ranges.getRanges()) {
					int start = LSPEclipseUtils.toOffset(r.getStart(), document);
					int end = LSPEclipseUtils.toOffset(r.getEnd(), document);

					if (start <= command.offset && end > command.offset) {
						commandRange = r;
						delta = command.offset - start;
						break;
					}
				}
			} catch (BadLocationException e) {
				return;
			}

			if (commandRange == null) {
				return;
			}

			final Range rangeAtCursor = commandRange;
			ranges.getRanges().forEach(r -> {
				if (rangeAtCursor != r) {
					try {
						int start = LSPEclipseUtils.toOffset(r.getStart(), document) + delta;
						command.addCommand(start, command.length, command.text, command.owner);
					} catch (BadLocationException e) {
						e.printStackTrace();
					}
				}
			});
		}
	}
	*/

	@Override
	public void customizeDocumentCommand(IDocument document, DocumentCommand command) {
		LinkedEditingRanges ranges = fLinkedEditingRanges.get(document);
		if (ranges == null) {
			return;
		}

		Set<Range> sortedRanges = new TreeSet<>(RANGE_OFFSET_ORDER);
		sortedRanges.addAll(ranges.getRanges());

		int changeStart = Integer.MAX_VALUE;
		int changeEnd = Integer.MIN_VALUE;
		Range commandRange = null;
		int delta = 0;
		try {
			for (Range r : sortedRanges) {
				int start = LSPEclipseUtils.toOffset(r.getStart(), document);
				if (changeStart > start) {
					changeStart = start;
				}
				int end = LSPEclipseUtils.toOffset(r.getEnd(), document);
				if (changeEnd < end) {
					changeEnd = end;
				}

				if (start <= command.offset && end >= command.offset) {
					commandRange = r;
					delta = command.offset - start;
				}
			}
		} catch (BadLocationException e) {
			return;
		}

		if (commandRange == null) {
			return;
		}

		StringBuilder text = new StringBuilder();
		int caretOffset = -1;
		try {
			int currentOffset = changeStart;
			for (Range r : sortedRanges) {
				int rangeStart = LSPEclipseUtils.toOffset(r.getStart(), document);
				int rangeEnd = LSPEclipseUtils.toOffset(r.getEnd(), document);
				if (currentOffset < rangeStart) {
					text.append(document.get(currentOffset, rangeStart - currentOffset));
				}

				int rangeChangeEnd = rangeStart + delta + command.length;
				String rangeTextBeforeCommand = document.get(rangeStart, delta);
				String rangeTextAfterCommand = rangeEnd > rangeChangeEnd ?
						document.get(rangeChangeEnd, rangeEnd - rangeChangeEnd) :
							""; //$NON-NLS-1$

				text.append(rangeTextBeforeCommand).append(command.text);
				if (r == commandRange) {
					caretOffset = text.length();
				}
				text.append(rangeTextAfterCommand);
				currentOffset = rangeEnd > rangeChangeEnd ? rangeEnd : rangeChangeEnd;
			}
		} catch (BadLocationException e) {
			return;
		}

		command.offset = changeStart;
		command.length = changeEnd - changeStart;
		command.text = text.toString();
		command.caretOffset = changeStart + caretOffset;
		command.shiftsCaret = false;
	}

	@Override
	public void documentAboutToBeChanged(DocumentEvent event) {
	}

	@Override
	public void documentChanged(DocumentEvent event) {
		updateLinkedEditingHighlights(event.getOffset());
	}

	/**
	 * Cancel the last call of 'linkedEditing'.
	 */
	private void cancel() {
		if (request != null && !request.isDone()) {
			request.cancel(true);
			request = null;
		}
	}

	private void updateLinkedEditingHighlights(int offset) {
		if (highlightJob != null) {
			highlightJob.cancel();
		}
		highlightJob = Job.createSystem("LSP4E Linked Editing Highlight", //$NON-NLS-1$
				(ICoreRunnable)(monitor -> collectLinkedEditingHighlights(offset, monitor)));
		highlightJob.schedule();
	}

	private void updateLinkedEditingHighlights(ISelection selection) {
		if (selection instanceof ITextSelection) {
			updateLinkedEditingHighlights(((ITextSelection) selection).getOffset());
		}
	}

	/**
	 * Update the UI annotations with the given list of LinkedEditing.
	 *
	 * @param highlights
	 *            list of LinkedEditing
	 * @param annotationModel
	 *            annotation model to update.
	 */
	private void updateLinkedEditingAnnotations(LinkedEditingRanges linkedEditingRanges, IAnnotationModel annotationModel) {
		Map<Annotation, org.eclipse.jface.text.Position> annotationMap = new HashMap<>(linkedEditingRanges.getRanges().size());
		for (Range r : linkedEditingRanges.getRanges()) {
			try {
				int start = LSPEclipseUtils.toOffset(r.getStart(), document);
				int end = LSPEclipseUtils.toOffset(r.getEnd(), document);
				annotationMap.put(new Annotation(LINKEDEDITING_ANNOTATION_TYPE, false, null),
						new org.eclipse.jface.text.Position(start, end - start));
			} catch (BadLocationException e) {
				LanguageServerPlugin.logError(e);
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
			fLinkedEditingAnnotations = annotationMap.keySet().toArray(new Annotation[annotationMap.keySet().size()]);
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
		fLinkedEditingRanges.put(this.document, null);
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

    /**
     * A Comparator that orders {@code Region} objects by offset
     */
    private static final Comparator<Range> RANGE_OFFSET_ORDER
                                         = new RangeOffsetComparator();
    private static class RangeOffsetComparator
            implements Comparator<Range> {

    	@Override
		public int compare(Range r1, Range r2) {
            Position p1 = r1.getStart();
            Position p2 = r2.getStart();

            if (p1.getLine() == p2.getLine()) {
            	return p1.getCharacter() - p2.getCharacter();
            }

            return p1.getLine() - p2.getLine();
        }
    }
}