/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Michał Niewrzał (Rogue Wave Software Inc.)
 *******************************************************************************/
package org.eclipse.lsp4e.operations.format;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

public class LSPFormatHandler extends AbstractHandler implements IHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IEditorPart part = HandlerUtil.getActiveEditor(event);
		if (part instanceof ITextEditor) {
			ITextEditor textEditor = (ITextEditor) part;
			LSPDocumentInfo info = LanguageServiceAccessor.getLSPDocumentInfoFor(
					LSPEclipseUtils.getDocument(textEditor),
			        (capabilities) -> supportFormatting(capabilities));
			if (info != null) {
				ISelection sel = textEditor.getSelectionProvider().getSelection();
				if (sel instanceof TextSelection) {
					TextSelection textSelection = (TextSelection) sel;
					try {
						CompletableFuture<List<? extends TextEdit>> formatter = format(info, textSelection);
						final Shell shell = HandlerUtil.getActiveShell(event);
						formatter.thenAccept((List<? extends TextEdit> t) -> {
							shell.getDisplay().asyncExec(() -> {
								LSPEclipseUtils.applyEdits(info.getDocument(), t);
							});
						});
					} catch (BadLocationException e) {
						// TODO
						e.printStackTrace();
						return null;
					}
				}
			}
		}
		return null;
	}

	private CompletableFuture<List<? extends TextEdit>> format(LSPDocumentInfo info, TextSelection textSelection)
	        throws BadLocationException {
		TextDocumentIdentifier docId = new TextDocumentIdentifier(info.getFileUri().toString());
		ServerCapabilities capabilities = info.getCapabilites();
		// use range formatting if standard formatting is not supported or text is selected
		if (capabilities != null && Boolean.TRUE.equals(capabilities.getDocumentRangeFormattingProvider())
		        && (capabilities.getDocumentFormattingProvider() == null
		                || !capabilities.getDocumentFormattingProvider() || textSelection.getLength() != 0)) {
			DocumentRangeFormattingParams params = new DocumentRangeFormattingParams();
			params.setTextDocument(docId);
			params.setOptions(new FormattingOptions());
			boolean fullFormat = textSelection.getLength() == 0;
			Position start = LSPEclipseUtils.toPosition(fullFormat ? 0 : textSelection.getOffset(), info.getDocument());
			Position end = LSPEclipseUtils.toPosition(
			        fullFormat ? info.getDocument().getLength() : textSelection.getOffset() + textSelection.getLength(),
			        info.getDocument());
			params.setRange(new Range(start, end));
			return info.getLanguageClient().getTextDocumentService().rangeFormatting(params);
		}

		DocumentFormattingParams params = new DocumentFormattingParams();
		params.setTextDocument(docId);
		params.setOptions(new FormattingOptions());
		return info.getLanguageClient().getTextDocumentService().formatting(params);
	}

	@Override
	public boolean isEnabled() {
		IWorkbenchPart part = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActivePart();
		if (part instanceof ITextEditor) {
			LSPDocumentInfo info = LanguageServiceAccessor.getLSPDocumentInfoFor(
					LSPEclipseUtils.getDocument((ITextEditor) part),
			        (capabilities) -> supportFormatting(capabilities));
			ISelection selection = ((ITextEditor) part).getSelectionProvider().getSelection();
			return info != null && !selection.isEmpty() && selection instanceof ITextSelection;
		}
		return false;
	}

	private boolean supportFormatting(ServerCapabilities capabilities) {
		return Boolean.TRUE.equals(capabilities.getDocumentFormattingProvider())
		        || Boolean.TRUE.equals(capabilities.getDocumentRangeFormattingProvider());
	}

}
