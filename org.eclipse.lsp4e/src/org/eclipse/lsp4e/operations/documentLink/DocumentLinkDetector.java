/*******************************************************************************
 * Copyright (c) 2017, 2019 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *  Martin Lippert (Pivotal Inc.) - bug 531452
 *******************************************************************************/
package org.eclipse.lsp4e.operations.documentLink;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.jface.text.hyperlink.AbstractHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.DocumentLink;
import org.eclipse.lsp4j.DocumentLinkParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;

public class DocumentLinkDetector extends AbstractHyperlinkDetector {

	public static class DocumentHyperlink implements IHyperlink {

		private final String uri;
		private final IRegion highlightRegion;

		public DocumentHyperlink(String uri, IRegion highlightRegion) {
			this.uri = uri;
			this.highlightRegion = highlightRegion;
		}

		@Override
		public IRegion getHyperlinkRegion() {
			return this.highlightRegion;
		}

		@Override
		public String getTypeLabel() {
			return uri;
		}

		@Override
		public String getHyperlinkText() {
			return uri;
		}

		@Override
		public void open() {
			LSPEclipseUtils.open(uri, UI.getActivePage(), null);
		}

	}

	@Override
	public IHyperlink[] detectHyperlinks(ITextViewer textViewer, IRegion region, boolean canShowMultipleHyperlinks) {
		final IDocument document = textViewer.getDocument();
		URI uri = LSPEclipseUtils.toUri(document);
		if (uri == null) {
			return null;
		}
		final DocumentLinkParams params = new DocumentLinkParams(new TextDocumentIdentifier(uri.toString()));
		try {
			return LanguageServiceAccessor
					.getLanguageServers(textViewer.getDocument(),
							capabilities -> capabilities.getDocumentLinkProvider() != null)
					.thenApplyAsync(languageServers -> {
						IHyperlink[] res = languageServers.stream()
								.map(languageServer -> languageServer.getTextDocumentService().documentLink(params))
								.map(future -> {
									try {
										return future.get(2, TimeUnit.SECONDS);
									} catch (ExecutionException | TimeoutException e) {
										LanguageServerPlugin.logError(e);
										return null;
									} catch (InterruptedException e) {
										LanguageServerPlugin.logError(e);
										Thread.currentThread().interrupt();
										return null;
									}
								}).filter(Objects::nonNull).flatMap(List<DocumentLink>::stream).map(link -> {
									DocumentHyperlink jfaceLink = null;
									try {
										int start = LSPEclipseUtils.toOffset(link.getRange().getStart(),
												textViewer.getDocument());
										int end = LSPEclipseUtils.toOffset(link.getRange().getEnd(),
												textViewer.getDocument());
										IRegion linkRegion = new Region(start, end - start);
										if (TextUtilities.overlaps(region, linkRegion) && link.getTarget() != null) {
											jfaceLink = new DocumentHyperlink(link.getTarget(), linkRegion);
										}
									} catch (BadLocationException ex) {
										LanguageServerPlugin.logError(ex);
									}
									return jfaceLink;
								}).filter(Objects::nonNull).toArray(IHyperlink[]::new);
						if (res.length == 0) {
							return null;
						} else {
							return res;
						}
					}).get(2, TimeUnit.SECONDS);
		} catch (ExecutionException | TimeoutException e) {
			LanguageServerPlugin.logError(e);
			return null;
		} catch (InterruptedException e) {
			LanguageServerPlugin.logError(e);
			Thread.currentThread().interrupt();
			return null;
		}
	}

}
