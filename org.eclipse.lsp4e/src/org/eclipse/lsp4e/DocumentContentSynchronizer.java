/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Michał Niewrzał (Rogue Wave Software Inc.)
 *******************************************************************************/
package org.eclipse.lsp4e;

import java.io.File;
import java.util.Collections;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.services.LanguageServer;

final class DocumentContentSynchronizer implements IDocumentListener {

	private final LanguageServer languageServer;
	private final String fileUri;
	private final TextDocumentSyncKind syncKind;
	private int version = 0;
	private final DidChangeTextDocumentParams changeParams;
	private long modificationStamp;
	private @NonNull IDocument document;

	public DocumentContentSynchronizer(@NonNull LanguageServer languageServer, @NonNull IDocument document,
			@NonNull IPath filePath, TextDocumentSyncKind syncKind) {
		this.languageServer = languageServer;
		File file = filePath.toFile();
		this.fileUri = file.toURI().toString();
		this.modificationStamp = file.lastModified();
		this.syncKind = syncKind != null ? syncKind : TextDocumentSyncKind.Full;

		// Initialize change params to avoid it during text typing
		this.changeParams = new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(), null,
				Collections.singletonList(new TextDocumentContentChangeEvent()));
		this.changeParams.getTextDocument().setUri(fileUri);

		this.document = document;
		// add a document buffer
		TextDocumentItem textDocument = new TextDocumentItem();
		textDocument.setUri(fileUri);
		textDocument.setText(document.get());
		textDocument.setLanguageId(filePath.getFileExtension());
		textDocument.setVersion(++version);
		this.languageServer.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(textDocument, null));
	}

	@Override
	public void documentChanged(DocumentEvent event) {
		checkEvent(event);
		if (syncKind == TextDocumentSyncKind.Full) {
			updateChangeEvent(event);
		}
		changeParams.getTextDocument().setVersion(++version);
		languageServer.getTextDocumentService().didChange(changeParams);
	}

	@Override
	public void documentAboutToBeChanged(DocumentEvent event) {
		checkEvent(event);
		if (syncKind == TextDocumentSyncKind.Incremental) {
			// this really needs to happen before event gets actually
			// applied, to properly compute positions
			updateChangeEvent(event);
		}
	}

	/**
	 * Convert Eclipse {@link DocumentEvent} to LS according {@link TextDocumentSyncKind}.
	 * {@link TextDocumentContentChangeEventImpl}.
	 *
	 * @param event
	 *            Eclipse {@link DocumentEvent}
	 * @return true if change event is ready to be sent
	 */
	private boolean updateChangeEvent(DocumentEvent event) {
		IDocument document = event.getDocument();
		TextDocumentContentChangeEvent changeEvent = null;
		TextDocumentSyncKind syncKind = getTextDocumentSyncKind();
		switch (syncKind) {
		case None:
			return false;
		case Full:
			changeParams.getContentChanges().get(0).setText(event.getDocument().get());
			break;
		case Incremental:
			changeEvent = changeParams.getContentChanges().get(0);
			String newText = event.getText();
			int offset = event.getOffset();
			int length = event.getLength();
			try {
				// try to convert the Eclipse start/end offset to LS range.
				Range range = new Range(LSPEclipseUtils.toPosition(offset, document),
						LSPEclipseUtils.toPosition(offset + length, document));
				changeEvent.setRange(range);
				changeEvent.setText(newText);
				changeEvent.setRangeLength(length);
			} catch (BadLocationException e) {
				// error while conversion (should never occur)
				// set the full document text as changes.
				changeEvent.setText(document.get());
			}
			break;
		}
		return true;
	}

	public void documentSaved(long timestamp) {
		this.modificationStamp = timestamp;
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(fileUri);
		DidSaveTextDocumentParams params = new DidSaveTextDocumentParams(identifier);
		languageServer.getTextDocumentService().didSave(params);
	}

	public void documentClosed() {
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(fileUri);
		DidCloseTextDocumentParams params = new DidCloseTextDocumentParams(identifier);
		languageServer.getTextDocumentService().didClose(params);
	}

	/**
	 * Returns the text document sync kind capabilities of the server and {@link TextDocumentSyncKind#Full} otherwise.
	 *
	 * @return the text document sync kind capabilities of the server and {@link TextDocumentSyncKind#Full} otherwise.
	 */
	private TextDocumentSyncKind getTextDocumentSyncKind() {
		return syncKind;
	}

	protected long getModificationStamp() {
		return modificationStamp;
	}

	public IDocument getDocument() {
		return this.document;
	}

	private void checkEvent(DocumentEvent event) {
		if (this.document != event.getDocument()) {
			throw new IllegalStateException("Synchronizer should apply to only a single document, which is the one it was instantiated for"); //$NON-NLS-1$
		}
	}
}
