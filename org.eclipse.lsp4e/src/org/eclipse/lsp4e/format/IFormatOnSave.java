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
package org.eclipse.lsp4e.format;

import org.eclipse.jface.text.IDocument;

public interface IFormatOnSave {

	/**
	 * Returns the {@link FormatStrategy} for the given document.
	 * @param document
	 * @return FormatStrategy
	 */
	FormatStrategy getFormatStrategy(IDocument document);

}
