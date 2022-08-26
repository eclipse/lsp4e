/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.operations.inlayhint;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.codemining.LineContentCodeMining;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServersRegistry.LanguageServerDefinition;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintLabelPart;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageServer;

public class LSPLineContentCodeMining extends LineContentCodeMining {

	private InlayHint inlayHint;
	private final LanguageServer languageServer;

	public LSPLineContentCodeMining(InlayHint inlayHint, IDocument document, LanguageServer languageServer,
			LanguageServerDefinition languageServerDefinition, InlayHintProvider provider) throws BadLocationException {
		super(toPosition(inlayHint.getPosition(), document), provider);
		this.inlayHint = inlayHint;
		this.languageServer = languageServer;
		setLabel(getInlayHintString(inlayHint));
	}

	protected static @Nullable String getInlayHintString(@NonNull InlayHint inlayHint) {
		Either<String, List<InlayHintLabelPart>> label = inlayHint.getLabel();
		return label.map(Function.identity(), (parts) -> {
			if (parts == null) {
				return null;
			}
			return parts.stream().map(InlayHintLabelPart::getValue).collect(Collectors.joining());
		});
	}

	@Override
	protected CompletableFuture<Void> doResolve(ITextViewer viewer, IProgressMonitor monitor) {
		if (!LanguageServiceAccessor.checkCapability(languageServer, LSPLineContentCodeMining::canResolveInlayHint)) {
			return CompletableFuture.completedFuture(null);
		}
		return languageServer.getTextDocumentService().resolveInlayHint(this.inlayHint)
				.thenAcceptAsync(resolvedInlayHint -> {
					inlayHint = resolvedInlayHint;
					if (resolvedInlayHint != null) {
						setLabel(getInlayHintString(resolvedInlayHint));
					}
				});
	}

	private static boolean canResolveInlayHint(ServerCapabilities capabilities) {
		return capabilities.getInlayHintProvider().map(Function.identity(), (r) -> r.getResolveProvider());
	}

	/**
	 * Returns the Eclipse position from the given LSP position.
	 *
	 * @param position
	 *            the LSP position to convert
	 * @param document
	 * @return the Eclipse position from the given LSP position.
	 *
	 * @throws BadLocationException
	 */
	private static org.eclipse.jface.text.Position toPosition(Position position, IDocument document)
			throws BadLocationException {
		int start = LSPEclipseUtils.toOffset(position, document);
		return new org.eclipse.jface.text.Position(start, 1);
	}

}
