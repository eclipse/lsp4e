/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e;

import java.io.IOException;

import org.eclipse.core.expressions.PropertyTester;
import org.eclipse.core.resources.IFile;
import org.eclipse.ui.part.FileEditorInput;

public class HasLanguageServerPropertyTester extends PropertyTester {

	@Override
	public boolean test(Object receiver, String property, Object[] args, Object expectedValue) {
		IFile file = null;
		if (receiver instanceof IFile) {
			file = (IFile)receiver;
		} else if (receiver instanceof FileEditorInput) {
			file = ((FileEditorInput)receiver).getFile();
		}
		if (file != null) {
			try {
				return !LanguageServiceAccessor.getInitializedLanguageServers(file, capabilities -> true).isEmpty();
			} catch (IOException e) {
				LanguageServerPlugin.logError(e);
			}
		}
		return false;
	}

}
