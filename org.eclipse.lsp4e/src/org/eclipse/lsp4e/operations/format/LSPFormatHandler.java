/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Michał Niewrzał (Rogue Wave Software Inc.)
 *  Sebastian Thomschke (Vegard IT GmbH)
 *******************************************************************************/
package org.eclipse.lsp4e.operations.format;

import java.util.ConcurrentModificationException;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.ServerMessageHandler;
import org.eclipse.lsp4e.internal.LSPDocumentAbstractHandler;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

public class LSPFormatHandler extends LSPDocumentAbstractHandler {

	private final LSPFormatter formatter = new LSPFormatter();

	@Override
	protected void execute(ExecutionEvent event, ITextEditor textEditor) {
		final ISelection selection = HandlerUtil.getCurrentSelection(event);
		if (selection instanceof final ITextSelection textSelection && !textSelection.isEmpty()) {

			final IDocument doc = LSPEclipseUtils.getDocument(textEditor);
			if (doc == null)
				return;

			try {
				formatter.requestFormatting(doc, textSelection)
					.thenAcceptAsync(result -> {
						result.ifPresent(edits -> {
							try {
								edits.apply();
							} catch (final ConcurrentModificationException ex) {
								ServerMessageHandler.showMessage(Messages.LSPFormatHandler_DiscardedFormat,
										new MessageParams(MessageType.Error,
												Messages.LSPFormatHandler_DiscardedFormatResponse));
							} catch (BadLocationException e) {
								LanguageServerPlugin.logError(e);
							}
						});
					}, UI.getDisplay());
			} catch (BadLocationException e) {
				LanguageServerPlugin.logError(e);
			}
		}
	}

	@Override
	public void setEnabled(Object evaluationContext) {
		setEnabled(LSPFormatter::supportsFormatting, this::hasSelection);
	}
}
