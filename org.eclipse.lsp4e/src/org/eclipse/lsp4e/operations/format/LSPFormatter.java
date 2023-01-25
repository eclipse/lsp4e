/*******************************************************************************
 * Copyright (c) 2022 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *  Lucas Bullen (Red Hat Inc.) - [Bug 517428] Requests sent before initialization
 *                              - [Bug 528848] Formatting Request should include FormattingOptions
 *******************************************************************************/
package org.eclipse.lsp4e.operations.format;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.LanguageServers.LanguageServerDocumentExecutor;
import org.eclipse.lsp4e.LanguageServers.VersionedEdits;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.texteditor.AbstractDecoratedTextEditorPreferenceConstants;

public class LSPFormatter {
	public CompletableFuture<Optional<VersionedEdits>> requestFormatting(@NonNull IDocument document, @NonNull ITextSelection textSelection) throws BadLocationException {
		URI uri = LSPEclipseUtils.toUri(document);
		if (uri == null) {
			return CompletableFuture.completedFuture(Optional.empty());
		}
		LanguageServerDocumentExecutor executor = LanguageServers.forDocument(document).withFilter(LSPFormatter::supportsFormatting);
		FormattingOptions formatOptions = getFormatOptions();
		TextDocumentIdentifier docId = new TextDocumentIdentifier(uri.toString());

		DocumentRangeFormattingParams rangeParams = new DocumentRangeFormattingParams();
		rangeParams.setTextDocument(docId);
		rangeParams.setOptions(formatOptions);
		boolean fullFormat = textSelection.getLength() == 0;
		Position start = LSPEclipseUtils.toPosition(fullFormat ? 0 : textSelection.getOffset(), document);
		Position end = LSPEclipseUtils.toPosition(
				fullFormat ? document.getLength() : textSelection.getOffset() + textSelection.getLength(),
				document);
		rangeParams.setRange(new Range(start, end));

		DocumentFormattingParams params = new DocumentFormattingParams();
		params.setTextDocument(docId);
		params.setOptions(formatOptions);

		// TODO: Could refine this algorithm: at present this grabs the first non-null response but the most functional
		// implementation (if a text selection is present) would try all the servers in turn to see if they supported
		return executor.computeFirst((w, ls) -> {
			final ServerCapabilities capabilities = w.getServerCapabilities();
			if (isDocumentRangeFormattingSupported(capabilities)
					&& (!isDocumentFormattingSupported(capabilities)
							|| textSelection.getLength() != 0)) {
				return ls.getTextDocumentService().rangeFormatting(rangeParams).thenApply(executor::toVersionedEdits);
			}

			return ls.getTextDocumentService().formatting(params).thenApply(executor::toVersionedEdits);
		});

	}

	private FormattingOptions getFormatOptions() {
		IPreferenceStore store = EditorsUI.getPreferenceStore();
		int tabWidth = store.getInt(AbstractDecoratedTextEditorPreferenceConstants.EDITOR_TAB_WIDTH);
		boolean insertSpaces = store.getBoolean(AbstractDecoratedTextEditorPreferenceConstants.EDITOR_SPACES_FOR_TABS);
		return new FormattingOptions(tabWidth, insertSpaces);
	}

	private static boolean isDocumentRangeFormattingSupported(ServerCapabilities capabilities) {
		return LSPEclipseUtils.hasCapability(capabilities.getDocumentRangeFormattingProvider());
	}

	private static boolean isDocumentFormattingSupported(ServerCapabilities capabilities) {
		return LSPEclipseUtils.hasCapability(capabilities.getDocumentFormattingProvider());
	}

	public static boolean supportsFormatting(ServerCapabilities capabilities) {
		return isDocumentFormattingSupported(capabilities)
				|| isDocumentRangeFormattingSupported(capabilities);
	}

}
