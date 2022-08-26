/*******************************************************************************
 * Copyright (c) 2017, 2018 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Martin Lippert (Pivotal Inc.) - bug 531167
 *******************************************************************************/
package org.eclipse.lsp4e;

import org.eclipse.core.expressions.PropertyTester;
import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.ui.IEditorInput;

public class HasLanguageServerPropertyTester extends PropertyTester {

	@Override
	public boolean test(Object receiver, String property, Object[] args, Object expectedValue) {
		if (receiver instanceof IFile file) {
			return LanguageServersRegistry.getInstance().canUseLanguageServer(file);
		} else if (receiver instanceof IEditorInput editorInput) {
			return LanguageServersRegistry.getInstance().canUseLanguageServer(editorInput);
		} else if (receiver instanceof IDocument document) {
			return LanguageServersRegistry.getInstance().canUseLanguageServer(document);
		} else if (receiver instanceof ITextViewer viewer) {
			IDocument document = viewer.getDocument();
			if (document != null) {
				return LanguageServersRegistry.getInstance().canUseLanguageServer(document);
			}
		}
		return false;
	}

}
