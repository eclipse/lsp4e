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
 *******************************************************************************/
package org.eclipse.lsp4e.operations.format;

import java.util.Collection;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.lsp4e.ui.UI;
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
		if (part instanceof MultiPageEditorPart) {
			Object selectedPage = ((MultiPageEditorPart)part).getSelectedPage();
			if (selectedPage instanceof IEditorPart) {
				part = (IEditorPart)selectedPage;
			}
		}

		if (part instanceof ITextEditor) {
			ITextEditor textEditor = (ITextEditor) part;
			final IDocument document = LSPEclipseUtils.getDocument(textEditor);
			final Shell shell = textEditor.getSite().getShell();
			ISelection selection = HandlerUtil.getCurrentSelection(event);
			if (document != null && selection instanceof ITextSelection) {
				formatter.requestFormatting(document, (ITextSelection) selection).thenAcceptAsync(
						edits -> shell.getDisplay().asyncExec(() -> formatter.applyEdits(document, edits)));
			}
		}
		return null;
	}

	@Override
	public boolean isEnabled() {
		IWorkbenchPart part = UI.getActivePart();
		if (part instanceof MultiPageEditorPart) {
			Object selectedPage = ((MultiPageEditorPart)part).getSelectedPage();
			if (selectedPage instanceof IWorkbenchPart) {
				part = (IWorkbenchPart)selectedPage;
			}
		}

		if (part instanceof ITextEditor) {
			Collection<LSPDocumentInfo> infos = LanguageServiceAccessor.getLSPDocumentInfosFor(
					LSPEclipseUtils.getDocument((ITextEditor) part),
					LSPFormatter::supportFormatting);
			ISelection selection = ((ITextEditor) part).getSelectionProvider().getSelection();
			return !infos.isEmpty() && !selection.isEmpty() && selection instanceof ITextSelection;
		}
		return false;
	}

}
