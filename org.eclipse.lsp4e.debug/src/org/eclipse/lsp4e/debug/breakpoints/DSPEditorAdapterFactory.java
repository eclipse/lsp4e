/*******************************************************************************
 * Copyright (c) 2017 Kichwa Coders Ltd. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.lsp4e.debug.breakpoints;

import org.eclipse.core.runtime.IAdapterFactory;
import org.eclipse.debug.ui.actions.IToggleBreakpointsTarget;
import org.eclipse.ui.editors.text.TextEditor;
import org.eclipse.ui.internal.genericeditor.ExtensionBasedTextEditor;

@SuppressWarnings("restriction")
public class DSPEditorAdapterFactory implements IAdapterFactory {

	@SuppressWarnings("unchecked")
	@Override
	public <T> T getAdapter(Object adaptableObject, Class<T> adapterType) {
		if (adapterType == IToggleBreakpointsTarget.class) {
			if (adaptableObject instanceof ExtensionBasedTextEditor || adaptableObject instanceof TextEditor) {
				return (T) new DSPBreakpointAdapter();
			}
		}
		return null;
	}

	@Override
	public Class<?>[] getAdapterList() {
		return new Class[] { IToggleBreakpointsTarget.class };
	}
}
