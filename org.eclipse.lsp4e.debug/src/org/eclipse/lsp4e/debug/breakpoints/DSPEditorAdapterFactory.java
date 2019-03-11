/*******************************************************************************
 * Copyright (c) 2017 Kichwa Coders Ltd. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
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
		if (adapterType == IToggleBreakpointsTarget.class
				&& (adaptableObject instanceof ExtensionBasedTextEditor || adaptableObject instanceof TextEditor)) {
			return (T) new DSPBreakpointAdapter();
		}
		return null;
	}

	@Override
	public Class<?>[] getAdapterList() {
		return new Class[] { IToggleBreakpointsTarget.class };
	}
}
