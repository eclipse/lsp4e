/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.search.ui.NewSearchUI;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * LSP "Find references" handler.
 *
 */
public class LSFindReferences extends AbstractHandler implements IHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IEditorPart part = HandlerUtil.getActiveEditor(event);
		if (! (part instanceof ITextEditor)) {
			return null;
		}
		ITextEditor editor = (ITextEditor)part;
		IDocument document = LSPEclipseUtils.getDocument(editor);
		if (document == null) {
			return null;
		}
		ISelection sel = editor.getSelectionProvider().getSelection();
		if (!(sel instanceof ITextSelection)) {
			return null;
		}
		int offset = ((ITextSelection) sel).getOffset();
		LanguageServiceAccessor.getLanguageServers(document, capabilities -> LSPEclipseUtils.hasCapability(capabilities.getReferencesProvider())).thenAcceptAsync(languageServers -> {
			if (languageServers.isEmpty()) {
				return;
			}
			LanguageServer ls = languageServers.get(0);
			try {
				LSSearchQuery query = new LSSearchQuery(document, offset, ls);
				HandlerUtil.getActiveShell(event).getDisplay().asyncExec(() -> NewSearchUI.runQueryInBackground(query));
			} catch (BadLocationException e) {
				LanguageServerPlugin.logError(e);
			}
		});
		return null;
	}

	@Override
	public boolean isEnabled() {
		var page = UI.getActivePage();
		if(page == null) {
			return false;
		}
		IEditorPart part = page.getActiveEditor();
		if (!(part instanceof ITextEditor)) {
			return false;
		}
		ITextEditor editor = (ITextEditor) part;
		ISelection selection = editor.getSelectionProvider().getSelection();
		if (selection.isEmpty() || !(selection instanceof ITextSelection)) {
			return false;
		}
		try {
			return !LanguageServiceAccessor
					.getLanguageServers(LSPEclipseUtils.getDocument(editor),
							capabilities -> LSPEclipseUtils.hasCapability(capabilities.getReferencesProvider()))
					.get(50, TimeUnit.MILLISECONDS).isEmpty();
		} catch (TimeoutException | java.util.concurrent.ExecutionException e) {
			LanguageServerPlugin.logError(e);
		} catch (InterruptedException e) {
			LanguageServerPlugin.logError(e);
			Thread.currentThread().interrupt();
		}
		return false;
	}

	@Override
	public boolean isHandled() {
		return true;
	}

}
