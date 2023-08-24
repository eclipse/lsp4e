/*******************************************************************************
 * Copyright (c) 2023 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   See git history
 *******************************************************************************/
package org.eclipse.lsp4e;

import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;

public interface IFormatOnSave {

	/**
	 * Checks whether formatting shall be performed prior to file buffer saving.
	 * @param document
	 * @return true if document buffer shall be formatted prior saving
	 */
	boolean isEnabledFor(IDocument document);

	/**
	 * Get the regions to be formatted (e.g. full file, lines edited this session, lines edited vs. source control baseline).
	 * @param buffer the buffer to compare contents from
	 * @return regions to be formatted before saving.
	 */
	IRegion[] getFormattingRegions(ITextFileBuffer buffer);

}
