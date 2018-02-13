/*******************************************************************************
 * Copyright (c) 2018 Pivotal Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Martin Lippert (Pivotal Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test;

import java.util.function.Supplier;

import org.eclipse.jface.text.tests.util.DisplayHelper;

/**
 * display helper with supplier function, for easy use with lambda expression
 */
public class LSDisplayHelper extends DisplayHelper {

	private Supplier<Boolean> tester;

	public LSDisplayHelper(Supplier<Boolean> tester) {
		this.tester = tester;
	}

	@Override
	protected boolean condition() {
		return this.tester.get();
	}

}
