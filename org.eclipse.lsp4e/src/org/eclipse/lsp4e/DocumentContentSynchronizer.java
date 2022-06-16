/*******************************************************************************
 * Copyright (c) 2016, 2022 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Michał Niewrzał (Rogue Wave Software Inc.)
 *  Rubén Porras Campo (Avaloq Evolution AG) - documentAboutToBeSaved implementation
 *******************************************************************************/
package org.eclipse.lsp4e;

import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextDocumentSaveReason;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WillSaveTextDocumentParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.osgi.util.NLS;

final class DocumentContentSynchronizer implements IDocumentListener {

	private final @NonNull LanguageServerWrapper languageServerWrapper;
	private final @NonNull IDocument document;
	private final @NonNull URI fileUri;
	private final TextDocumentSyncKind syncKind;

	private int version = 0;
	private DidChangeTextDocumentParams changeParams;
	private long modificationStamp;
	private final AtomicReference<CompletableFuture<LanguageServer>> lastChangeFuture;
	private LanguageServer languageServer;
	private IPreferenceStore store;

	public DocumentContentSynchronizer(@NonNull LanguageServerWrapper languageServerWrapper,
			@NonNull IDocument document, TextDocumentSyncKind syncKind) {
		this.languageServerWrapper = languageServerWrapper;
		URI uri = LSPEclipseUtils.toUri(document);
		if (uri == null) {
			throw new NullPointerException();
		}
		this.fileUri = uri;
		try {
			IFileStore store = EFS.getStore(fileUri);
			this.modificationStamp = store.fetchInfo().getLastModified();
		} catch (CoreException e) {
			LanguageServerPlugin.logError(e);
			this.modificationStamp = new File(fileUri).lastModified();
		}
		this.syncKind = syncKind != null ? syncKind : TextDocumentSyncKind.Full;

		this.document = document;
		this.store = LanguageServerPlugin.getDefault().getPreferenceStore();

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
		lastChangeFuture = new AtomicReference<>(languageServerWrapper.getInitializedServer().thenApplyAsync(ls -> {
			this.languageServer = ls;
			ls.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(textDocument));
			return ls;
		}));
	}

	/**
 	 * Submit an asynchronous call (i.e. to the language server) that be inserted into the current position w.r.t.
 	 * document change events
 	 *
 	 * @param <U> Computation return type
 	 * @param fn Asynchronous computation on the language server
 	 * @return Asynchronous result object.
 	 */
	<U> @NonNull CompletableFuture<U> executeOnCurrentVersionAsync(
			Function<LanguageServer, ? extends CompletionStage<U>> fn) {
		AtomicReference<CompletableFuture<U>> resValueFuture = new AtomicReference<>();
		lastChangeFuture.updateAndGet(f -> {
			CompletableFuture<U> valueFuture = f.thenComposeAsync(fn);
			resValueFuture.set(valueFuture);
			// We ignore any exceptions that happen when executing the given future
			return valueFuture.handle((value, error) -> this.languageServer);
		});
		return resValueFuture.get();
	}

	CompletableFuture<LanguageServer> lastChangeFuture() {
		return lastChangeFuture.get();
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
			lastChangeFuture.updateAndGet(f -> f.thenApplyAsync(ls -> {
				ls.getTextDocumentService().didChange(changeParamsToSend);
				return ls;
			}));
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
	 * Convert Eclipse {@link DocumentEvent} to LS according
	 * {@link TextDocumentSyncKind}. {@link TextDocumentContentChangeEventImpl}.
	 *
	 * @param event
	 *            Eclipse {@link DocumentEvent}
	 * @return true if change event is ready to be sent
	 */
	private boolean createChangeEvent(DocumentEvent event) {
		Assert.isTrue(changeParams == null);
		changeParams = new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(),
				Collections.singletonList(new TextDocumentContentChangeEvent()));
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

	private boolean serverSupportsWillSaveWaitUntil() {
		ServerCapabilities serverCapabilities = languageServerWrapper.getServerCapabilities();
		if(serverCapabilities != null ) {
			Either<TextDocumentSyncKind, TextDocumentSyncOptions> textDocumentSync = serverCapabilities.getTextDocumentSync();
			if(textDocumentSync.isRight()) {
				TextDocumentSyncOptions saveOptions = textDocumentSync.getRight();
				return saveOptions != null && Boolean.TRUE.equals(saveOptions.getWillSaveWaitUntil());
			}
		}
		return false;
	}

	private static final String WILL_SAVE_WAIT_UNTIL_TIMEOUT__KEY = "timeout.willSaveWaitUntil"; //$NON-NLS-1$

	private static final int WILL_SAVE_WAIT_UNTIL_COUNT_THRESHOLD = 3;
	private static final Map<String, Integer> WILL_SAVE_WAIT_UNTIL_TIMEOUT_MAP = new ConcurrentHashMap<>();

	/**
	 * Converts a language server ID to the preference ID to define a timeout
	 * for willSaveWaitUntil
	 *
	 * @return language server's preference ID to define a timeout for willSaveWaitUntil
	 */
	private static @NonNull String lsToWillSaveWaitUntilTimeoutKey(String serverId) {
		return serverId + '.' + WILL_SAVE_WAIT_UNTIL_TIMEOUT__KEY;
	}

	private int lsToWillSaveWaitUntilTimeout() {
		int defaultWillSaveWaitUntilTimeoutInSeconds = 5;
		int willSaveWaitUntilTimeout = store.getInt(lsToWillSaveWaitUntilTimeoutKey(languageServerWrapper.serverDefinition.id));
		return willSaveWaitUntilTimeout != 0 ? willSaveWaitUntilTimeout : defaultWillSaveWaitUntilTimeoutInSeconds;
	}

	public void documentAboutToBeSaved() {
		if (!serverSupportsWillSaveWaitUntil()) {
			return;
		}

		String uri = fileUri.toString();
		if (WILL_SAVE_WAIT_UNTIL_TIMEOUT_MAP.getOrDefault(uri, 0) > WILL_SAVE_WAIT_UNTIL_COUNT_THRESHOLD) {
			return;
		}

		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri);
		// Use @link{TextDocumentSaveReason.Manual} as the platform does not give enough information to be accurate
		WillSaveTextDocumentParams params = new WillSaveTextDocumentParams(identifier, TextDocumentSaveReason.Manual);


		try {
			List<TextEdit> edits = executeOnCurrentVersionAsync(ls -> ls.getTextDocumentService().willSaveWaitUntil(params))
				.get(lsToWillSaveWaitUntilTimeout(), TimeUnit.SECONDS);
			try {
				LSPEclipseUtils.applyEdits(document, edits);
			} catch (BadLocationException e) {
				LanguageServerPlugin.logError(e);
			}
		} catch (ExecutionException e) {
			LanguageServerPlugin.logError(e);
		} catch (TimeoutException e) {
			Integer timeoutCount = WILL_SAVE_WAIT_UNTIL_TIMEOUT_MAP.compute(uri,
					(k, v) -> v == null ? 1 : Integer.valueOf(v + 1));
			String message = timeoutCount > WILL_SAVE_WAIT_UNTIL_COUNT_THRESHOLD ?
					Messages.DocumentContentSynchronizer_TimeoutThresholdMessage:
						Messages.DocumentContentSynchronizer_TimeoutMessage;
			String boundMessage = NLS.bind(message, Integer.valueOf(lsToWillSaveWaitUntilTimeout()).toString(), uri);
			ServerMessageHandler.showMessage(Messages.DocumentContentSynchronizer_OnSaveActionTimeout, new MessageParams(MessageType.Error, boundMessage));
		} catch (InterruptedException e) {
			LanguageServerPlugin.logError(e);
			Thread.currentThread().interrupt();
		}
	}

	public void documentSaved(long timestamp) {
		this.modificationStamp = timestamp;
		ServerCapabilities serverCapabilities = languageServerWrapper.getServerCapabilities();
		if (serverCapabilities != null) {
			Either<TextDocumentSyncKind, TextDocumentSyncOptions> textDocumentSync = serverCapabilities
					.getTextDocumentSync();
			if (textDocumentSync.isRight() && textDocumentSync.getRight().getSave() == null) {
				return;
			}
		}
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(fileUri.toString());
		DidSaveTextDocumentParams params = new DidSaveTextDocumentParams(identifier, document.get());
		lastChangeFuture.updateAndGet(f -> f.thenApplyAsync(ls -> {
  			ls.getTextDocumentService().didSave(params);
  			return ls;
  		}));
	}

	public void documentClosed() {
		String uri = fileUri.toString();
		WILL_SAVE_WAIT_UNTIL_TIMEOUT_MAP.remove(uri);
		// When LS is shut down all documents are being disconnected. No need to send
		// "didClose" message to the LS that is being shut down or not yet started
		if (languageServerWrapper.isActive()) {
			TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri);
			DidCloseTextDocumentParams params = new DidCloseTextDocumentParams(identifier);
			lastChangeFuture.get().thenAcceptAsync(ls -> ls.getTextDocumentService().didClose(params));
		}
	}

	/**
	 * Returns the text document sync kind capabilities of the server and
	 * {@link TextDocumentSyncKind#Full} otherwise.
	 *
	 * @return the text document sync kind capabilities of the server and
	 *         {@link TextDocumentSyncKind#Full} otherwise.
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
			throw new IllegalStateException(
					"Synchronizer should apply to only a single document, which is the one it was instantiated for"); //$NON-NLS-1$
		}
	}
}
