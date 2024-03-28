/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.operations.inlinevalue;

import java.net.URI;
import java.util.List;
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
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.LanguageServers.LanguageServerDocumentExecutor;
import org.eclipse.lsp4j.InlineValue;
import org.eclipse.lsp4j.InlineValueContext;
import org.eclipse.lsp4j.InlineValueParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

public class InlineValueProvider extends AbstractCodeMiningProvider {

	private CompletableFuture<List<? extends ICodeMining>> provideCodeMinings(@NonNull IDocument document, Range range,
			InlineValueContext context) {
		URI docURI = LSPEclipseUtils.toUri(document);
		if (docURI != null && range != null) {
			final var param = new InlineValueParams(LSPEclipseUtils.toTextDocumentIdentifier(docURI), range, context);
			LanguageServerDocumentExecutor executor = LanguageServers.forDocument(document)
					.withFilter(sc -> sc.getInlineValueProvider() != null);
			return executor
					.collectAll((ls) -> ls.getTextDocumentService().inlineValue(param)
							.thenApply(inlineValues -> LanguageServers.streamSafely(inlineValues)
									.map(inlineValue -> toCodeMining(document, inlineValue)).filter(Objects::nonNull)))
					.thenApply(result -> result.stream().flatMap(s -> s).toList());
		} else {
			return null;
		}
	}

	private InlineValueTextCodeMining toCodeMining(IDocument document, InlineValue inlineValue) {
		if (inlineValue == null || !inlineValue.isInlineValueText()) {
			return null;
		}
		try {
			return new InlineValueTextCodeMining(inlineValue.getInlineValueText(), document, InlineValueProvider.this);
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
			return null;
		}
	}

	@Override
	public CompletableFuture<List<? extends ICodeMining>> provideCodeMinings(ITextViewer viewer,
			IProgressMonitor monitor) {
		IDocument document = viewer.getDocument();
		if (document == null) {
			return null;
		}
		Range range;
		try {
			range = new Range(new Position(0, 0),
					new Position(document.getNumberOfLines(), document.getLineLength(document.getNumberOfLines() - 1)));
			return provideCodeMinings(document, range, new InlineValueContext());
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
			return null;
		}

	}

}
