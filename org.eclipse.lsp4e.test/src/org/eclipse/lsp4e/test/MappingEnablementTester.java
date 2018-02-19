/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
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
