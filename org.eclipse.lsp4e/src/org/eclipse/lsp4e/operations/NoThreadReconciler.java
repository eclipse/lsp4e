/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Victor Rubezhny (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations;

import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.ITextInputListener;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.reconciler.IReconciler;
import org.eclipse.jface.text.reconciler.IReconcilingStrategy;
import org.eclipse.jface.text.reconciler.IReconcilingStrategyExtension;

public class NoThreadReconciler implements IReconciler {

	/** The text viewer's document */
	private IDocument document;
	/** The text viewer */
	private ITextViewer textViewer;

	private IReconcilingStrategy strategy;
	private Listener fListener;

	public NoThreadReconciler(IReconcilingStrategy strategy) {
		this.strategy = strategy;
	}

	/**
	 * Internal document listener and text input listener.
	 */
	class Listener implements IDocumentListener, ITextInputListener {

		/*
		 * @see IDocumentListener#documentAboutToBeChanged(DocumentEvent)
		 */
		@Override
		public void documentAboutToBeChanged(DocumentEvent e) {
		}

		/*
		 * @see IDocumentListener#documentChanged(DocumentEvent)
		 */
		@Override
		public void documentChanged(DocumentEvent e) {

		}

		/*
		 * @see ITextInputListener#inputDocumentAboutToBeChanged(IDocument, IDocument)
		 */
		@Override
		public void inputDocumentAboutToBeChanged(IDocument oldInput, IDocument newInput) {

			if (oldInput == document) {

				if (document != null)
					document.removeDocumentListener(this);
				document = null;
			}
		}

		/*
		 * @see ITextInputListener#inputDocumentChanged(IDocument, IDocument)
		 */
		@Override
		public void inputDocumentChanged(IDocument oldInput, IDocument newInput) {

			document = newInput;
			if (document == null)
				return;
			reconcilerDocumentChanged(document);

			document.addDocumentListener(this);
			initialProcess();
		}

	}

	@Override
	public void install(ITextViewer textViewer) {
		this.textViewer = textViewer;
		fListener = new Listener();
		textViewer.addTextInputListener(fListener);
	}

	@Override
	public void uninstall() {
		if (fListener != null) {
			textViewer.removeTextInputListener(fListener);
			if (document != null)
				document.removeDocumentListener(fListener);
			fListener = null;
		}
		this.textViewer = null;
	}

	protected void reconcilerDocumentChanged(IDocument document) {
		strategy.setDocument(document);
	}

	@Override
	public IReconcilingStrategy getReconcilingStrategy(String contentType) {
		return strategy;
	}

	protected void initialProcess() {
		if (strategy instanceof IReconcilingStrategyExtension) {
			IReconcilingStrategyExtension extension = (IReconcilingStrategyExtension) strategy;
			extension.initialReconcile();
		}
	}
}
