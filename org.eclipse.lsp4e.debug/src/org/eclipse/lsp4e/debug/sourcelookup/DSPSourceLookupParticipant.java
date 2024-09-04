/*******************************************************************************
 * Copyright (c) 2017 Kichwa Coders Ltd. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.debug.sourcelookup;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.sourcelookup.AbstractSourceLookupParticipant;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4e.debug.debugmodel.DSPStackFrame;

public class DSPSourceLookupParticipant extends AbstractSourceLookupParticipant {

	@Override
	public @Nullable String getSourceName(Object object) throws CoreException {
		if (object instanceof String string) {
			return string;
		}
		if (object instanceof DSPStackFrame stackFrame) {
			return stackFrame.getSourceName();
		}
		return null;
	}

}
