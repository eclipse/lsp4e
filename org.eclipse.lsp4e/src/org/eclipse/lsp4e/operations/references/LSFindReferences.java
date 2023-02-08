/*******************************************************************************
 * Copyright (c) 2016-23 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Michał Niewrzał (Rogue Wave Software Inc.)
 *  Angelo Zerr <angelo.zerr@gmail.com> - fix Bug 526255
 *******************************************************************************/
package org.eclipse.lsp4e.operations.references;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.LanguageServers.LanguageServerDocumentExecutor;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.search.ui.NewSearchUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * LSP "Find references" handler.
 *
 */
public class LSFindReferences extends AbstractHandler implements IHandler {

	private @NonNull LanguageServerDocumentExecutor getExecutor(@NonNull IDocument document) {
		return LanguageServers.forDocument(document).withCapability(ServerCapabilities::getReferencesProvider);
	}

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		if (HandlerUtil.getActiveEditor(event) instanceof ITextEditor editor) {
			ISelection sel = editor.getSelectionProvider().getSelection();
			if (sel instanceof ITextSelection textSelection) {
				IDocument document = LSPEclipseUtils.getDocument(editor);
				if (document != null) {
					try {
						final var query = new LSSearchQuery(textSelection.getOffset(), getExecutor(document));
						HandlerUtil.getActiveShell(event).getDisplay().asyncExec(() -> NewSearchUI.runQueryInBackground(query));
					} catch (BadLocationException e) {
						LanguageServerPlugin.logError(e);
					}
				}
			}
		}
		return null;
	}

	@Override
	public boolean isEnabled() {
		var page = UI.getActivePage();
		if(page == null) {
			return false;
		}
		if (page.getActiveEditor() instanceof ITextEditor editor) {
			ISelection selection = editor.getSelectionProvider().getSelection();
			if (selection.isEmpty() || !(selection instanceof ITextSelection)) {
				return false;
			}
			IDocument document = LSPEclipseUtils.getDocument(editor);
			if (document != null) {
				return getExecutor(document).anyMatching();
			}
		}
		return false;
	}

	@Override
	public boolean isHandled() {
		return true;
	}

}
