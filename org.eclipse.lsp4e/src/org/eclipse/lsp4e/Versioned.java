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

import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.internal.DocumentUtil;

/**
 * Bundles together a result from a language server with the document version at the time it was run.
 * Supports optimistic locking.
 *
 * @param <T>
 */
public class Versioned<T> {
	protected final IDocument document;
	public final long sourceDocumentVersion;
	public final T data;

	/**
	 * Constructs a new Versioned for the given document, and specify source version
	 */
	public Versioned(final IDocument document, long sourceDocumentVersion, final T data) {
		this.document = document;
		this.sourceDocumentVersion = sourceDocumentVersion;
		this.data = data;
	}

	/**
	 * Constructs a new Versioned for the given document, using its current modification
	 * stamp as source version.
	 */
	public Versioned(final IDocument document, final T data) {
		this(document, DocumentUtil.getDocumentModificationStamp(document), data);
	}

}
