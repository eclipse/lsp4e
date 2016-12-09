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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

public final class MockLanguageSever implements LanguageServer {

	public static final MockLanguageSever INSTANCE = new MockLanguageSever();

	private MockTextDocumentService textDocumentService = new MockTextDocumentService();
	private MockWorkspaceService workspaceService = new MockWorkspaceService();
	private Set<String> completionTriggerChars;

	private MockLanguageSever() {
	}

	@Override
	public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
		ServerCapabilities capabilities = new ServerCapabilities();
		capabilities.setTextDocumentSync(TextDocumentSyncKind.Incremental);
		CompletionOptions completionProvider = new CompletionOptions();
		if (this.completionTriggerChars != null) {
			completionProvider.setTriggerCharacters(new ArrayList<>(this.completionTriggerChars));
		}
		capabilities.setCompletionProvider(completionProvider);
		return CompletableFuture.completedFuture(new InitializeResult(capabilities));
	}

	@Override
	public TextDocumentService getTextDocumentService() {
		return textDocumentService;
	}

	@Override
	public WorkspaceService getWorkspaceService() {
		return workspaceService;
	}

	public void setCompletionList(CompletionList completionList) {
		this.textDocumentService.setCompletionList(completionList);
	}

	public void setDidChangeCallback(CompletableFuture<DidChangeTextDocumentParams> didChangeExpectation) {
		this.textDocumentService.setDidChangeCallback(didChangeExpectation);
	}

	public void setCompletionTriggerChars(Set<String> chars) {
		this.completionTriggerChars = new HashSet<>(chars);
	}

	@Override
	public CompletableFuture<Object> shutdown() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void exit() {
	}

}
