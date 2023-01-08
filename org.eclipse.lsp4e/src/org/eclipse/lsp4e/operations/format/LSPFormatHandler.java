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

import java.util.Collection;
import java.util.ConcurrentModificationException;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.lsp4e.ServerMessageHandler;
import org.eclipse.lsp4e.operations.format.LSPFormatter.VersionedFormatRequest;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

public class LSPFormatHandler extends AbstractHandler {

	private final LSPFormatter formatter = new LSPFormatter();

	@Override
	public Object execute(final ExecutionEvent event) throws ExecutionException {
		final ITextEditor textEditor = UI.getActiveTextEditor();
		if (textEditor == null)
			return null;

		final ISelection selection = HandlerUtil.getCurrentSelection(event);
		if (selection instanceof final ITextSelection textSelection && !textSelection.isEmpty()) {

			final IDocument doc = LSPEclipseUtils.getDocument(textEditor);
			if (doc == null)
				return null;

			final VersionedFormatRequest versionedEdits = formatter.versionedRequestFormatting(doc, textSelection);
			versionedEdits.edits().thenAcceptAsync(edits -> {
				if (!edits.isEmpty()) {
					UI.getDisplay().asyncExec(() -> {
						try {
							formatter.applyEdits(doc, edits, versionedEdits.version());
						} catch (final ConcurrentModificationException ex) {
							ServerMessageHandler.showMessage(Messages.LSPFormatHandler_DiscardedFormat,
									new MessageParams(MessageType.Error,
											Messages.LSPFormatHandler_DiscardedFormatResponse));
						}
					});
				}
			});
		}
		return null;
	}

	@Override
	public boolean isEnabled() {
		final ITextEditor textEditor = UI.getActiveTextEditor();
		if (textEditor == null)
			return false;

		final ISelection selection = textEditor.getSelectionProvider().getSelection();
		if (!(selection instanceof ITextSelection) || selection.isEmpty())
			return false;

		final IDocument doc = LSPEclipseUtils.getDocument(textEditor);
		if (doc == null)
			return false;

		final Collection<LSPDocumentInfo> infos = LanguageServiceAccessor.getLSPDocumentInfosFor(doc,
				LSPFormatter::supportsFormatting);
		return !infos.isEmpty();
	}
}
