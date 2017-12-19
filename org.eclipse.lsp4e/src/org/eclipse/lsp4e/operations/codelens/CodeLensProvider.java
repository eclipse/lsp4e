/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.lsp4e.operations.codelens;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.codemining.AbstractCodeMiningProvider;
import org.eclipse.jface.text.codemining.ICodeMining;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;

public class CodeLensProvider extends AbstractCodeMiningProvider {

	private CompletableFuture<List<? extends ICodeMining>> provideCodeMinings(@NonNull IDocument document) {
		List<@NonNull LSPDocumentInfo> docInfos = LanguageServiceAccessor.getLSPDocumentInfosFor(document,
				(capabilities) -> (capabilities.getCodeLensProvider() != null));
		final CompletableFuture<?>[] requests = new CompletableFuture<?>[docInfos.size()];
		final List<LSPCodeMining> codeLensResults = Collections.synchronizedList(new ArrayList<>(docInfos.size()));
		for (int i = 0; i < docInfos.size(); i++) {
			final LSPDocumentInfo info = docInfos.get(i);
			CodeLensParams param = new CodeLensParams(new TextDocumentIdentifier(info.getFileUri().toString()));
			requests[i] = info.getInitializedLanguageClient()
					.thenCompose(languageServer -> languageServer.getTextDocumentService().codeLens(param))
					.thenAccept(codeLenses -> {
						for (CodeLens codeLens : codeLenses) {
							if (codeLens != null) {
								try {
									codeLensResults
											.add(new LSPCodeMining(codeLens, document, info.getLanguageClient(), this));
								} catch (BadLocationException e) {
									LanguageServerPlugin.logError(e);
								}
							}
						}
					});
		}
		return CompletableFuture.allOf(requests).thenApply(theVoid -> codeLensResults);
	}

	@Override
	public CompletableFuture<List<? extends ICodeMining>> provideCodeMinings(ITextViewer viewer,
			IProgressMonitor monitor) {
		return provideCodeMinings(viewer.getDocument());
	}



}
