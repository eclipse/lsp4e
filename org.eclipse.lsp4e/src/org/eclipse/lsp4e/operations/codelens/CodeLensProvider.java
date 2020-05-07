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
import java.util.ArrayList;
import java.util.Collections;
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
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.services.LanguageServer;

public class CodeLensProvider extends AbstractCodeMiningProvider {

	private CompletableFuture<List<? extends ICodeMining>> provideCodeMinings(@NonNull IDocument document) {
		URI docURI = LSPEclipseUtils.toUri(document);
		if (docURI != null) {
			CodeLensParams param = new CodeLensParams(new TextDocumentIdentifier(docURI.toString()));
			List<LSPCodeMining> codeLensResults = Collections.synchronizedList(new ArrayList<>());
			return LanguageServiceAccessor
					.getLanguageServers(document, capabilities -> capabilities.getCodeLensProvider() != null)
					.thenComposeAsync(languageServers -> CompletableFuture.allOf(languageServers.stream()
							.map(languageServer -> languageServer.getTextDocumentService().codeLens(param)
									.thenAcceptAsync(codeLenses -> {
										// textDocument/codeLens may return null
										if (codeLenses != null) {
											codeLenses.stream().filter(Objects::nonNull)
													.map(codeLens -> toCodeMining(document, languageServer, codeLens))
													.filter(Objects::nonNull).forEach(codeLensResults::add);
										}
									}))
							.toArray(CompletableFuture[]::new)))
					.thenApplyAsync(theVoid -> codeLensResults);
		}
		else {
			return null;
		}
	}

	private LSPCodeMining toCodeMining(IDocument document, LanguageServer languageServer, CodeLens codeLens) {
		try {
			return new LSPCodeMining(codeLens, document, languageServer, LanguageServiceAccessor.resolveServerDefinition(languageServer).orElse(null), CodeLensProvider.this);
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
