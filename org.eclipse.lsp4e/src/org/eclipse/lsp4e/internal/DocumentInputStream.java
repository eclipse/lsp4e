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

import org.eclipse.jface.text.IDocument;

public final class DocumentInputStream extends CharsInputStream {

	public DocumentInputStream(final IDocument doc) {
		super(doc::getChar, doc::getLength, DocumentUtil.getCharset(doc));
	}
}
