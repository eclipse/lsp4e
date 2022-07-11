/*******************************************************************************
 * Copyright (c) 2022 Cocotec Ltd and others.
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
 * Groups a result from the language server with the modification stamp that existed on the document
 * at the time the request to the server was made
 * @param <T>
 */
public class VersionedResult<T> {

	private final long documentModificationStamp;

	private final T t;

	public VersionedResult(final T result, final long documentModificationStamp) {
		this.t = result;
		this.documentModificationStamp = documentModificationStamp;
	}

	public T get() {
		return this.t;
	}

	/**
	 * Modification stamp. See org.eclipse.jface.text.IDocumentExtension4.getModificationStamp()
	 *
	 * Note that this is closer to a hash than a timestamp, since comparing stamps for ordering rather than
	 * equality is not guaranteed to be meaningful.
	 * @return
	 */
	public long getStamp() {
		return this.documentModificationStamp;
	}
}
