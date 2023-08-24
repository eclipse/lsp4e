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
import org.eclipse.jface.text.Region;
import org.osgi.service.component.annotations.Component;

@Component(property = { "service.ranking:Integer=0" })
public class DefaultFormatOnSave implements IFormatOnSave {

	@Override
	public boolean isEnabledFor(IDocument document) {
		return false;
	}

	/**
	 * Formats the full file
	 */
	@Override
	public IRegion[] getFormattingRegions(ITextFileBuffer buffer) {
		return new IRegion[] { new Region(0, buffer.getDocument().getLength()) };
	}

}
