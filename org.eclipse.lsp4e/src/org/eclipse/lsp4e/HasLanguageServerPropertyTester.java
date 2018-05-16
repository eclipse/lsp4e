/*******************************************************************************
 * Copyright (c) 2017, 2018 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Martin Lippert (Pivotal Inc.) - bug 531167
 *******************************************************************************/
package org.eclipse.lsp4e;

import org.eclipse.core.expressions.PropertyTester;
import org.eclipse.core.resources.IFile;
import org.eclipse.ui.IEditorInput;

public class HasLanguageServerPropertyTester extends PropertyTester {

	@Override
	public boolean test(Object receiver, String property, Object[] args, Object expectedValue) {
		if (receiver instanceof IFile) {
			return LanguageServersRegistry.getInstance().canUseLanguageServer((IFile) receiver);
		} else if (receiver instanceof IEditorInput) {
			return LanguageServersRegistry.getInstance().canUseLanguageServer((IEditorInput) receiver);
		}
		return false;
	}

}
