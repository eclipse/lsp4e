/*******************************************************************************
 * Copyright (c) 2018, 2021 Angelo Zerr and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Angelo Zerr <angelo.zerr@gmail.com> - [code mining] Support 'textDocument/documentColor' with CodeMining - Bug 533322
 */
package org.eclipse.lsp4e.operations.color;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.codemining.AbstractCodeMiningProvider;
import org.eclipse.jface.text.codemining.ICodeMining;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4j.DocumentColorParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGBA;
import org.eclipse.swt.widgets.Display;

/**
 * Consume the 'textDocument/documentColor' request to decorate color references
 * in the editor.
 *
 */
public class DocumentColorProvider extends AbstractCodeMiningProvider {

	private final Map<RGBA, Color> colorTable;

	public DocumentColorProvider() {
		colorTable = new HashMap<>();
	}

	private CompletableFuture<List<? extends ICodeMining>> provideCodeMinings(@NonNull IDocument document) {
		URI docURI = LSPEclipseUtils.toUri(document);

		if (docURI != null) {
			TextDocumentIdentifier textDocumentIdentifier = new TextDocumentIdentifier(docURI.toString());
			DocumentColorParams param = new DocumentColorParams(textDocumentIdentifier);
			final List<ColorInformationMining> colorResults = Collections.synchronizedList(new ArrayList<>());
			return LanguageServiceAccessor.getLanguageServers(document, DocumentColorProvider::isColorProvider)
					.thenComposeAsync(languageServers -> CompletableFuture
							.allOf(languageServers.stream().map(languageServer -> languageServer
									.getTextDocumentService().documentColor(param).thenAcceptAsync(colors -> {
										if (colors != null) {
											colors.stream().filter(Objects::nonNull).map(color -> {
												ColorInformationMining mining = null;
												try {
													mining = new ColorInformationMining(color, document,
															textDocumentIdentifier, languageServer,
															DocumentColorProvider.this);
												} catch (BadLocationException e) {
													LanguageServerPlugin.logError(e);
												}
												return mining;
											}).filter(Objects::nonNull).forEach(colorResults::add);
										}
									})).toArray(CompletableFuture[]::new)))
					.thenApplyAsync(theVoid -> colorResults);
		} else {
			return null;
		}
	}

	@Override
	public CompletableFuture<List<? extends ICodeMining>> provideCodeMinings(ITextViewer viewer,
			IProgressMonitor monitor) {
		IDocument document = viewer.getDocument();
		return document != null ? provideCodeMinings(document) : null;
	}

	/**
	 * Returns the color from the given rgba.
	 *
	 * @param rgba
	 *            the rgba declaration
	 * @param display
	 *            the display to use to create a color instance
	 * @return the color from the given rgba.
	 */
	public Color getColor(RGBA rgba, Display display) {
		return colorTable.computeIfAbsent(rgba, key -> new Color(display, rgba));
	}

	private static boolean isColorProvider(ServerCapabilities capabilities) {
		return capabilities.getColorProvider() != null
				&& ((capabilities.getColorProvider().getLeft() != null && capabilities.getColorProvider().getLeft())
						|| capabilities.getColorProvider().getRight() != null);
	}

}
