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
import java.util.stream.Collectors;

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
import org.eclipse.lsp4j.ColorInformation;
import org.eclipse.lsp4j.DocumentColorParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.services.LanguageServer;
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


			return LanguageServiceAccessor
				.computeOnServers(document,
						DocumentColorProvider::isColorProvider,
						// Need to do some of the result processing inside the function we supply to computeOnServers(...)
						// as need the LS to construct the ColorInformationMining
						ls -> ls.getTextDocumentService().documentColor(param)
									.thenApply(colors -> LanguageServiceAccessor.streamSafely(colors)
											.map(color -> toMining(color, document, textDocumentIdentifier, ls))))
			 		.thenApply(res -> res.stream().flatMap(s -> s).filter(Objects::nonNull).collect(Collectors.toList()));
		} else {
			return null;
		}
	}

	private ColorInformationMining toMining(ColorInformation color, IDocument document, TextDocumentIdentifier textDocumentIdentifier, LanguageServer server) {
		try {
			return new ColorInformationMining(color, document,
					textDocumentIdentifier, server,
					DocumentColorProvider.this);
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
		}
		return null;
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
		return capabilities != null && capabilities.getColorProvider() != null
				&& ((capabilities.getColorProvider().getLeft() != null && capabilities.getColorProvider().getLeft())
						|| capabilities.getColorProvider().getRight() != null);
	}

}
