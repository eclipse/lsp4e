/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Lucia Jelinkova (Red Hat Inc.)  - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.utils;

import org.eclipse.lsp4e.tests.mock.MockConnectionProvider;

public class MockConnectionProviderWithException extends MockConnectionProvider {

	public MockConnectionProviderWithException() {
		throw new IllegalStateException("Testing error from constructor");
	}
}
