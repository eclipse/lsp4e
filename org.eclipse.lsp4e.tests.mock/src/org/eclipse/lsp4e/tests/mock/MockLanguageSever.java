/*******************************************************************************
 * Copyright (c) 2016-2017 Rogue Wave Software Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *  Mickael Istria (Red Hat Inc.) - added support for delays
 *******************************************************************************/
package org.eclipse.lsp4e.tests.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Function;

import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentLink;
import org.eclipse.lsp4j.DocumentLinkOptions;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.WorkspaceService;

public final class MockLanguageSever implements LanguageServer {

	public static final MockLanguageSever INSTANCE = new MockLanguageSever();

	private MockTextDocumentService textDocumentService = new MockTextDocumentService(this::buildMaybeDelayedFuture);
	private MockWorkspaceService workspaceService = new MockWorkspaceService(this::buildMaybeDelayedFuture);
	private InitializeResult initializeResult = new InitializeResult();
	private long delay = 0;

	private MockLanguageSever() {
		resetInitializeResult();
	}
	
	/**
	 * Starts the language server on stdin/stdout
	 * @throws ExecutionException 
	 * @throws InterruptedException 
	 */
	public static void main(String[] args) throws InterruptedException, ExecutionException {
		Launcher<LanguageClient> l = LSPLauncher.createServerLauncher(MockLanguageSever.INSTANCE, System.in, System.out);
		Future<?> f = l.startListening();
		MockLanguageSever.INSTANCE.addRemoteProxy(l.getRemoteProxy());
		f.get();
	}

	public void addRemoteProxy(LanguageClient remoteProxy) {
		this.textDocumentService.addRemoteProxy(remoteProxy);
	}

	private void resetInitializeResult() {
		ServerCapabilities capabilities = new ServerCapabilities();
		capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);
		CompletionOptions completionProvider = new CompletionOptions();
		capabilities.setCompletionProvider(completionProvider);
		capabilities.setHoverProvider(true);
		capabilities.setDefinitionProvider(true);
		capabilities.setReferencesProvider(true);
		capabilities.setDocumentFormattingProvider(true);
		capabilities.setCodeActionProvider(Boolean.TRUE);
		capabilities.setDocumentLinkProvider(new DocumentLinkOptions());
		initializeResult.setCapabilities(capabilities);
	}
	
	<U> CompletableFuture<U> buildMaybeDelayedFuture(U value) {
		if (delay > 0) {
			return CompletableFuture.runAsync(() -> {
				try {
					Thread.sleep(delay);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}).thenApply(new Function<Void, U>() {
				public U apply(Void v) {
					return value;
				}
			});  
		}
		return CompletableFuture.completedFuture(value);
	}

	@Override
	public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
		return buildMaybeDelayedFuture(initializeResult);
	}

	@Override
	public MockTextDocumentService getTextDocumentService() {
		return textDocumentService;
	}

	@Override
	public WorkspaceService getWorkspaceService() {
		return workspaceService;
	}

	public void setCompletionList(CompletionList completionList) {
		this.textDocumentService.setMockCompletionList(completionList);
	}
	
	public void setHover(Hover hover) {
		this.textDocumentService.setMockHover(hover);
	}
	
	public void setDefinition(List<? extends Location> definitionLocations){
		this.textDocumentService.setMockDefinitionLocations(definitionLocations);
	}

	public void setDidChangeCallback(CompletableFuture<DidChangeTextDocumentParams> didChangeExpectation) {
		this.textDocumentService.setDidChangeCallback(didChangeExpectation);
	}
	
	public void setDidSaveCallback(CompletableFuture<DidSaveTextDocumentParams> didSaveExpectation) {
		this.textDocumentService.setDidSaveCallback(didSaveExpectation);
	}
	
	public void setDidCloseCallback(CompletableFuture<DidCloseTextDocumentParams> didCloseExpectation) {
		this.textDocumentService.setDidCloseCallback(didCloseExpectation);
	}
	
	public void setFormattingTextEdits(List<? extends TextEdit> formattingTextEdits) {
		this.textDocumentService.setMockFormattingTextEdits(formattingTextEdits);
	}

	public void setCompletionTriggerChars(Set<String> chars) {
		if (chars != null) {
			initializeResult.getCapabilities().getCompletionProvider().setTriggerCharacters(new ArrayList<>(chars));
		}
	}

	public InitializeResult getInitializeResult() {
		return initializeResult;
	}

	@Override
	public CompletableFuture<Object> shutdown() {
		this.delay = 0;
		resetInitializeResult();
		this.textDocumentService.reset();
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public void exit() {
	}

	public void setTimeToProceedQueries(int i) {
		this.delay = i;
	}

	public void setDiagnostics(List<Diagnostic> diagnostics) {
		this.textDocumentService.setDiagnostics(diagnostics);
	}

	public void setCodeActions(List<Command> commands) {
		this.textDocumentService.setCodeActions(commands);
	}

	public void setSignatureHelp(SignatureHelp signatureHelp) {
		this.textDocumentService.setSignatureHelp(signatureHelp);
	}

	public void setDocumentLinks(List<DocumentLink> documentLinks) {
		this.textDocumentService.setMockDocumentLinks(documentLinks);
	}
	
}
