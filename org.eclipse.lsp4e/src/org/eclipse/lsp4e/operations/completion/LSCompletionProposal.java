/*******************************************************************************
 * Copyright (c) 2016, 2017 Red Hat Inc. and others.
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

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension2;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.swt.SWT;

public class LSCompletionProposal extends LSIncompleteCompletionProposal
		implements ICompletionProposalExtension, ICompletionProposalExtension2 {

	public LSCompletionProposal(IDocument document, int offset, @NonNull CompletionItem item,
			LanguageServer languageServer) {
		super(document, offset, item, languageServer);
	}

	@Override
	public boolean isValidFor(IDocument document, int offset) {
		return validate(document, offset, null);
	}

	@Override
	public void selected(ITextViewer viewer, boolean smartToggle) {
		this.viewer = viewer;
	}

	@Override
	public void unselected(ITextViewer viewer) {
	}

	@Override
	public boolean validate(IDocument document, int offset, DocumentEvent event) {
		if (item.getLabel() == null || item.getLabel().isEmpty()) {
			return false;
		}
		if (offset < this.bestOffset) {
			return false;
		}
		try {
			String documentFilter = getDocumentFilter(offset);
			if (!documentFilter.isEmpty()) {
				return CompletionProposalTools.isSubstringFoundOrderedInString(documentFilter, getFilterString());
			} else if (item.getTextEdit() != null) {
				if(item.getTextEdit().isLeft()) {
					return offset == LSPEclipseUtils.toOffset(item.getTextEdit().getLeft().getRange().getStart(), document);
				} else {
					return offset == LSPEclipseUtils.toOffset(item.getTextEdit().getRight().getInsert().getStart(), document);
				}
			}
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
		}
		return true;
	}

	@Override
	public void apply(ITextViewer viewer, char trigger, int stateMask, int offset) {
		this.viewer = viewer;
		apply(viewer.getDocument(), trigger, stateMask, offset);
	}

	@Override
	public void apply(IDocument document, char trigger, int offset) {
		apply(document, trigger, 0, offset);
	}

	@Override
	public void apply(IDocument document) {
		apply(document, Character.MIN_VALUE, 0, this.bestOffset);
	}

	@Override
	public char[] getTriggerCharacters() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getContextInformationPosition() {
		return SWT.RIGHT;
	}

}