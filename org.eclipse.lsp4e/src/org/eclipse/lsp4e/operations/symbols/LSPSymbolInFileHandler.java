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
 *  Lucas Bullen (Red Hat Inc.) - [Bug 517428] Requests sent before initialization
 *******************************************************************************/
package org.eclipse.lsp4e.operations.symbols;

import java.util.concurrent.CompletableFuture;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.HandlerEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

public class LSPSymbolInFileHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IEditorPart part = HandlerUtil.getActiveEditor(event);
		if (part instanceof ITextEditor textEditor) {
			final IDocument document = LSPEclipseUtils.getDocument(textEditor);
			if (document == null) {
				return null;
			}

			final Shell shell = HandlerUtil.getActiveShell(event);

			if (shell == null) {
				return null;
			}

			// TODO maybe consider better strategy such as iterating on all LS until we have
			// a good result
			LanguageServers.forDocument(document).withCapability(ServerCapabilities::getDocumentSymbolProvider)
					.computeFirst((w, ls) -> CompletableFuture.completedFuture(w))
					.thenAcceptAsync(oW -> oW.ifPresent(w -> {
						if (w != null) {
							new LSPSymbolInFileDialog(shell, textEditor, document, w).open();
						}
					}), shell.getDisplay());
		}
		return null;
	}

	@Override
	public boolean isEnabled() {
		IWorkbenchPart part = UI.getActivePart();
		if (part instanceof final ITextEditor textEditor) {
			final IDocument document = LSPEclipseUtils.getDocument(textEditor);
			if (document == null) {
				return false;
			}

			return LanguageServers.forDocument(document).withCapability(ServerCapabilities::getDocumentSymbolProvider)
					.anyMatching(() -> fireHandlerChanged(new HandlerEvent(this, true, false)));
		}
		return false;
	}
}
