/*******************************************************************************
 * Copyright (c) 2016 Rogue Wave Software Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.services.TextDocumentService;

public class MockTextDocumentService implements TextDocumentService {

	private CompletionList completionList;

	private CompletableFuture<DidChangeTextDocumentParams> didChangeCallback;
	
	@Override
	public CompletableFuture<CompletionList> completion(TextDocumentPositionParams position) {
		return CompletableFuture.completedFuture(completionList);
	}

	@Override
	public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CompletableFuture<Hover> hover(TextDocumentPositionParams position) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CompletableFuture<SignatureHelp> signatureHelp(TextDocumentPositionParams position) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CompletableFuture<List<? extends Location>> definition(TextDocumentPositionParams position) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(TextDocumentPositionParams position) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CompletableFuture<List<? extends SymbolInformation>> documentSymbol(DocumentSymbolParams params) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CompletableFuture<List<? extends Command>> codeAction(CodeActionParams params) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CompletableFuture<CodeLens> resolveCodeLens(CodeLens unresolved) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams params) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CompletableFuture<List<? extends TextEdit>> onTypeFormatting(DocumentOnTypeFormattingParams params) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void didOpen(DidOpenTextDocumentParams params) {
		// TODO Auto-generated method stub

	}

	@Override
	public void didChange(DidChangeTextDocumentParams params) {
		if (didChangeCallback != null) {
			didChangeCallback.complete(params);
			didChangeCallback = null;
		}
	}

	@Override
	public void didClose(DidCloseTextDocumentParams params) {
		// TODO Auto-generated method stub

	}

	@Override
	public void didSave(DidSaveTextDocumentParams params) {
		// TODO Auto-generated method stub

	}
	
	public void setCompletionList(CompletionList completionList) {
		this.completionList = completionList;
	}

	public void setDidChangeCallback(CompletableFuture<DidChangeTextDocumentParams> didChangeExpectation) {
		this.didChangeCallback = didChangeExpectation;
	}
	
}
