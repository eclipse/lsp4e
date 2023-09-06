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
import org.eclipse.jface.text.IRegion;

/**
 * Can be implemented by clients as OSGi service
 * to provide editor specific formatting regions for format-on-save feature.
 */
public interface IFormatRegionsProvider {

	/**
	 * Get the formatting regions
	 * @param document
	 * @return region to be formatted or null if document should not be formatted on save.
	 */
	IRegion[] getFormattingRegions(IDocument document);

}