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
 *******************************************************************************/

package org.eclipse.lsp4e.callhierarchy;

import org.eclipse.jface.text.IDocument;

/**
 * Simple type representing the input to the call hierarchy view.
 */
public class CallHierarchyViewInput {
    private final IDocument document;
    private final int offset;

    /**
     * Creates a new instance of {@link CallHierarchyViewInput}.
     *
     * @param document
     *            the document containing the selection to start a call hierarchy
     *            from.
     * @param offset
     *            the offset into the document to select as the root of the
     *            hierarchy.
     */
    public CallHierarchyViewInput(final IDocument document, final int offset) {
        this.document = document;
        this.offset = offset;
    }

    /**
     * Get the document containing the selection to start a call hierarchy from.
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
