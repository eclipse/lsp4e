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
import org.osgi.service.component.annotations.Component;

@Component(property = { "service.ranking:Integer=0" })
public class DefaultFormatOnSave implements IFormatOnSave {

	@Override
	public FormatStrategy getFormatStrategy(IDocument document) {
		//TODO: return format strategy depending on LSP4E preferences.
		// Currently disabled
		return FormatStrategy.NO_FORMAT;
	}

}

