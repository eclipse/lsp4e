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

import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

public class LSPSymbolInFileHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IEditorPart part = HandlerUtil.getActiveEditor(event);
		if (part instanceof ITextEditor) {
			final ITextEditor textEditor = (ITextEditor) part;
			final IDocument document = LSPEclipseUtils.getDocument(textEditor);
			if (document == null) {
				return null;
			}

			List<@NonNull LSPDocumentInfo> infos = LanguageServiceAccessor.getLSPDocumentInfosFor(document,
					capabilities -> LSPEclipseUtils.hasCapability(capabilities.getDocumentSymbolProvider()));
			if (infos.isEmpty()) {
				return null;
			}

			// TODO maybe consider better strategy such as iterating on all LS until we have
			// a good result
			final LSPDocumentInfo info = infos.get(0);
			info.getInitializedLanguageClient().thenAcceptAsync(langServer -> {
				final Shell shell = HandlerUtil.getActiveShell(event);
				shell.getDisplay()
						.asyncExec(() -> new LSPSymbolInFileDialog(shell, textEditor, document, langServer).open());
			});
		}
		return null;
	}

	@Override
	public boolean isEnabled() {
		IWorkbenchPart part = UI.getActivePart();
		if (part instanceof ITextEditor) {
			final ITextEditor textEditor = (ITextEditor) part;
			final IDocument document = LSPEclipseUtils.getDocument(textEditor);
			if (document == null) {
				return false;
			}
			List<@NonNull LSPDocumentInfo> infos = LanguageServiceAccessor.getLSPDocumentInfosFor(document,
					capabilities -> LSPEclipseUtils.hasCapability(capabilities.getDocumentSymbolProvider()));
			return !infos.isEmpty();
		}
		return false;
	}
}
