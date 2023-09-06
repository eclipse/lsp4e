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
import org.osgi.service.component.annotations.Component;

@Component(property = { "service.ranking:Integer=0" })
public class DefaultRegionsProvider implements IFormatRegionsProvider {

	@Override
	public IRegion[] getFormattingRegions(IDocument document) {
		//TODO: return regions provider depending on LSP4E preference e.g. NO_FORMAT, EDITED_LINES, ALL_LINES
		return new EditedLinesRegionsProvider().getFormattingRegions(document);
	}
}

