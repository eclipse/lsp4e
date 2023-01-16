/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.operations.documentLink;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextPresentation;
import org.eclipse.jface.text.reconciler.DirtyRegion;
import org.eclipse.jface.text.reconciler.IReconcilingStrategy;
import org.eclipse.jface.text.reconciler.IReconcilingStrategyExtension;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4j.DocumentLink;
import org.eclipse.lsp4j.DocumentLinkParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.widgets.Control;

/**
 * Reconciling strategy used to display links coming from LSP
 * 'textDocument/documentLink' with underline style.
 *
 * @author Angelo ZERR
 *
 */
public class LSPDocumentLinkPresentationReconcilingStrategy
		implements IReconcilingStrategy, IReconcilingStrategyExtension {

	/** The target viewer. */
	private ITextViewer viewer;

	private CompletableFuture<Void> request;

	private IDocument document;

	public void install(@Nullable ITextViewer viewer) {
		this.viewer = viewer;
	}

	public void uninstall() {
		this.viewer = null;
		cancel();
	}

	private void underline() {
		if (viewer == null)
			return;

		final IDocument document = viewer.getDocument();
		if (document == null) {
			return;
		}

		URI uri = LSPEclipseUtils.toUri(document);
		if (uri == null) {
			return;
		}
		cancel();
		final var params = new DocumentLinkParams(new TextDocumentIdentifier(uri.toString()));
		request = LanguageServiceAccessor
				.getLanguageServers(document, capabilities -> capabilities.getDocumentLinkProvider() != null)
				.thenAcceptAsync(languageServers -> CompletableFuture.allOf(languageServers.stream()
						.map(languageServer -> languageServer.getTextDocumentService().documentLink(params))
						.map(request -> request.thenAcceptAsync(links -> {
							final Control control = viewer.getTextWidget();
							if (control != null) {
								control.getDisplay().asyncExec(() -> underline(links));
							}
						})).toArray(CompletableFuture[]::new)));
	}

	private void underline(List<DocumentLink> links) {
		if (document == null || links == null) {
			return;
		}
		for (DocumentLink link : links) {
			try {
				// Compute link region
				int start = LSPEclipseUtils.toOffset(link.getRange().getStart(), document);
				int end = LSPEclipseUtils.toOffset(link.getRange().getEnd(), document);
				int length = end - start;
				final var linkRegion = new Region(start, length);

				// Update existing style range with underline or create a new style range with
				// underline
				StyleRange styleRange = null;
				StyleRange[] styleRanges = viewer.getTextWidget().getStyleRanges(start, length);
				if (styleRanges != null && styleRanges.length > 0) {
					// It exists some styles for the range of document link, update just the
					// underline style.
					for (StyleRange s : styleRanges) {
						s.underline = true;
					}
					final var presentation = new TextPresentation(linkRegion, 100);
					presentation.replaceStyleRanges(styleRanges);
					viewer.changeTextPresentation(presentation, false);

				} else {
					// No styles for the range of document link, create a style range with underline
					styleRange = new StyleRange();
					styleRange.underline = true;
					styleRange.start = start;
					styleRange.length = length;

					final var presentation = new TextPresentation(linkRegion, 100);
					presentation.replaceStyleRange(styleRange);
					viewer.changeTextPresentation(presentation, false);
				}

			} catch (BadLocationException e) {
				LanguageServerPlugin.logError(e);
			}
		}
	}

	@Override
	public void initialReconcile() {
		underline();
	}

	/**
	 * Cancel the last call of 'documenLink'.
	 */
	private void cancel() {
		if (request != null) {
			request.cancel(true);
			request = null;
		}
	}

	@Override
	public void setDocument(IDocument document) {
		this.document = document;
	}

	@Override
	public void setProgressMonitor(IProgressMonitor monitor) {
		// Do nothing
	}

	@Override
	public void reconcile(DirtyRegion dirtyRegion, IRegion subRegion) {
		// Do nothing
	}

	@Override
	public void reconcile(IRegion partition) {
		// Underline document by using textDocument/documentLink with some delay as
		// reconcile method is called in a Thread background.
		underline();
	}

}