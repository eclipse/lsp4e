/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.operations.documentLink;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.reconciler.IReconciler;
import org.eclipse.jface.text.reconciler.MonoReconciler;

/**
 * {@link IReconciler} implementation to display textDocument/documentLink as underlined.
 */
public class LSPDocumentLinkPresentationReconciler extends MonoReconciler {

	public LSPDocumentLinkPresentationReconciler() {
		super(new LSPDocumentLinkPresentationReconcilingStrategy(), false);
	}

	@Override
	public void install(ITextViewer textViewer) {
		super.install(textViewer);
		// no need to do that if https://bugs.eclipse.org/bugs/show_bug.cgi?id=521326 is accepted
		((LSPDocumentLinkPresentationReconcilingStrategy) getReconcilingStrategy(IDocument.DEFAULT_CONTENT_TYPE)).install(textViewer);
	}

	@Override
	public void uninstall() {
		super.uninstall();
		// no need to do that if https://bugs.eclipse.org/bugs/show_bug.cgi?id=521326 is accepted
		((LSPDocumentLinkPresentationReconcilingStrategy) getReconcilingStrategy(IDocument.DEFAULT_CONTENT_TYPE)).uninstall();
	}
}
