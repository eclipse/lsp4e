/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/

package org.eclipse.lsp4e;

import java.io.IOException;
import java.io.InputStream;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;

final class DocumentInputStream extends InputStream {
	private int index = 0;
	private final IDocument document;

	DocumentInputStream(IDocument document) {
		this.document = document;
	}

	@Override
	public int read() throws IOException {
		if (index < document.getLength()) {
			try {
				char res = document.getChar(index);
				index++;
				return res;
			} catch (BadLocationException e) {
				throw new IOException(e);
			}
		}
		return -1;
	}

}