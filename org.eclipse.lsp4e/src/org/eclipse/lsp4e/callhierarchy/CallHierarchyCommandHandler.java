/*******************************************************************************
 * Copyright (c) 2022 Avaloq Group AG (http://www.avaloq.com).
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Andrew Lamb (Avaloq Group AG) - Initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.callhierarchy;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.internal.LSPDocumentAbstractHandler;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * Command handler for the LSP call hierarchy view.
 */
public class CallHierarchyCommandHandler extends LSPDocumentAbstractHandler {

	@Override
	protected void execute(ExecutionEvent event, ITextEditor editor) {
		IDocument document = LSPEclipseUtils.getDocument(editor);
		if (document == null) {
			return;
		}
		if (editor.getSelectionProvider().getSelection() instanceof ITextSelection sel) {
			int offset = sel.getOffset();

			try {
				CallHierarchyView theView = UI.showView(CallHierarchyView.ID);
				theView.initialize(document, offset);
			} catch (PartInitException e) {
				LanguageServerPlugin.logError("Error while opening the Call Hierarchy view", e); //$NON-NLS-1$
			}
		}
	}

	@Override
	public void setEnabled(Object evaluationContext) {
		setEnabled(ServerCapabilities::getCallHierarchyProvider, this::hasSelection);
	}
}
