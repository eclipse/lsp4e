/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Angelo ZERR (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.selectionRange;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.LanguageServers.LanguageServerDocumentExecutor;
import org.eclipse.lsp4e.internal.LSPDocumentAbstractHandler;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SelectionRange;
import org.eclipse.lsp4j.SelectionRangeParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.swt.custom.CaretEvent;
import org.eclipse.swt.custom.CaretListener;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * Abstract class which takes care of 'textDocument/selectionRange' LSP
 * operation.
 *
 * @author Angelo ZERR
 *
 */
public abstract class LSPSelectionRangeAbstractHandler extends LSPDocumentAbstractHandler {

	/**
	 * Selection range handler mapped with a {@link StyledText} of a text editor.
	 *
	 */
	protected static class SelectionRangeHandler {

		public static enum Direction {
			UP, DOWN;
		}

		private static final String KEY = SelectionRangeHandler.class.getName();

		private SelectionRange root;

		private SelectionRange previous;

		private final StyledText styledText;

		private boolean updating;

		public void setRoot(SelectionRange root) {
			this.root = root;
			this.previous = root;
		}

		public static SelectionRangeHandler getSelectionRangeHandler(StyledText styledText) {
			SelectionRangeHandler handler = (SelectionRangeHandler) styledText.getData(KEY);
			if (handler == null) {
				handler = new SelectionRangeHandler(styledText);
			}
			return handler;
		}

		public SelectionRangeHandler(StyledText styledText) {
			this.styledText = styledText;
			styledText.setData(KEY, this);
			styledText.addCaretListener(new CaretListener() {

				@Override
				public void caretMoved(CaretEvent arg0) {
					if (!updating) {
						// The cursor location changed, reset the cached selection range.
						root = null;
					}
				}
			});
		}

		public SelectionRange getSelectionRange(Direction direction) {
			if (direction == Direction.UP) {
				if (previous != null) {
					previous = previous.getParent();
					return previous;
				}
			} else {
				if (previous != null) {
					SelectionRange selectionRange = root;
					while (selectionRange != null) {
						SelectionRange parent = selectionRange.getParent();
						if (previous.equals(parent)) {
							previous = selectionRange;
							return previous;
						}
						selectionRange = parent;
					}
				}
			}
			return null;
		}

		/**
		 * Returns true if the selection range needs to be collected by the language
		 * server 'textDocument/selectionRange' operation and false otherwise.
		 *
		 * @return true if the selection range needs to be collected by the language
		 *         server 'textDocument/selectionRange' operation and false otherwise.
		 */
		public boolean isDirty() {
			return root == null;
		}

		public void updateSelection(ISelectionProvider provider, IDocument document, Direction direction) {
			if (styledText.isDisposed()) {
				return;
			}
			SelectionRange selectionRange = getSelectionRange(direction);
			if (selectionRange != null) {
				ISelection selection = LSPEclipseUtils.toSelection(selectionRange.getRange(), document);
				styledText.getDisplay().execute(() -> {
					try {
						updating = true;
						provider.setSelection(selection);
					} finally {
						updating = false;
					}
				});
			}
		}

	}

	@Override
	protected void execute(ExecutionEvent event, ITextEditor textEditor) {
		final ISelectionProvider provider = textEditor.getSelectionProvider();
		if (provider == null) {
			return;
		}
		ISelection sel = provider.getSelection();
		ITextViewer viewer = textEditor.getAdapter(ITextViewer.class);
		if (viewer == null) {
			return;
		}
		StyledText styledText = viewer.getTextWidget();
		if (styledText == null) {
			return;
		}

		if (sel instanceof ITextSelection textSelection && !textSelection.isEmpty()) {
			IDocument document = LSPEclipseUtils.getDocument(textEditor);
			if (document != null) {
				LanguageServerDocumentExecutor executor = LanguageServers.forDocument(document)
						.withCapability(ServerCapabilities::getSelectionRangeProvider);
				if (executor.anyMatching()) {
					// It exists a language server which supports 'textDocument/SelectionRange' LSP
					// operation.
					SelectionRangeHandler.Direction direction = getDirection();
					// Get the SelectionRangeHandler instance of the editor.
					SelectionRangeHandler handler = SelectionRangeHandler.getSelectionRangeHandler(styledText);
					if (handler.isDirty()) {
						// Collect the selection ranges for cursor location
						collectSelectionRanges(document, textSelection.getOffset()).thenApply(result -> {
							if (result.isPresent()) {
								List<SelectionRange> ranges = result.get();
								SelectionRange root = ranges.get(0);
								// Update handler with the collected selection range from he language server
								handler.setRoot(root);
								// Update Eclipse selection by using the collected LSP SelectionRage
								handler.updateSelection(provider, document, direction);
							}
							return null;
						});
					} else {
						handler.updateSelection(provider, document, direction);
					}
				}
			}
		}
	}

	/**
	 * Returns the selection range hierarchy of the given document at the given
	 * offset by consuming the 'textDocument/SelectionRange' LSP operation of
	 * language servers which are linked to the given document and null otherwise.
	 *
	 * @param document
	 *            the document
	 * @param offset
	 *            the offset.
	 * @return the selection range hierarchy of the given document at the given
	 *         offset.
	 */
	private CompletableFuture<Optional<List<SelectionRange>>> collectSelectionRanges(IDocument document, int offset) {
		if (document == null) {
			return CompletableFuture.completedFuture(null);
		}
		try {
			Position position = LSPEclipseUtils.toPosition(offset, document);
			TextDocumentIdentifier identifier = LSPEclipseUtils.toTextDocumentIdentifier(document);
			List<Position> positions = Collections.singletonList(position);
			SelectionRangeParams params = new SelectionRangeParams(identifier, positions);
			return LanguageServers.forDocument(document).withCapability(ServerCapabilities::getSelectionRangeProvider)
					.computeFirst(languageServer -> languageServer.getTextDocumentService().selectionRange(params))
					.thenApply(ranges -> ranges.stream().filter(Objects::nonNull).findFirst());
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
			return CompletableFuture.completedFuture(null);
		}
	}

	@Override
	public void setEnabled(Object evaluationContext) {
		setEnabled(ServerCapabilities::getSelectionRangeProvider, x -> true);
	}

	/**
	 * Returns the direction used to select the proper selection range.
	 *
	 * @return the direction used to select the proper selection range.
	 */
	protected abstract SelectionRangeHandler.Direction getDirection();
}
