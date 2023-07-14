/*******************************************************************************
 * Copyright (c) 2023 Bachmann electronic GmbH and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * Gesa Hentschke (Bachmann electronic GmbH) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.typeHierarchy;

import java.util.Optional;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.callhierarchy.CallHierarchyCommandHandler;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * Command handler for the LSP type hierarchy view.
 */
public class TypeHierarchyViewHandler extends CallHierarchyCommandHandler {

	@Override
	public Object execute(final ExecutionEvent event) throws ExecutionException {
		if (HandlerUtil.getActiveEditor(event) instanceof ITextEditor editor) {
			IDocument document = LSPEclipseUtils.getDocument(editor);
			if (document == null) {
				return null;
			}
			if (editor.getSelectionProvider().getSelection() instanceof ITextSelection sel) {
				int offset = sel.getOffset();
				Optional.ofNullable(getActivePage()).map(p -> {
					try {
						return p.showView(TypeHierarchyView.ID);
					} catch (PartInitException e) {
						LanguageServerPlugin.logError("Error while opening the Type Hierarchy view", e); //$NON-NLS-1$
					}
					return Optional.empty();
				}).filter(view -> view instanceof TypeHierarchyView).ifPresent(thv -> ((TypeHierarchyView) thv).initialize(document, offset));
			}
		}
		return null;
	}

	@Override
	public void setEnabled(Object evaluationContext) {
		setEnabled(ServerCapabilities::getTypeHierarchyProvider, this::hasSelection);
	}
}
