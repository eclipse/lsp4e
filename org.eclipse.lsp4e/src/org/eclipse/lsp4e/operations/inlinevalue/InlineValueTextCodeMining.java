/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.operations.inlinevalue;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.codemining.LineEndCodeMining;
import org.eclipse.lsp4j.InlineValueText;

public class InlineValueTextCodeMining extends LineEndCodeMining {
	public InlineValueTextCodeMining(InlineValueText inlineValueText, @NonNull IDocument document, InlineValueProvider provider) throws BadLocationException {
		super(document, inlineValueText.getRange().getStart().getLine(), provider);
		setLabel(inlineValueText.getText());
	}
}
