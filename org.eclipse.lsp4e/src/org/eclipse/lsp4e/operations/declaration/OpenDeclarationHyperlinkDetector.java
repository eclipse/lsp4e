/*******************************************************************************
 * Copyright (c) 2016, 2019 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Michał Niewrzał (Rogue Wave Software Inc.) - hyperlink range detection
 *  Lucas Bullen (Red Hat Inc.) - [Bug 517428] Requests sent before initialization
 *******************************************************************************/
package org.eclipse.lsp4e.operations.declaration;

import java.net.URI;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.hyperlink.AbstractHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

public class OpenDeclarationHyperlinkDetector extends AbstractHyperlinkDetector {

	public static class LSBasedHyperlink implements IHyperlink {

		private Location location;
		private IRegion highlightRegion;

		public LSBasedHyperlink(Location response, IRegion highlightRegion) {
			this.location = response;
			this.highlightRegion = highlightRegion;
		}

		@Override
		public IRegion getHyperlinkRegion() {
			return this.highlightRegion;
		}

		@Override
		public String getTypeLabel() {
			return Messages.hyperlinkLabel;
		}

		@Override
		public String getHyperlinkText() {
			return Messages.hyperlinkLabel;
		}

		@Override
		public void open() {
			IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
			LSPEclipseUtils.openInEditor(location, page);
		}

	}

	@Override
	public IHyperlink[] detectHyperlinks(ITextViewer textViewer, IRegion region, boolean canShowMultipleHyperlinks) {
		final IDocument document = textViewer.getDocument();
		TextDocumentPositionParams params;
		try {
			URI uri = LSPEclipseUtils.toUri(document);
			if (uri == null) {
				return null;
			}
			params = new TextDocumentPositionParams(
					new TextDocumentIdentifier(uri.toString()),
					LSPEclipseUtils.toPosition(region.getOffset(), document));
		} catch (BadLocationException e1) {
			LanguageServerPlugin.logError(e1);
			return null;
		}
		IRegion r = findWord(textViewer.getDocument(), region.getOffset());
		final IRegion linkRegion = r != null ? r : region;
		try {
			return LanguageServiceAccessor
					.getLanguageServers(textViewer.getDocument(),
							capabilities -> Boolean.TRUE.equals(capabilities.getDefinitionProvider()))
					.thenApplyAsync(languageServers -> {
						IHyperlink[] res = languageServers.stream()
								.map(languageServer -> languageServer.getTextDocumentService().definition(params))
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
								}).filter(Objects::nonNull) //
								.flatMap(locations -> locations.stream()) //
								.map(location -> new LSBasedHyperlink(location, linkRegion)) //
								.filter(Objects::nonNull) //
								.toArray(IHyperlink[]::new);
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

	/**
	 * This method is only a workaround for missing range value (which can be used
	 * to highlight hyperlink) in LSP 'definition' response.
	 *
	 * Should be removed when protocol will be updated
	 * (https://github.com/Microsoft/language-server-protocol/issues/3)
	 *
	 * @param document
	 * @param offset
	 * @return
	 */
	private IRegion findWord(IDocument document, int offset) {
		int start = -2;
		int end = -1;

		try {

			int pos = offset;
			char c;

			while (pos >= 0) {
				c = document.getChar(pos);
				if (!Character.isUnicodeIdentifierPart(c)) {
					break;
				}
				--pos;
			}

			start = pos;

			pos = offset;
			int length = document.getLength();

			while (pos < length) {
				c = document.getChar(pos);
				if (!Character.isUnicodeIdentifierPart(c))
					break;
				++pos;
			}

			end = pos;

		} catch (BadLocationException x) {
			LanguageServerPlugin.logWarning(x.getMessage(), x);
		}

		if (start >= -1 && end > -1) {
			if (start == offset && end == offset)
				return new Region(offset, 0);
			else if (start == offset)
				return new Region(start, end - start);
			else
				return new Region(start + 1, end - start - 1);
		}

		return null;
	}

}
