/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.operations.codelens;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.codemining.LineHeaderCodeMining;
import org.eclipse.lsp4e.LanguageServersRegistry.LanguageServerDefinition;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.command.CommandExecutor;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.swt.events.MouseEvent;

public class LSPCodeMining extends LineHeaderCodeMining {
	private CodeLens codeLens;
	private final LanguageServer languageServer;
	private final LanguageServerDefinition languageServerDefinition;
	private final @Nullable IDocument document;

	public LSPCodeMining(CodeLens codeLens, IDocument document, LanguageServer languageServer, LanguageServerDefinition languageServerDefinition,
			CodeLensProvider provider) throws BadLocationException {
		super(codeLens.getRange().getStart().getLine(), document, provider, null);
		this.codeLens = codeLens;
		this.languageServer = languageServer;
		this.languageServerDefinition = languageServerDefinition;
		this.document = document;
		setLabel(getCodeLensString(codeLens));
	}

	protected static @Nullable String getCodeLensString(@NonNull CodeLens codeLens) {
		Command command = codeLens.getCommand();
		if (command == null || command.getTitle().isEmpty()) {
			return null;
		}
		return command.getTitle();
	}

	@Override
	protected CompletableFuture<Void> doResolve(ITextViewer viewer, IProgressMonitor monitor) {
		if (!LanguageServiceAccessor.checkCapability(languageServer,
				capabilites -> capabilites.getCodeLensProvider().getResolveProvider())) {
			return CompletableFuture.completedFuture(null);
		}
		return languageServer.getTextDocumentService().resolveCodeLens(this.codeLens)
				.thenAcceptAsync(resolvedCodeLens -> {
					codeLens = resolvedCodeLens;
					setLabel(getCodeLensString(resolvedCodeLens));
				});
	}

	@Override
	public final Consumer<MouseEvent> getAction() {
		final Command command = codeLens.getCommand();
		if(command != null && command.getCommand() != null) {
			return this::performAction;
		} else {
			return null;
		}
	}

	private void performAction(MouseEvent mouseEvent) {
		IDocument document = this.document;
		if(document != null) {
			CommandExecutor.executeCommand(codeLens.getCommand(), document, languageServerDefinition.id);
		}
	}


}
