/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.operations.typeHierarchy;

import java.util.concurrent.CompletableFuture;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.internal.LSPDocumentAbstractHandler;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.ui.texteditor.ITextEditor;

public class TypeHierarchyHandler extends LSPDocumentAbstractHandler {

	@Override
	protected void execute(ExecutionEvent event, ITextEditor editor) {
		IDocument document = LSPEclipseUtils.getDocument(editor);
		LanguageServers.forDocument(document)
			.withCapability(ServerCapabilities::getTypeHierarchyProvider)
			.computeFirst((wrapper, ls) -> CompletableFuture.completedFuture(wrapper.serverDefinition))
			.thenAcceptAsync(definition -> definition.ifPresent(def -> new TypeHierarchyDialog(editor.getSite().getShell(), (ITextSelection)editor.getSelectionProvider().getSelection(), document, def).open()), editor.getSite().getShell().getDisplay());
	}

	@Override
	public void setEnabled(@Nullable Object evaluationContext) {
		setEnabled(ServerCapabilities::getTypeDefinitionProvider, editor -> true);
	}

}
