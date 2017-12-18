/*******************************************************************************
 * Copyright (c) 2017 Kichwa Coders Ltd. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
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
