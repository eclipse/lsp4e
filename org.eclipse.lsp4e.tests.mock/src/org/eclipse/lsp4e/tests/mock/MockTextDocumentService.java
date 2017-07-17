/*******************************************************************************
 * Copyright (c) 2017 Rogue Wave Software Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *  Mickael Istria (Red Hat Inc.) - Support for delay and mock references
 *******************************************************************************/
package org.eclipse.lsp4e.tests.mock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentLink;
import org.eclipse.lsp4j.DocumentLinkParams;
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;

public class MockTextDocumentService implements TextDocumentService {

	private CompletionList mockCompletionList;
	private Hover mockHover;
	private List<? extends Location> mockDefinitionLocations;
	private List<? extends TextEdit> mockFormattingTextEdits;
	private SignatureHelp mockSignatureHelp;
	private List<DocumentLink> mockDocumentLinks;

	private CompletableFuture<DidOpenTextDocumentParams> didOpenCallback;
	private CompletableFuture<DidChangeTextDocumentParams> didChangeCallback;
	private CompletableFuture<DidSaveTextDocumentParams> didSaveCallback;
	private CompletableFuture<DidCloseTextDocumentParams> didCloseCallback;

	private Function<?,? extends CompletableFuture<?>> _futureFactory;
	private List<LanguageClient> remoteProxies;
	private Location mockReferences;
	private List<Diagnostic> diagnostics;
	private List<Command> mockCodeActions;
	
	public <U> MockTextDocumentService(Function<U, CompletableFuture<U>> futureFactory) {
		this._futureFactory = futureFactory;
		// Some default values for mocks, can be overriden
		CompletionItem item = new CompletionItem();
		item.setLabel("Mock completion item");
		mockCompletionList = new CompletionList(false, Collections.singletonList(item));
		mockHover = new Hover(Collections.singletonList(Either.forLeft("Mock hover")), null);
		this.remoteProxies = new ArrayList<>();
	}

	private <U> CompletableFuture<U> futureFactory(U value) {
		return ((Function<U, CompletableFuture<U>>)this._futureFactory).apply(value);
	}

	@Override
	public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(TextDocumentPositionParams position) {
		return futureFactory(Either.forRight(mockCompletionList));
	}

	@Override
	public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved) {
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public CompletableFuture<Hover> hover(TextDocumentPositionParams position) {
		return CompletableFuture.completedFuture(mockHover);
	}

	@Override
	public CompletableFuture<SignatureHelp> signatureHelp(TextDocumentPositionParams position) {
		return CompletableFuture.completedFuture(mockSignatureHelp);
	}

	@Override
	public CompletableFuture<List<? extends Location>> definition(TextDocumentPositionParams position) {
		return CompletableFuture.completedFuture(mockDefinitionLocations);
	}

	@Override
	public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
		return futureFactory(Collections.singletonList(this.mockReferences));
	}

	@Override
	public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(TextDocumentPositionParams position) {
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public CompletableFuture<List<? extends SymbolInformation>> documentSymbol(DocumentSymbolParams params) {
		return CompletableFuture.completedFuture(null);
	}
	
	@Override
	public CompletableFuture<List<DocumentLink>> documentLink(DocumentLinkParams params) {
		return CompletableFuture.completedFuture(mockDocumentLinks);
	}

	@Override
	public CompletableFuture<List<? extends Command>> codeAction(CodeActionParams params) {
		return CompletableFuture.completedFuture(this.mockCodeActions);
	}

	@Override
	public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public CompletableFuture<CodeLens> resolveCodeLens(CodeLens unresolved) {
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
		return CompletableFuture.completedFuture(mockFormattingTextEdits);
	}

	@Override
	public CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams params) {
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public CompletableFuture<List<? extends TextEdit>> onTypeFormatting(DocumentOnTypeFormattingParams params) {
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public void didOpen(DidOpenTextDocumentParams params) {
		if (didOpenCallback != null) {
			didOpenCallback.complete(params);
			didOpenCallback = null;
		}

		if (this.diagnostics != null && !this.diagnostics.isEmpty()) {
			this.remoteProxies.stream().forEach(p -> p.publishDiagnostics(new PublishDiagnosticsParams(params.getTextDocument().getUri(), this.diagnostics)));
		}
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
		if (didCloseCallback != null) {
			didCloseCallback.complete(params);
			didCloseCallback = null;
		}
	}

	@Override
	public void didSave(DidSaveTextDocumentParams params) {
		if (didSaveCallback != null) {
			didSaveCallback.complete(params);
			didSaveCallback = null;
		}
	}
	
	public void setMockCompletionList(CompletionList completionList) {
		this.mockCompletionList = completionList;
	}

	public void setDidOpenCallback(CompletableFuture<DidOpenTextDocumentParams> didOpenExpectation) {
		this.didOpenCallback = didOpenExpectation;
	}
	
	public void setDidChangeCallback(CompletableFuture<DidChangeTextDocumentParams> didChangeExpectation) {
		this.didChangeCallback = didChangeExpectation;
	}
	
	public void setDidSaveCallback(CompletableFuture<DidSaveTextDocumentParams> didSaveExpectation) {
		this.didSaveCallback = didSaveExpectation;
	}
	
	public void setDidCloseCallback(CompletableFuture<DidCloseTextDocumentParams> didCloseExpectation) {
		this.didCloseCallback = didCloseExpectation;
	}
	
	public void setMockHover(Hover hover) {
		this.mockHover = hover;
	}
	
	public void setMockDefinitionLocations(List<? extends Location> definitionLocations) {
		this.mockDefinitionLocations = definitionLocations;
	}

	public void setMockReferences(Location location) {
		this.mockReferences = location;
	}
	
	public void setMockFormattingTextEdits(List<? extends TextEdit> formattingTextEdits) {
		this.mockFormattingTextEdits = formattingTextEdits;
	}

	public void setMockDocumentLinks(List<DocumentLink> documentLinks) {
		this.mockDocumentLinks = documentLinks;
	}
	
	public void reset() {
		this.mockCompletionList = new CompletionList();
		this.mockDefinitionLocations = Collections.emptyList();
		this.mockHover = null;
		this.mockReferences = null;
		this.remoteProxies = new ArrayList<LanguageClient>();
		this.mockCodeActions = new ArrayList<Command>();
	}

	public void setDiagnostics(List<Diagnostic> diagnostics) {
		this.diagnostics = diagnostics;
	}

	public void addRemoteProxy(LanguageClient remoteProxy) {
		this.remoteProxies.add(remoteProxy);
	}

	public void setCodeActions(List<Command> commands) {
		this.mockCodeActions = commands;
	}
	
	public void setSignatureHelp(SignatureHelp signatureHelp) {
		this.mockSignatureHelp = signatureHelp;
	}
	
}
