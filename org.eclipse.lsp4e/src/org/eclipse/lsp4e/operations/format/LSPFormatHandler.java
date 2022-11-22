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
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.part.MultiPageEditorPart;
import org.eclipse.ui.texteditor.ITextEditor;

public class LSPFormatHandler extends AbstractHandler {

	private final LSPFormatter formatter = new LSPFormatter();

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IEditorPart part = HandlerUtil.getActiveEditor(event);
		if (part instanceof MultiPageEditorPart multoPage) {
			Object selectedPage = multoPage.getSelectedPage();
			if (selectedPage instanceof IEditorPart editor) {
				part = editor;
			}
		}

		if (part instanceof ITextEditor textEditor) {
			final IDocument document = LSPEclipseUtils.getDocument(textEditor);
			final Shell shell = textEditor.getSite().getShell();
			ISelection selection = HandlerUtil.getCurrentSelection(event);
			if (document != null && selection instanceof ITextSelection textSelection) {
				VersionedFormatRequest versionedEdits = formatter.versionedRequestFormatting(document, textSelection);
				versionedEdits.edits().thenAcceptAsync(edits -> {
					if (!edits.isEmpty()) {
						shell.getDisplay().asyncExec(() -> {
							try {
								formatter.applyEdits(document, edits, versionedEdits.version());
							} catch (ConcurrentModificationException e) {
								ServerMessageHandler.showMessage(Messages.LSPFormatHandler_DiscardedFormat, //
										new MessageParams(MessageType.Error,
												Messages.LSPFormatHandler_DiscardedFormatResponse));
							}
						});
					}
				});
			}
		}
		return null;
	}

	@Override
	public boolean isEnabled() {
		IWorkbenchPart part = UI.getActivePart();
		if (part instanceof MultiPageEditorPart multiPage) {
			Object selectedPage = multiPage.getSelectedPage();
			if (selectedPage instanceof IWorkbenchPart workbenchPart) {
				part = workbenchPart;
			}
		}

		if (part instanceof ITextEditor textEditor) {
			Collection<LSPDocumentInfo> infos = LanguageServiceAccessor.getLSPDocumentInfosFor(
					LSPEclipseUtils.getDocument(textEditor),
					LSPFormatter::supportFormatting);
			ISelection selection = textEditor.getSelectionProvider().getSelection();
			return !infos.isEmpty() && !selection.isEmpty() && selection instanceof ITextSelection;
		}
		return false;
	}

}
