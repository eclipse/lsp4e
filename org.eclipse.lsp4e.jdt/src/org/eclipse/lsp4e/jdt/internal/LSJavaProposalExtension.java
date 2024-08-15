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
package org.eclipse.lsp4e.jdt.internal;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension;

class LSJavaProposalExtension extends LSJavaProposal implements ICompletionProposalExtension {

	public LSJavaProposalExtension(ICompletionProposal delegate) {
		super(delegate);
	}

	@Override
	public void apply(IDocument doc, char trigger, int offset) {
		if (delegate instanceof ICompletionProposalExtension proposalExt) {
			proposalExt.apply(doc, trigger, offset);
		}
	}

	@Override
	public int getContextInformationPosition() {
		if (delegate instanceof ICompletionProposalExtension proposalExt) {
			return proposalExt.getContextInformationPosition();
		}
		return -1;
	}

	@Override
	public char @Nullable [] getTriggerCharacters() {
		if (delegate instanceof ICompletionProposalExtension proposalExt) {
			return proposalExt.getTriggerCharacters();
		}
		return null;
	}

	@Override
	public boolean isValidFor(IDocument doc, int offset) {
		if (delegate instanceof ICompletionProposalExtension proposalExt) {
			return proposalExt.isValidFor(doc, offset);
		}
		return false;
	}

}
