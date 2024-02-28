/*******************************************************************************
 * Copyright (c) 2024 Broadcom Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  - Alex Boyko (VMware Inc.) - Initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.jdt;

import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension2;

class LSJavaProposalExtension2 extends LSJavaProposal implements ICompletionProposalExtension2 {

	public LSJavaProposalExtension2(ICompletionProposal delegate, int relevance) {
		super(delegate, relevance);
	}

	@Override
	public void apply(ITextViewer viewer, char trigger, int stateMask, int offset) {
		if (delegate instanceof ICompletionProposalExtension2) {
			((ICompletionProposalExtension2) delegate).apply(viewer, trigger, stateMask, offset);
		}
	}

	@Override
	public void selected(ITextViewer viewer, boolean smartToggle) {
		if (delegate instanceof ICompletionProposalExtension2) {
			((ICompletionProposalExtension2) delegate).selected(viewer, smartToggle);
		}
	}

	@Override
	public void unselected(ITextViewer viewer) {
		if (delegate instanceof ICompletionProposalExtension2) {
			((ICompletionProposalExtension2) delegate).unselected(viewer);
		}
	}

	@Override
	public boolean validate(IDocument document, int offset, DocumentEvent event) {
		if (delegate instanceof ICompletionProposalExtension2) {
			return ((ICompletionProposalExtension2) delegate).validate(document, offset, event);
		}
		return false;
	}
	
}
