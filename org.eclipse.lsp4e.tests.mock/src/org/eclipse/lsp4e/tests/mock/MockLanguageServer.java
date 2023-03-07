/*******************************************************************************
 * Copyright (c) 2016, 2022 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *  Mickael Istria (Red Hat Inc.) - added support for delays
 *  Lucas Bullen (Red Hat Inc.) - Bug 508458 - Add support for codelens.
 *  Kris De Volder (Pivotal Inc.) - Provide test code access to Client proxy.
 *  Rubén Porras Campo (Avaloq Evolution AG) - Add support for willSaveWaitUntil.
 *******************************************************************************/
package org.eclipse.lsp4e.tests.mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensOptions;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidChangeNotebookDocumentParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseNotebookDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenNotebookDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveNotebookDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentLink;
import org.eclipse.lsp4j.DocumentLinkOptions;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.LinkedEditingRangeRegistrationOptions;
import org.eclipse.lsp4j.LinkedEditingRanges;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.RenameOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureHelpOptions;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.NotebookDocumentService;

public final class MockLanguageServer implements LanguageServer {

	public static MockLanguageServer INSTANCE = new MockLanguageServer(MockLanguageServer::defaultServerCapabilities);

	/**
	 * This command will be reported on initialization to be supported for execution
	 * by the server
	 */
	public static String SUPPORTED_COMMAND_ID = "mock.command";

	private MockTextDocumentService textDocumentService = new MockTextDocumentService(this::buildMaybeDelayedFuture);
	private MockWorkspaceService workspaceService = new MockWorkspaceService(this::buildMaybeDelayedFuture);
	private InitializeResult initializeResult = new InitializeResult();
	private long delay = 0;
	private boolean started;

	private List<LanguageClient> remoteProxies = new ArrayList<>();

	private List<CompletableFuture<?>> inFlight = new CopyOnWriteArrayList<>();

	public static void reset() {
		INSTANCE = new MockLanguageServer(MockLanguageServer::defaultServerCapabilities);
	}

	public static void reset(final Supplier<ServerCapabilities> serverConfigurer) {
		INSTANCE = new MockLanguageServer(serverConfigurer);
	}

	private MockLanguageServer(final Supplier<ServerCapabilities> serverConfigurer) {
		resetInitializeResult(serverConfigurer);
	}

	/**
	 * Starts the language server on stdin/stdout
	 *
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */
	public static void main(String[] args) throws InterruptedException, ExecutionException {
		Launcher<LanguageClient> l = LSPLauncher.createServerLauncher(MockLanguageServer.INSTANCE, System.in, System.out);
		Future<?> f = l.startListening();
		MockLanguageServer.INSTANCE.addRemoteProxy(l.getRemoteProxy());
		f.get();
	}

	public void waitBeforeTearDown() {
		inFlight.forEach(future -> {
			try {
				future.join();
			} catch (CompletionException e) {
				System.err.println("Error waiting for in flight requests prior to teardown: " + e);
			} catch (CancellationException e) {
				// Not a test error!
			}
		});
	}

	public void addRemoteProxy(LanguageClient remoteProxy) {
		this.textDocumentService.addRemoteProxy(remoteProxy);
		this.remoteProxies.add(remoteProxy);
		this.started = true;
	}

	private void resetInitializeResult(final Supplier<ServerCapabilities> serverConfigurer) {
		initializeResult.setCapabilities(serverConfigurer.get());
	}

	public <U> CompletableFuture<U> buildMaybeDelayedFuture(U value) {
		if (delay > 0) {
			CompletableFuture<U> future = CompletableFuture.runAsync(() -> {
				try {
					Thread.sleep(delay);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}).thenApply(v -> value);
			inFlight.add(future);
			return future;
		}
		return CompletableFuture.completedFuture(value);
	}

	public static ServerCapabilities defaultServerCapabilities() {
		ServerCapabilities capabilities = new ServerCapabilities();
		capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);
		CompletionOptions completionProvider = new CompletionOptions(false, null);
		capabilities.setCompletionProvider(completionProvider);
		capabilities.setHoverProvider(true);
		capabilities.setDefinitionProvider(true);
		capabilities.setTypeDefinitionProvider(Boolean.TRUE);
		capabilities.setReferencesProvider(true);
		capabilities.setDocumentFormattingProvider(true);
		capabilities.setCodeActionProvider(Boolean.TRUE);
		capabilities.setCodeLensProvider(new CodeLensOptions(true));
		capabilities.setDocumentLinkProvider(new DocumentLinkOptions());
		capabilities.setSignatureHelpProvider(new SignatureHelpOptions());
		capabilities.setDocumentHighlightProvider(Boolean.TRUE);
		capabilities
				.setExecuteCommandProvider(new ExecuteCommandOptions(Collections.singletonList(SUPPORTED_COMMAND_ID)));
		RenameOptions prepareRenameProvider = new RenameOptions();
		prepareRenameProvider.setPrepareProvider(true);
		Either<Boolean, RenameOptions> renameEither = Either.forRight(prepareRenameProvider);
		capabilities.setRenameProvider(renameEither);
		capabilities.setColorProvider(Boolean.TRUE);
		capabilities.setDocumentSymbolProvider(Boolean.TRUE);
		capabilities.setLinkedEditingRangeProvider(new LinkedEditingRangeRegistrationOptions());
		return capabilities;
	}

