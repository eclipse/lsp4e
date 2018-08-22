/**
 *  Copyright (c) 2018 Angelo ZERR.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *  Angelo Zerr <angelo.zerr@gmail.com> - Add support for 'textDocument/foldingRange' - Bug 537706
 */
package org.eclipse.lsp4e.operations.folding;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.reconciler.MonoReconciler;
import org.eclipse.jface.text.source.projection.ProjectionViewer;

/**
 * LSP folding reconciler.
 *
 */
public class LSPFoldingReconciler extends MonoReconciler {

	public LSPFoldingReconciler() {
		super(new LSPFoldingReconcilingStrategy(), false);
	}

	@Override
	public void install(ITextViewer textViewer) {
		super.install(textViewer);
		if (textViewer instanceof ProjectionViewer) {
			((LSPFoldingReconcilingStrategy) getReconcilingStrategy(IDocument.DEFAULT_CONTENT_TYPE))
					.install((ProjectionViewer) textViewer);
		}
	}

	@Override
	public void uninstall() {
		super.uninstall();
		((LSPFoldingReconcilingStrategy) getReconcilingStrategy(IDocument.DEFAULT_CONTENT_TYPE)).uninstall();
	}

}
