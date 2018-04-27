/*******************************************************************************
 * Copyright (c) 2017 Kichwa Coders Ltd. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.debug.sourcelookup;

import java.nio.file.Paths;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.sourcelookup.ISourceContainer;
import org.eclipse.debug.core.sourcelookup.ISourceContainerType;
import org.eclipse.debug.core.sourcelookup.containers.AbstractSourceContainer;

public class AbsolutePathSourceContainer extends AbstractSourceContainer implements ISourceContainer {

	@Override
	public Object[] findSourceElements(String name) throws CoreException {
		if (name != null) {
			if (Paths.get(name).isAbsolute()) {
				return new Object[] { name };
			}
		}
		return new Object[0];
	}

	@Override
	public String getName() {
		return "Absolute Path";
	}

	@Override
	public ISourceContainerType getType() {
		return null;
	}

}
