/*******************************************************************************
 * Copyright (c) 2024 Sebastian Thomschke and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * Sebastian Thomschke - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.internal;

public class ArrayUtil {

	/** reusable empty byte array */
	public static final byte[] NO_BYTES = new byte[0];

	/** reusable empty char array */
	public static final char[] NO_CHARS = new char[0];

	/** reusable empty {@link Object} array */
	public static final Object[] NO_OBJECTS = new Object[0];

	/** reusable empty {@link String} array */
	public static final String[] NO_STRINGS = new String[0];

	private ArrayUtil() {
	}
}
