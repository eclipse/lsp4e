/*******************************************************************************
 * Copyright (c) 2024 Vegard IT GmbH and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Sebastian Thomschke (Vegard IT GmbH) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.utils;

import java.lang.System.Logger.Level;

import org.junit.rules.TestName;
import org.junit.runner.Description;

public class TestInfoRule extends TestName {
	private volatile Class<?> testClass;
	private volatile String displayName;

	@Override
	protected void starting(Description d) {
		super.starting(d);
		testClass = d.getTestClass();
		System.getLogger(testClass.getName()).log(Level.INFO, "Testing [" + getMethodName() + "()]...");
	}

	public String getClassName() {
		return testClass.getName();
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getSimpleClassName() {
		return testClass.getSimpleName();
	}

	@Override
	public String toString() {
		return getClassName() + "#" + getMethodName();
	}
}
