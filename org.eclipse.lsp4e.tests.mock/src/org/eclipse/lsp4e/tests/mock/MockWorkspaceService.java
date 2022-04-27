/*******************************************************************************
 * Copyright (c) 2016 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *  Max Bureck (Fraunhofer FOKUS) - Added ability to check for executed command
 *******************************************************************************/
package org.eclipse.lsp4e.tests.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.services.WorkspaceService;

public class MockWorkspaceService implements WorkspaceService {

	private Function<?, ?> _futureFactory;
	private CompletableFuture<ExecuteCommandParams> executedCommand = new CompletableFuture<>();
	private List<DidChangeWorkspaceFoldersParams> workspaceFoldersEvents = new ArrayList<>();

	public <U> MockWorkspaceService(Function<U, CompletableFuture<U>> futureFactory) {
		this._futureFactory = futureFactory;
	}

	/**
	 * Use this method to get a future that will wait specified delay before returning
	 * value
	 * @param value the value that will be returned by the future
	 * @return a future that completes to value, after delay from {@link MockLanguageServer#delay}
	 */
	private <U> CompletableFuture<U> futureFactory(U value) {
		return ((Function<U, CompletableFuture<U>>)this._futureFactory).apply(value);
	}
	
	@Override
	public CompletableFuture<List<? extends SymbolInformation>> symbol(WorkspaceSymbolParams params) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void didChangeConfiguration(DidChangeConfigurationParams params) {
		// TODO Auto-generated method stub

	}

	@Override
	public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
		// TODO Auto-generated method stub

	}

	@Override
	public void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params) {
		workspaceFoldersEvents.add(params);
	}

	public List<DidChangeWorkspaceFoldersParams> getWorkspaceFoldersEvents() {
		return this.workspaceFoldersEvents;
	}

	@Override
	public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
		executedCommand.complete(params);
		return futureFactory(null);
	}

	public CompletableFuture<ExecuteCommandParams> getExecutedCommand() {
		return executedCommand;
	}
}
