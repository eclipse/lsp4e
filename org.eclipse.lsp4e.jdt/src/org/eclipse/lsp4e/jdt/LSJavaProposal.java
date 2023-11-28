/*******************************************************************************
 * Copyright (c) 2022, 2023 VMware Inc. and others.
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

import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension2;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.lsp4e.operations.completion.LSCompletionProposal;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

@SuppressWarnings("restriction")
class LSJavaProposal implements IJavaCompletionProposal, ICompletionProposalExtension2 {
	
	private ICompletionProposal delegate;
	private int relevance;

	public LSJavaProposal(ICompletionProposal delegate,  int relevance) {
		this.delegate = delegate;
		this.relevance = relevance;
	}

	@Override
	public void apply(IDocument document) {
		delegate.apply(document);	
	}

	@Override
	public String getAdditionalProposalInfo() {
		return delegate.getAdditionalProposalInfo();
	}

	@Override
	public IContextInformation getContextInformation() {
		return delegate.getContextInformation();
	}

	@Override
	public String getDisplayString() {
		return delegate.getDisplayString();
	}

	@Override
	public Image getImage() {
		return delegate.getImage();
	}

	@Override
	public Point getSelection(IDocument document) {
		return delegate.getSelection(document);
	}

	@Override
	public int getRelevance() {
		return relevance;
	}

	@Override
	public void apply(ITextViewer viewer, char trigger, int stateMask, int offset) {
		if (delegate instanceof LSCompletionProposal) {
			((LSCompletionProposal) delegate).apply(viewer, trigger, stateMask, offset);
		}
	}

	@Override
	public void selected(ITextViewer viewer, boolean smartToggle) {
		if (delegate instanceof LSCompletionProposal) {
			((LSCompletionProposal) delegate).selected(viewer, smartToggle);
		}
	}

	@Override
	public void unselected(ITextViewer viewer) {
		if (delegate instanceof LSCompletionProposal) {
			((LSCompletionProposal) delegate).unselected(viewer);
		}
	}

	@Override
	public boolean validate(IDocument document, int offset, DocumentEvent event) {
		if (delegate instanceof LSCompletionProposal) {
			return ((LSCompletionProposal) delegate).validate(document, offset, event);
		}
		return false;
	}
	
}
