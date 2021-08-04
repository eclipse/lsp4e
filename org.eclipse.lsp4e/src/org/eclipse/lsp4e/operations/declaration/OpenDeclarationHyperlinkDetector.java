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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

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
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.TypeDefinitionRegistrationOptions;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class OpenDeclarationHyperlinkDetector extends AbstractHyperlinkDetector {

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
		Map<Either<Location, LocationLink>,LSBasedHyperlink> allLinks = Collections.synchronizedMap(new LinkedHashMap<>());
		try {
			// Collect definitions
			Collection<CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>>> allFutures = Collections.synchronizedCollection(new ArrayList<>());
			CompletableFuture.allOf(
				LanguageServiceAccessor
					.getLanguageServers(textViewer.getDocument(), capabilities -> LSPEclipseUtils.hasCapability(capabilities.getDefinitionProvider()))
					.thenAcceptAsync(languageServers ->
						languageServers.stream().map(ls -> ls.getTextDocumentService().definition(LSPEclipseUtils.toDefinitionParams(params))).forEach(allFutures::add)
					),
				LanguageServiceAccessor
					.getLanguageServers(textViewer.getDocument(), OpenDeclarationHyperlinkDetector::isTypeDefinitionProvider)
					.thenAcceptAsync(languageServers ->
						languageServers.stream().map(ls -> ls.getTextDocumentService().typeDefinition(LSPEclipseUtils.toTypeDefinitionParams(params))).forEach(allFutures::add)
					)
			).thenCompose(theVoid ->
				CompletableFuture.allOf(allFutures.stream().map(future ->
				future.thenAccept(locations -> {
					Collection<LSBasedHyperlink> links = toHyperlinks(document, linkRegion, locations);
					synchronized (allLinks) {
						links.forEach(link -> {
							allLinks.putIfAbsent(link.getLocation(), link);
						});
					}
				})).toArray(CompletableFuture[]::new))
			).get(500, TimeUnit.MILLISECONDS);
		} catch (ExecutionException | TimeoutException e) {
			LanguageServerPlugin.logError(e);
		} catch (InterruptedException e) {
			LanguageServerPlugin.logError(e);
			Thread.currentThread().interrupt();
		}
		if (allLinks.isEmpty()) {
			return null;
		}
		return allLinks.values().toArray(new IHyperlink[allLinks.size()]);
	}

	/**
	 * Fill the given Eclipse links by using the given LSP locations
	 *
	 * @param document
	 *            the document
	 * @param linkRegion
	 *            the link region
	 * @param locations
	 *            the LSP locations
	 * @param allLinks
	 *            the Eclipse links to update
	 */
	private static Collection<LSBasedHyperlink> toHyperlinks(final IDocument document, final IRegion linkRegion,
			Either<List<? extends Location>, List<? extends LocationLink>> locations) {
		if (locations == null) {
			return Collections.emptyList();
		}
		if (locations.isLeft()) {
			return locations.getLeft().stream()
					.filter(Objects::nonNull)
					.map(location -> new LSBasedHyperlink(location, linkRegion))
					.collect(Collectors.toList());
		} else if (locations.isRight()) {
			return locations.getRight().stream().filter(Objects::nonNull).map(locationLink -> {
						IRegion selectionRegion = linkRegion;
						Range originSelectionRange = locationLink.getOriginSelectionRange();
						if (originSelectionRange != null) {
							try {
								int offset = LSPEclipseUtils
										.toOffset(originSelectionRange.getStart(), document);
								int endOffset = LSPEclipseUtils
										.toOffset(originSelectionRange.getEnd(), document);
								selectionRegion = new Region(offset, endOffset - offset);
							} catch (BadLocationException e) {
								LanguageServerPlugin.logError(e.getMessage(), e);
							}
						}
						return new LSBasedHyperlink(locationLink, selectionRegion);
					}).collect(Collectors.toList());
		}
		return Collections.emptyList();
	}

	private static boolean isTypeDefinitionProvider(ServerCapabilities capabilities) {
		 Either<Boolean, TypeDefinitionRegistrationOptions> typeDefinitionProvider = capabilities.getTypeDefinitionProvider();
		 if (typeDefinitionProvider == null) {
			 return false;
		 }
		 if (typeDefinitionProvider.isLeft()) {
			return Boolean.TRUE.equals(typeDefinitionProvider.getLeft());
		 } else if (typeDefinitionProvider.isRight()) {
			return true;
		}
		return false;
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

			while (pos >= 0 && pos < document.getLength()) {
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
