/*******************************************************************************
 * Copyright (c) 2022 Avaloq Group AG (http://www.avaloq.com).
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Andrew Lamb (Avaloq Group AG) - Initial implementation
 *  Gesa Hentschke - made the class generic
 *******************************************************************************/

package org.eclipse.lsp4e.ui.views;

import org.eclipse.jface.text.IDocument;

/**
 * Simple type representing the input to a hierarchy view.
 */
public class HierarchyViewInput {
	private final IDocument document;
	private final int offset;

	/**
	 * Creates a new instance of {@link HierarchyViewInput}.
	 *
	 * @param document
	 *            the document containing the selection to start a hierarchy
	 *            from.
	 * @param offset
	 *            the offset into the document to select as the root of the
	 *            hierarchy.
	 */
	public HierarchyViewInput(final IDocument document, final int offset) {
		this.document = document;
		this.offset = offset;
	}

	/**
	 * Get the document containing the selection to start a hierarchy from.
	 *
	 * @return the document containing the selection to start a call hierarchy from.
	 */
	public IDocument getDocument() {
		return document;
	}

	/**
	 * Get the offset into the document to select as the root of the hierarchy.
	 *
	 * @return the offset into the document to select as the root of the hierarchy.
	 */
	public int getOffset() {
		return offset;
	}
}
