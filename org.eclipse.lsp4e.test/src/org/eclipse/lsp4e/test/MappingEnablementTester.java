/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Rastislav Wagner (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test;

import org.eclipse.core.expressions.PropertyTester;

public class MappingEnablementTester extends PropertyTester {

	public static boolean enabled = false;

	@Override
	public boolean test(Object receiver, String property, Object[] args, Object expectedValue) {
		return enabled;
	}

}
