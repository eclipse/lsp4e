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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.codemining.AbstractCodeMiningProvider;
import org.eclipse.jface.text.codemining.ICodeMining;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4j.ColorInformation;
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

	private @Nullable CompletableFuture<List<? extends ICodeMining>> provideCodeMinings(IDocument document) {
		URI docURI = LSPEclipseUtils.toUri(document);

		if (docURI != null) {
			final var textDocumentIdentifier = LSPEclipseUtils.toTextDocumentIdentifier(docURI);
			final var param = new DocumentColorParams(textDocumentIdentifier);
			return LanguageServers.forDocument(document)
				.withFilter(DocumentColorProvider::isColorProvider)
				.collectAll(
					// Need to do some of the result processing inside the function we supply to collectAll(...)
					// as need the LSW to construct the ColorInformationMining
					(wrapper, ls) -> ls.getTextDocumentService().documentColor(param)
								.thenApply(colors -> LanguageServers.streamSafely(colors)
										.map(color -> toMining(color, document, textDocumentIdentifier, wrapper))))
				.thenApply(res -> res.stream().flatMap(Function.identity()).filter(Objects::nonNull).toList());
		} else {
			return null;
		}
	}

	private @Nullable ColorInformationMining toMining(ColorInformation color, IDocument document, TextDocumentIdentifier textDocumentIdentifier, LanguageServerWrapper wrapper) {
		try {
			return new ColorInformationMining(color, document,
					textDocumentIdentifier, wrapper,
					DocumentColorProvider.this);
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
		}
		return null;
	}

	@Override
	public @Nullable CompletableFuture<List<? extends ICodeMining>> provideCodeMinings(ITextViewer viewer,
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

	private static boolean isColorProvider(final @Nullable ServerCapabilities capabilities) {
		return capabilities != null && LSPEclipseUtils.hasCapability(capabilities.getColorProvider());
	}

}
