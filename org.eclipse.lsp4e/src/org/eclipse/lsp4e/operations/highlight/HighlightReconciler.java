/*******************************************************************************
 * Copyright (c) 2017 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michal Niewrzal‚ (Rogue Wave Software Inc.) - initial implementation
 *  Angelo Zerr <angelo.zerr@gmail.com> - fix Bug 521020
 *******************************************************************************/
package org.eclipse.lsp4e.operations.highlight;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.reconciler.IReconciler;
import org.eclipse.jface.text.reconciler.MonoReconciler;

/**
 * {@link IReconciler} implementation to Highlight Symbol (mark occurrences like).
 */
public class HighlightReconciler extends MonoReconciler {

	public HighlightReconciler() {
		super(new HighlightReconcilingStrategy(), false);
	}

	@Override
	public void install(ITextViewer textViewer) {
		super.install(textViewer);
		// no need to do that if https://bugs.eclipse.org/bugs/show_bug.cgi?id=521326 is accepted
		((HighlightReconcilingStrategy) getReconcilingStrategy(IDocument.DEFAULT_CONTENT_TYPE)).install(textViewer);
	}

	@Override
	public void uninstall() {
		super.uninstall();
		// no need to do that if https://bugs.eclipse.org/bugs/show_bug.cgi?id=521326 is accepted
		((HighlightReconcilingStrategy) getReconcilingStrategy(IDocument.DEFAULT_CONTENT_TYPE)).uninstall();
	}
}
