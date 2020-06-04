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

import java.util.Collection;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.expressions.IEvaluationContext;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ISources;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

public class LSPSymbolInFileHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IEditorPart part = HandlerUtil.getActiveEditor(event);
		if (part instanceof ITextEditor) {
			final ITextEditor textEditor = (ITextEditor) part;
			Collection<LSPDocumentInfo> infos = LanguageServiceAccessor.getLSPDocumentInfosFor(
					LSPEclipseUtils.getDocument(textEditor),
					capabilities -> Boolean.TRUE.equals(capabilities.getDocumentSymbolProvider()));
			if (infos.isEmpty()) {
				return null;
			}
			// TODO maybe consider better strategy such as iterating on all LS until we have a good result
			LSPDocumentInfo info = infos.iterator().next();
			final Shell shell = HandlerUtil.getActiveShell(event);
			DocumentSymbolParams params = new DocumentSymbolParams(
					new TextDocumentIdentifier(info.getFileUri().toString()));
			info.getInitializedLanguageClient()
					.thenComposeAsync(langaugeServer -> langaugeServer.getTextDocumentService().documentSymbol(params))
					.thenAcceptAsync(t -> shell.getDisplay().asyncExec(() -> {
						LSPSymbolInFileDialog dialog = new LSPSymbolInFileDialog(shell, textEditor,
								info.getFileUri(), t);
						dialog.open();
					}));
		}
		return null;
	}

	@Override
	public void setEnabled(Object evaluationContext) {
		boolean enabled = false;
		if (evaluationContext instanceof IEvaluationContext) {
			Object activeEditor = ((IEvaluationContext) evaluationContext).getVariable(ISources.ACTIVE_EDITOR_NAME);
			if (activeEditor instanceof ITextEditor) {
				enabled = isEnabled((ITextEditor)activeEditor);
			}
		}
		setBaseEnabled(enabled);
	}

	private boolean isEnabled(ITextEditor part) {
		ISelection selection = part.getSelectionProvider().getSelection();
		List<LSPDocumentInfo> infos = LanguageServiceAccessor.getLSPDocumentInfosFor(
				LSPEclipseUtils.getDocument(part),
				capabilities -> Boolean.TRUE.equals(capabilities.getDocumentSymbolProvider()));
		return !infos.isEmpty();
	}
}
