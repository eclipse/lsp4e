/*******************************************************************************
 * Copyright (c) 2018 Pivotal Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Martin Lippert (Pivotal Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test;

import java.util.function.Supplier;

import org.eclipse.ui.tests.harness.util.DisplayHelper;

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
