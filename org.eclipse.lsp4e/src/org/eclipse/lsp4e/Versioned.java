/*******************************************************************************
 * Copyright (c) 2022-3 Cocotec Ltd and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Ahmed Hussain (Cocotec Ltd) - initial implementation
 *
 *******************************************************************************/
package org.eclipse.lsp4e;

/**
 * Bundles together a result from a language server with the document version at the time it was run.
 * Supports optimistic locking.
 *
 * @param <T>
 */
public class Versioned<T> {
	private final long version;

	private final T data;

	public Versioned(final long version, final T data) {
		this.version = version;
		this.data = data;
	}

	public T get() {
		return data;
	}

	public long getVersion() {
		return version;
	}
}