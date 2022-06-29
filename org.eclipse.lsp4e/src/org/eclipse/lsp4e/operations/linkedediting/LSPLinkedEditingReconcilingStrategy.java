/*******************************************************************************
 * Copyright (c) 2021, 2022 Red Hat Inc. and others.
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.PreferenceChangeEvent;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.link.ILinkedModeListener;
import org.eclipse.jface.text.link.LinkedModeModel;
import org.eclipse.jface.text.link.LinkedModeUI;
import org.eclipse.jface.text.link.LinkedModeUI.ExitFlags;
import org.eclipse.jface.text.link.LinkedPosition;
import org.eclipse.jface.text.link.LinkedPositionGroup;
import org.eclipse.jface.text.reconciler.DirtyRegion;
import org.eclipse.jface.text.reconciler.IReconcilingStrategy;
import org.eclipse.jface.text.reconciler.IReconcilingStrategyExtension;
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
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.ui.progress.UIJob;
import org.eclipse.ui.texteditor.link.EditorLinkedModeUI;

public class LSPLinkedEditingReconcilingStrategy extends LSPLinkedEditingBase implements IReconcilingStrategy, IReconcilingStrategyExtension, IDocumentListener {
	private ISourceViewer sourceViewer;
	private IDocument fDocument;
	private EditorSelectionChangedListener editorSelectionChangedListener;
	private Job highlightJob;
	private LinkedModeModel linkedModel;

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
			updateLinkedEditing(event.getSelection());
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
				//linkedModel.exit(ILinkedModeListener.EXIT_ALL);
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
						updateLinkedEditing(selectionProvider.getSelection());
					}
				});
			}
		}
	}

	@Override
	public void setDocument(IDocument document) {
		if (this.fDocument != null) {
			this.fDocument.removeDocumentListener(this);
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
		updateLinkedEditing(event.getOffset());
	}

	private void updateLinkedEditing(ISelection selection) {
		if (selection instanceof ITextSelection) {
			updateLinkedEditing(((ITextSelection) selection).getOffset());
		}
	}

	private void updateLinkedEditing(int offset) {
		if (sourceViewer != null  && fDocument != null  && fEnabled && linkedModel == null || !linkedModel.anyPositionContains(offset)) {
			collectLinkedEditingRanges(fDocument, offset)
				.thenAcceptAsync(r -> {
					if (rangesContainOffset(r, offset)) {
						applyLinkedEdit(r);
					}
				});
		}
	}

	private void applyLinkedEdit(LinkedEditingRanges ranges) {
		if (highlightJob != null) {
			highlightJob.cancel();
		}
		if (ranges == null) {
			return;
		}
		highlightJob = new UIJob("LSP4E Linked Editing") { //$NON-NLS-1$
			@Override
			public IStatus runInUIThread(IProgressMonitor monitor) {
				linkedModel = new LinkedModeModel();
				try {
					linkedModel.addGroup(toJFaceGroup(ranges));
					linkedModel.forceInstall();
					ITextSelection selectionBefore = (ITextSelection)sourceViewer.getSelectionProvider().getSelection();
					LinkedModeUI linkedMode = new EditorLinkedModeUI(linkedModel, sourceViewer);
					linkedMode.setExitPolicy((model, event, offset, length) -> {
						if (event.character == 0 || event.character == '\b') {
							return null;
						}

						if (ranges != null && ranges.getWordPattern()  != null) {
							String valuee = getValueInRange(ranges, event, offset, length);
							if (valuee != null) {
								Pattern pattern = Pattern.compile(ranges.getWordPattern());
								Matcher matcher = pattern.matcher(valuee);
								if (matcher.matches()) {
									return null;
								}
							}
						} else {
							if (Character.isUnicodeIdentifierPart(event.character) || event.character == '_') {
								return null;
							}
						}
						return new ExitFlags(ILinkedModeListener.EXIT_ALL, true);
					});
					linkedMode.enter();
					sourceViewer.getSelectionProvider().setSelection(selectionBefore);
					return Status.OK_STATUS;
				} catch (BadLocationException ex) {
					return new Status(IStatus.ERROR, LanguageServerPlugin.PLUGIN_ID, ex.getMessage(), ex);
				}
			}
		};
		highlightJob.schedule();
	}

	String getValueInRange(LinkedEditingRanges ranges, VerifyEvent event, int offset, int length) {
		try {
			for (Range range : ranges.getRanges()) {
				if (LSPEclipseUtils.isOffsetInRange(offset, range, fDocument)) {
					int start = LSPEclipseUtils.toOffset(range.getStart(), fDocument);
					int end = LSPEclipseUtils.toOffset(range.getEnd(), fDocument);
					StringBuilder sb = new StringBuilder();
					sb.append(fDocument.get(start, end - start)); 	// The range text before the insertion
					String newChars = event.character == 0 ? "" : Character.toString(event.character); //$NON-NLS-1$
					sb.replace(offset - start, offset - start  + length, newChars);
					return sb.toString();
				}
			}
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
		}
		return null;
	}

	private boolean rangesContainOffset(LinkedEditingRanges ranges, int offset) {
		for (Range range : ranges.getRanges()) {
			if (LSPEclipseUtils.isOffsetInRange(offset, range, fDocument)) {
				return true;
			}
		}
		return false;
	}

	private LinkedPositionGroup toJFaceGroup(LinkedEditingRanges ranges) throws BadLocationException {
		LinkedPositionGroup res = new LinkedPositionGroup();
		for (Range range : ranges.getRanges()) {
			int startOffset = LSPEclipseUtils.toOffset(range.getStart(), fDocument);
			int length = LSPEclipseUtils.toOffset(range.getEnd(), fDocument) - startOffset;
			res.addPosition(new LinkedPosition(fDocument, startOffset, length));
		}
		return res;
	}
}