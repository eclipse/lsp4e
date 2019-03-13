/*******************************************************************************
 * Copyright (c) 2016, 2019 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Mickael Istria (Red Hat Inc.) - initial implementation
 *   Michał Niewrzał (Rogue Wave Software Inc.)
 *   Lucas Bullen (Red Hat Inc.) - Refactored for incomplete completion lists
 *******************************************************************************/
package org.eclipse.lsp4e.operations.completion;

import java.util.Comparator;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.lsp4e.LanguageServerPlugin;

final class LSCompletionProposalComparator implements Comparator<LSCompletionProposal> {
	@Override
	public int compare(LSCompletionProposal o1, LSCompletionProposal o2) {
		try {
			int docFilterLen1 = o1.getDocumentFilter().length();
			int docFilterLen2 = o2.getDocumentFilter().length();
			if (docFilterLen1 > docFilterLen2) {
				return -1;
			} else if (docFilterLen1 < docFilterLen2) {
				return +1;
			}
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
		}
		if (o1.getRankCategory() < o2.getRankCategory()) {
			return -1;
		} else if (o1.getRankCategory() > o2.getRankCategory()) {
			return +1;
		}
		if ((o1.getRankCategory() < 5 && o2.getRankCategory() < 5)
				&& (!(o1.getRankScore() == -1 && o2.getRankScore() == -1))) {
			if (o2.getRankScore() == -1 || o1.getRankScore() < o2.getRankScore()) {
				return -1;
			} else if (o1.getRankScore() == -1 || o1.getRankScore() > o2.getRankScore()) {
				return +1;
			}
		}
		String c1 = o1.getSortText();
		String c2 = o2.getSortText();
		if (c1 == null) {
			return -1;
		}
		return c1.compareToIgnoreCase(c2);
	}
}