	@Override
	public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
		return buildMaybeDelayedFuture(initializeResult);
	}

	@Override
	public MockTextDocumentService getTextDocumentService() {
		return textDocumentService;
	}

	public void setTextDocumentService(MockTextDocumentService custom) {
		textDocumentService = custom;
	}

	@Override
	public MockWorkspaceService getWorkspaceService() {
		return workspaceService;
	}

	public void setCompletionList(CompletionList completionList) {
		this.textDocumentService.setMockCompletionList(completionList);
	}

	public void setHover(Hover hover) {
		this.textDocumentService.setMockHover(hover);
	}

	public void setCodeLens(List<CodeLens> codeLens) {
		this.textDocumentService.setMockCodeLenses(codeLens);
	}

	public void setDefinition(List<? extends Location> definitionLocations){
		this.textDocumentService.setMockDefinitionLocations(definitionLocations);
	}

	public void setDidOpenCallback(CompletableFuture<DidOpenTextDocumentParams> didOpenExpectation) {
		this.textDocumentService.setDidOpenCallback(didOpenExpectation);
	}

	public List<DidChangeTextDocumentParams> getDidChangeEvents() {
		return this.textDocumentService.getDidChangeEvents();
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

	public void setDocumentHighlights(List<? extends DocumentHighlight> documentHighlights) {
		this.textDocumentService.setDocumentHighlights(documentHighlights);
	}

	public void setLinkedEditingRanges(LinkedEditingRanges linkedEditingRanges) {
		this.textDocumentService.setLinkedEditingRanges(linkedEditingRanges);
	}

	public void setCompletionTriggerChars(Set<String> chars) {
		if (chars != null) {
			initializeResult.getCapabilities().getCompletionProvider().setTriggerCharacters(new ArrayList<>(chars));
		}
	}

	public void setContextInformationTriggerChars(Set<String> chars) {
		if (chars != null) {
			initializeResult.getCapabilities().getSignatureHelpProvider().setTriggerCharacters(new ArrayList<>(chars));
		}
	}

	public void setWillSaveWaitUntil(List<TextEdit> edits) {
		TextDocumentSyncOptions textDocumentSyncOptions = new TextDocumentSyncOptions();
		textDocumentSyncOptions.setWillSaveWaitUntil(true);
		textDocumentSyncOptions.setSave(true);
		textDocumentSyncOptions.setChange(TextDocumentSyncKind.Full);
		initializeResult.getCapabilities().setTextDocumentSync(textDocumentSyncOptions);

		this.textDocumentService.setWillSaveWaitUntilCallback(edits);
	}

	public InitializeResult getInitializeResult() {
		return initializeResult;
	}

	@Override
	public CompletableFuture<Object> shutdown() {
		this.started = false;
		return buildMaybeDelayedFuture(Collections.emptySet());
	}

	@Override
	public void exit() {
	}

	public void setTimeToProceedQueries(long l) {
		this.delay = l;
	}

	public void setDiagnostics(List<Diagnostic> diagnostics) {
		this.textDocumentService.setDiagnostics(diagnostics);
	}

	public void setCodeActions(List<Either<Command, CodeAction>> codeActions) {
		this.textDocumentService.setCodeActions(codeActions);
	}

	public void setSignatureHelp(SignatureHelp signatureHelp) {
		this.textDocumentService.setSignatureHelp(signatureHelp);
	}

	public void setDocumentLinks(List<DocumentLink> documentLinks) {
		this.textDocumentService.setMockDocumentLinks(documentLinks);
	}

	public void setTypeDefinitions(List<LocationLink> locations) {
		this.textDocumentService.setMockTypeDefinitions(locations);
	}

	public boolean isRunning() {
		return this.started;
	}

	public List<LanguageClient> getRemoteProxies() {
		return remoteProxies;
	}

	public void setDocumentSymbols(DocumentSymbol documentSymbol) {
		this.textDocumentService.setDocumentSymbols(Collections.singletonList(documentSymbol));
	}

	public void setDocumentSymbols(DocumentSymbol... documentSymbols) {
		this.textDocumentService.setDocumentSymbols(Arrays.asList(documentSymbols));
	}

	@Override
	public NotebookDocumentService getNotebookDocumentService() {
		return new NotebookDocumentService() {
			@Override
			public void didSave(DidSaveNotebookDocumentParams params) {
				// TODO Auto-generated method stub
			}

			@Override
			public void didOpen(DidOpenNotebookDocumentParams params) {
				// TODO Auto-generated method stub
			}

			@Override
			public void didClose(DidCloseNotebookDocumentParams params) {
				// TODO Auto-generated method stub
			}

			@Override
			public void didChange(DidChangeNotebookDocumentParams params) {
				// TODO Auto-generated method stub
			}
		};
	}

}
