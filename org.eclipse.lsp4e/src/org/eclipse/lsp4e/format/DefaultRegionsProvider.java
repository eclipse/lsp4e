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

import java.net.URI;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.osgi.service.component.annotations.Component;

@Component(property = { "service.ranking:Integer=0" })
public class DefaultRegionsProvider implements IFormatRegionsProvider {
	private final IFormatRegions notFormat = new NoFormat();

	@Override
	public boolean isEnabledFor(URI uri) {
		return true; //The default provider can be used for all languages.
	}

	@Override
	public IRegion[] getFormattingRegions(IDocument document) {
		//TODO: return regions provider depending on LSP4E preference e.g. NO_FORMAT, EDITED_LINES, ALL_LINES
		return notFormat.getFormattingRegions(document);
	}
}

