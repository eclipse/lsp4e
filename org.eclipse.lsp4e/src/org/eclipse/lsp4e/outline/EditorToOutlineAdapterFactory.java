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
 *  Lucas Bullen (Red Hat Inc.) - Bug 520053 - Clicking nodes in the 'Outline' should navigate
 *******************************************************************************/
package org.eclipse.lsp4e.outline;

import java.util.Collection;

import org.eclipse.core.runtime.IAdapterFactory;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServersRegistry;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

public class EditorToOutlineAdapterFactory implements IAdapterFactory {

	@Override
	public <T> T getAdapter(Object adaptableObject, Class<T> adapterType) {
		if (adapterType == IContentOutlinePage.class && adaptableObject instanceof IEditorPart &&
				LanguageServersRegistry.getInstance().canUseLanguageServer(((IEditorPart) adaptableObject).getEditorInput())) {
			IDocument document = LSPEclipseUtils.getDocument(((IEditorPart)adaptableObject).getEditorInput());
			if (document != null) {
				Collection<LSPDocumentInfo> info = LanguageServiceAccessor.getLSPDocumentInfosFor(
						document, capabilities -> Boolean.TRUE.equals(capabilities.getDocumentSymbolProvider()));
				if (!info.isEmpty()) {
					ITextEditor textEditor = null;
					if(adaptableObject instanceof ITextEditor) {
						textEditor = (ITextEditor)adaptableObject;
					}
					return (T)new CNFOutlinePage(info.iterator().next(), textEditor);
				}
			}
		}
		return null;
	}

	@Override
	public Class<?>[] getAdapterList() {
		return new Class<?>[] {
			IContentOutlinePage.class
		};
	}

}
