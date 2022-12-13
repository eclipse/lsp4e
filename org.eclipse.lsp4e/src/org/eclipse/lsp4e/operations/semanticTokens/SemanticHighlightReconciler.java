/*******************************************************************************
 * Copyright (c) 2022 Avaloq Group AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.operations.semanticTokens;

import java.util.Objects;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.reconciler.MonoReconciler;

public class SemanticHighlightReconciler extends MonoReconciler {

	public SemanticHighlightReconciler() {
		super(new SemanticHighlightReconcilerStrategy(), false);
	}

	@Override
	public void install(final ITextViewer textViewer) {
		super.install(textViewer);
		// no need to do that if https://bugs.eclipse.org/bugs/show_bug.cgi?id=521326 is
		// accepted
		Objects.requireNonNull(textViewer);
		((SemanticHighlightReconcilerStrategy) getReconcilingStrategy(IDocument.DEFAULT_CONTENT_TYPE))
				.install(textViewer);
	}

	@Override
	public void uninstall() {
		super.uninstall();
		// no need to do that if https://bugs.eclipse.org/bugs/show_bug.cgi?id=521326 is
		// accepted
		((SemanticHighlightReconcilerStrategy) getReconcilingStrategy(IDocument.DEFAULT_CONTENT_TYPE)).uninstall();
	}

}