/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.operations.codelens;

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
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.LanguageServers.LanguageServerDocumentExecutor;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;

public class CodeLensProvider extends AbstractCodeMiningProvider {

	private CompletableFuture<List<? extends ICodeMining>> provideCodeMinings(@NonNull IDocument document) {
		URI docURI = LSPEclipseUtils.toUri(document);
		if (docURI != null) {
			final var param = new CodeLensParams(LSPEclipseUtils.toTextDocumentIdentifier(docURI));
			LanguageServerDocumentExecutor executor = LanguageServers.forDocument(document)
					.withFilter(sc -> sc.getCodeLensProvider() != null);
			return executor.collectAll((w, ls) -> ls.getTextDocumentService().codeLens(param)
								.thenApply(codeLenses -> LanguageServers.streamSafely(codeLenses)
										.map(codeLens -> toCodeMining(document, w, codeLens))
										.filter(Objects::nonNull)))
				.thenApply(result -> result.stream().flatMap(s -> s).toList());
		}
		else {
			return null;
		}
	}

	private CodeLensCodeMining toCodeMining(IDocument document, LanguageServerWrapper languageServerWrapper, CodeLens codeLens) {
		if (codeLens == null) {
			return null;
		}
		try {
			return new CodeLensCodeMining(codeLens, document, languageServerWrapper, CodeLensProvider.this);
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
			return null;
		}
	}

	@Override
	public CompletableFuture<List<? extends ICodeMining>> provideCodeMinings(ITextViewer viewer,
			IProgressMonitor monitor) {
		IDocument document = viewer.getDocument();
		return document != null ? provideCodeMinings(document) : null;
	}

}
