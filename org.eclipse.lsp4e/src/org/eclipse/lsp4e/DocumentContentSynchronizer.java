/*******************************************************************************
 * Copyright (c) 2016, 2019 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Michał Niewrzał (Rogue Wave Software Inc.)
 *******************************************************************************/
package org.eclipse.lsp4e;

import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.content.IContentType;
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
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

final class DocumentContentSynchronizer implements IDocumentListener {

	private final @NonNull LanguageServerWrapper languageServerWrapper;
	private final @NonNull IDocument document;
	private final @NonNull URI fileUri;
	private final TextDocumentSyncKind syncKind;

	private int version = 0;
	private DidChangeTextDocumentParams changeParams;
	private long modificationStamp;
	final @NonNull CompletableFuture<Void> didOpenFuture;

	public DocumentContentSynchronizer(@NonNull LanguageServerWrapper languageServerWrapper,
			@NonNull IDocument document,
			TextDocumentSyncKind syncKind) {
		this.languageServerWrapper = languageServerWrapper;
		this.fileUri = LSPEclipseUtils.toUri(document);
        try {
            IFileStore store = EFS.getStore(fileUri);
            this.modificationStamp = store.fetchInfo().getLastModified();
        } catch (CoreException e) {
            LanguageServerPlugin.logError(e);
            this.modificationStamp =  new File(fileUri).lastModified();
        }
        this.syncKind = syncKind != null ? syncKind : TextDocumentSyncKind.Full;

		this.document = document;
		// add a document buffer
		TextDocumentItem textDocument = new TextDocumentItem();
		textDocument.setUri(fileUri.toString());
		textDocument.setText(document.get());

		List<IContentType> contentTypes = LSPEclipseUtils.getDocumentContentTypes(this.document);

		String languageId = languageServerWrapper.getLanguageId(contentTypes.toArray(new IContentType[0]));

		IPath fromPortableString = Path.fromPortableString(this.fileUri.getPath());
		if (languageId == null) {
			languageId = fromPortableString.getFileExtension();
			if (languageId == null) {
				languageId = fromPortableString.lastSegment();
			}
		}

		textDocument.setLanguageId(languageId);
		textDocument.setVersion(++version);
		didOpenFuture = languageServerWrapper.getInitializedServer()
				.thenAcceptAsync(ls -> ls.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(textDocument)));
	}

	@Override
	public void documentChanged(DocumentEvent event) {
		checkEvent(event);
		if (syncKind == TextDocumentSyncKind.Full) {
			createChangeEvent(event);
		}

		if (changeParams != null) {
			final DidChangeTextDocumentParams changeParamsToSend = changeParams;
			changeParams = null;

			changeParamsToSend.getTextDocument().setVersion(++version);
			languageServerWrapper.getInitializedServer()
					.thenAcceptAsync(ls -> ls.getTextDocumentService().didChange(changeParamsToSend));
		}
	}

	@Override
	public void documentAboutToBeChanged(DocumentEvent event) {
		checkEvent(event);
		if (syncKind == TextDocumentSyncKind.Incremental) {
			// this really needs to happen before event gets actually
			// applied, to properly compute positions
			createChangeEvent(event);
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
	private boolean createChangeEvent(DocumentEvent event) {
		changeParams = new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(), Collections.singletonList(new TextDocumentContentChangeEvent()));
		changeParams.getTextDocument().setUri(fileUri.toString());

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
		ServerCapabilities serverCapabilities = languageServerWrapper.getServerCapabilities();
		if(serverCapabilities != null ) {
			Either<TextDocumentSyncKind, TextDocumentSyncOptions> textDocumentSync = serverCapabilities.getTextDocumentSync();
			if(textDocumentSync.isRight() && textDocumentSync.getRight().getSave() == null) {
				return;
			}
		}
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(fileUri.toString());
		DidSaveTextDocumentParams params = new DidSaveTextDocumentParams(identifier, document.get());
		languageServerWrapper.getInitializedServer().thenAcceptAsync(ls -> ls.getTextDocumentService().didSave(params));
	}

	public void documentClosed() {
		// When LS is shut down all documents are being disconnected. No need to send "didClose" message to the LS that is being shut down or not yet started
		if (languageServerWrapper.isActive()) {
			TextDocumentIdentifier identifier = new TextDocumentIdentifier(fileUri.toString());
			DidCloseTextDocumentParams params = new DidCloseTextDocumentParams(identifier);
			languageServerWrapper.getInitializedServer().thenAcceptAsync(ls -> ls.getTextDocumentService().didClose(params));
		}
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

	int getVersion() {
		return version;
	}

	private void checkEvent(DocumentEvent event) {
		if (this.document != event.getDocument()) {
			throw new IllegalStateException("Synchronizer should apply to only a single document, which is the one it was instantiated for"); //$NON-NLS-1$
		}
	}
}
