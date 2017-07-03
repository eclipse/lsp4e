/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.outline;

import java.util.Collection;

import org.eclipse.core.runtime.IAdapterFactory;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

public class EditorToOutlineAdapterFactory implements IAdapterFactory {

	@Override
	public <T> T getAdapter(Object adaptableObject, Class<T> adapterType) {
		if (adapterType == IContentOutlinePage.class && adaptableObject instanceof ITextEditor) {
			Collection<LSPDocumentInfo> info = LanguageServiceAccessor.getLSPDocumentInfosFor(
				LSPEclipseUtils.getDocument((ITextEditor) adaptableObject),
				capabilities -> Boolean.TRUE.equals(capabilities.getDocumentSymbolProvider()));
			if (!info.isEmpty()) {
				return (T)new CNFOutinePage(info.iterator().next());
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
