/*******************************************************************************
 * Copyright (c) 2017 Rogue Wave Software Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.highlight;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;

class HighlightsFinder extends Job {

	private List<LSPDocumentInfo> infos;
	private Position position;
	private List<DocumentHighlight> highlights;
	private CompletableFuture<List<? extends DocumentHighlight>> request;

	public HighlightsFinder(List<LSPDocumentInfo> infos) {
		super("Search for highlights..."); //$NON-NLS-1$
		this.infos = infos;
		this.highlights = new ArrayList<>();
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		this.highlights.clear();
		for (LSPDocumentInfo info : infos) {
			if (monitor.isCanceled()) {
				return Status.CANCEL_STATUS;
			}
			TextDocumentIdentifier identifier = new TextDocumentIdentifier(info.getFileUri().toString());
			TextDocumentPositionParams params = new TextDocumentPositionParams(identifier, position);
			try {
				request = info.getLanguageClient().getTextDocumentService().documentHighlight(params);
				List<? extends DocumentHighlight> result = request.get(1, TimeUnit.SECONDS);
				if (result != null) {
					this.highlights.addAll(result);
				}
			} catch (InterruptedException | ExecutionException | TimeoutException e) {
				LanguageServerPlugin.logError(e);
			}
		}
		return Status.OK_STATUS;
	}

	public List<DocumentHighlight> getHighlights() {
		return highlights;
	}

	public void setPosition(Position position) {
		this.position = position;
	}

	public void doCancel() {
		if (request != null) {
			try {
				request.cancel(true);
			} catch (CancellationException e) {
				// ignore
			}
		}
		cancel();
	}
}