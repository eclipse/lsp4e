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
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

class LSJavaProposal implements IJavaCompletionProposal {
	
	protected ICompletionProposal delegate;
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
	
}
