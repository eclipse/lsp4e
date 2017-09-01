/*******************************************************************************
 * Copyright (c) 2016, 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Miro Spoenemann (TypeFox) - extracted LanguageClientImpl
 *  Jan Koehnlein (TypeFox) - bug 521744
 *******************************************************************************/
package org.eclipse.lsp4e;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.IFileBuffer;
import org.eclipse.core.filebuffers.IFileBufferListener;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LanguageServersRegistry.LanguageServerDefinition;
import org.eclipse.lsp4e.server.StreamConnectionProvider;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.CodeActionCapabilities;
import org.eclipse.lsp4j.CodeLensCapabilities;
import org.eclipse.lsp4j.CompletionCapabilities;
import org.eclipse.lsp4j.CompletionItemCapabilities;
import org.eclipse.lsp4j.DefinitionCapabilities;
import org.eclipse.lsp4j.DocumentHighlightCapabilities;
import org.eclipse.lsp4j.DocumentLinkCapabilities;
import org.eclipse.lsp4j.DocumentSymbolCapabilities;
import org.eclipse.lsp4j.ExecuteCommandCapabilities;
import org.eclipse.lsp4j.FormattingCapabilities;
import org.eclipse.lsp4j.HoverCapabilities;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.RangeFormattingCapabilities;
import org.eclipse.lsp4j.ReferencesCapabilities;
import org.eclipse.lsp4j.RenameCapabilities;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SignatureHelpCapabilities;
import org.eclipse.lsp4j.SymbolCapabilities;
import org.eclipse.lsp4j.SynchronizationCapabilities;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;

/**
 * Wraps instantiation, initialization of project-specific instance of the language server
 */
public class ProjectSpecificLanguageServerWrapper {

	private IFileBufferListener fileBufferListener = new FileBufferListenerAdapter() {
		@Override
		public void bufferDisposed(IFileBuffer buffer) {
			Path filePath = new Path(buffer.getFileStore().toURI().getPath());
			if (!isFromProject(filePath)) {
				return;
			}
			disconnect(filePath);
		}

		@Override
		public void dirtyStateChanged(IFileBuffer buffer, boolean isDirty) {
			if (isDirty) {
				return;
			}
			Path filePath = new Path(buffer.getFileStore().toURI().getPath());
			if (!isFromProject(filePath)) {
				return;
			}

			DocumentContentSynchronizer documentListener = connectedDocuments.get(filePath);
			if (documentListener != null && documentListener.getModificationStamp() < buffer.getModificationStamp()) {
				documentListener.documentSaved(buffer.getModificationStamp());
			}
		}

		private boolean isFromProject(IPath path) {
			if (project == null || project.getLocation() == null) {
				return false;
			}
			return project.getLocation().isPrefixOf(path);
		}

	};

	public final @NonNull LanguageServerDefinition serverDefinition;
	public final @NonNull IProject project;

	private final @NonNull StreamConnectionProvider lspStreamProvider;
	private LanguageServer languageServer;
	private Map<IPath, DocumentContentSynchronizer> connectedDocuments;

	private InitializeResult initializeResult;
	private Future<?> launcherFuture;
	private CompletableFuture<InitializeResult> initializeFuture;

	private boolean capabilitiesAlreadyRequested;

	private long initializeStartTime;


	public ProjectSpecificLanguageServerWrapper(@NonNull IProject project, @NonNull LanguageServerDefinition serverDefinition) {
		this.project = project;
		this.serverDefinition = serverDefinition;
		this.lspStreamProvider = serverDefinition.createConnectionProvider();
		this.connectedDocuments = new HashMap<>();
	}

	/**
	 * Starts a language server and triggers initialization. If language server is started and active, does nothing. If
	 * language server is inactive, restart it.
	 *
	 * @throws IOException
	 */
	public synchronized void start() throws IOException {
		Map<IPath, IDocument> filesToReconnect = Collections.emptyMap();
		if (this.languageServer != null) {
			if (isActive()) {
				return;
			} else {
				filesToReconnect = new HashMap<>();
				for (Entry<IPath, DocumentContentSynchronizer> entry : this.connectedDocuments.entrySet()) {
					filesToReconnect.put(entry.getKey(), entry.getValue().getDocument());
				}
				stop();
			}
		}
		try {
			this.lspStreamProvider.start();

			LanguageClientImpl client = serverDefinition.createLanguageClient();
			ExecutorService executorService = Executors.newCachedThreadPool();
			final InitializeParams initParams = new InitializeParams();
			initParams.setRootUri(LSPEclipseUtils.toUri(project).toString());
			initParams.setRootPath(project.getLocation().toFile().getAbsolutePath());
			Launcher<? extends LanguageServer> launcher = Launcher.createLauncher(client, serverDefinition.getServerInterface(),
					this.lspStreamProvider.getInputStream(), this.lspStreamProvider.getOutputStream(), executorService,
					consumer -> (message -> {
						consumer.consume(message);
						logMessage(message);
						this.lspStreamProvider.handleMessage(message, this.languageServer, URI.create(initParams.getRootUri()));
					}));
			this.languageServer = launcher.getRemoteProxy();
			client.connect(languageServer, this);
			this.launcherFuture = launcher.startListening();

			String name = "Eclipse IDE"; //$NON-NLS-1$
			if (Platform.getProduct() != null) {
				name = Platform.getProduct().getName();
			}
			WorkspaceClientCapabilities workspaceClientCapabilites = new WorkspaceClientCapabilities();
			workspaceClientCapabilites.setApplyEdit(Boolean.TRUE);
			workspaceClientCapabilites.setExecuteCommand(new ExecuteCommandCapabilities());
			workspaceClientCapabilites.setSymbol(new SymbolCapabilities());
			TextDocumentClientCapabilities textDocumentClientCapabilities = new TextDocumentClientCapabilities();
			textDocumentClientCapabilities.setCodeAction(new CodeActionCapabilities());
			textDocumentClientCapabilities.setCodeLens(new CodeLensCapabilities());
			textDocumentClientCapabilities.setCompletion(new CompletionCapabilities(new CompletionItemCapabilities(Boolean.TRUE)));
			textDocumentClientCapabilities.setDefinition(new DefinitionCapabilities());
			textDocumentClientCapabilities.setDocumentHighlight(new DocumentHighlightCapabilities());
			textDocumentClientCapabilities.setDocumentLink(new DocumentLinkCapabilities());
			textDocumentClientCapabilities.setDocumentSymbol(new DocumentSymbolCapabilities());
			textDocumentClientCapabilities.setFormatting(new FormattingCapabilities());
			textDocumentClientCapabilities.setHover(new HoverCapabilities());
			textDocumentClientCapabilities.setOnTypeFormatting(null); // TODO
			textDocumentClientCapabilities.setRangeFormatting(new RangeFormattingCapabilities());
			textDocumentClientCapabilities.setReferences(new ReferencesCapabilities());
			textDocumentClientCapabilities.setRename(new RenameCapabilities());
			textDocumentClientCapabilities.setSignatureHelp(new SignatureHelpCapabilities());
			textDocumentClientCapabilities.setSynchronization(new SynchronizationCapabilities(Boolean.TRUE, Boolean.TRUE, Boolean.TRUE));
			initParams.setCapabilities(new ClientCapabilities(workspaceClientCapabilites, textDocumentClientCapabilities, null));
			initParams.setClientName(name);
			initParams.setInitializationOptions(
					this.lspStreamProvider.getInitializationOptions(URI.create(initParams.getRootUri())));
			initializeFuture = languageServer.initialize(initParams).thenApply(res -> {
				initializeResult = res;
				return res;
			});
			initializeStartTime = System.currentTimeMillis();
			final Map<IPath, IDocument> toReconnect = filesToReconnect;
			initializeFuture.thenRun(() -> {
				for (Entry<IPath, IDocument> fileToReconnect : toReconnect.entrySet()) {
					try {
						connect(fileToReconnect.getKey(), fileToReconnect.getValue());
					} catch (IOException e) {
						LanguageServerPlugin.logError(e);
					}
				}
			});
			FileBuffers.getTextFileBufferManager().addFileBufferListener(fileBufferListener);
		} catch (Exception ex) {
			LanguageServerPlugin.logError(ex);
			stop();
		}
	}

	private void logMessage(Message message) {
		if (message instanceof ResponseMessage && ((ResponseMessage) message).getError() != null) {
			ResponseMessage responseMessage = (ResponseMessage) message;
			LanguageServerPlugin.logError(new ResponseErrorException(responseMessage.getError()));
		} else if (LanguageServerPlugin.DEBUG) {
			LanguageServerPlugin.logInfo(message.getClass().getSimpleName() + '\n' + message.toString());
		}
	}

	/**
	 * @return whether the underlying connection to language server is still active
	 */
	public boolean isActive() {
		return this.launcherFuture != null && !this.launcherFuture.isDone() && !this.launcherFuture.isCancelled();
	}

	private synchronized void stop() {
		if (this.initializeFuture != null) {
			this.initializeFuture.cancel(true);
			this.initializeFuture = null;
		}
		this.initializeResult = null;
		this.capabilitiesAlreadyRequested = false;

		if (this.languageServer != null) {
			try {
				CompletableFuture<Object> shutdown = this.languageServer.shutdown();
				shutdown.get(5000, TimeUnit.MILLISECONDS);
			} catch (Exception ex) {
				// most likely closed externally
			}
		}
		if (this.launcherFuture != null) {
			this.launcherFuture.cancel(true);
			this.launcherFuture = null;
		}
		if (this.lspStreamProvider != null) {
			this.lspStreamProvider.stop();
		}
		while (!this.connectedDocuments.isEmpty()) {
			disconnect(this.connectedDocuments.keySet().iterator().next());
		}
		this.languageServer = null;

		FileBuffers.getTextFileBufferManager().removeFileBufferListener(fileBufferListener);
	}

	public void connect(@NonNull IPath absolutePath, IDocument document) throws IOException {
		final IPath thePath = Path.fromOSString(absolutePath.toFile().getAbsolutePath()); // should be useless
		if (this.connectedDocuments.containsKey(thePath)) {
			return;
		}
		start();
		if (this.initializeFuture == null) {
			return;
		}
		if (document == null) {
			IFile file = (IFile) LSPEclipseUtils.findResourceFor(thePath.toFile().toURI().toString());
			document = LSPEclipseUtils.getDocument(file);
		}
		if (document == null) {
			return;
		}
		final IDocument theDocument = document;
		initializeFuture.thenRun(() -> {
			if (this.connectedDocuments.containsKey(thePath)) {
				return;
			}
			Either<TextDocumentSyncKind, TextDocumentSyncOptions> syncOptions = initializeFuture == null ? null
					: initializeResult.getCapabilities().getTextDocumentSync();
			TextDocumentSyncKind syncKind = null;
			if (syncOptions != null) {
				if (syncOptions.isRight()) {
					syncKind = syncOptions.getRight().getChange();
				} else if (syncOptions.isLeft()) {
					syncKind = syncOptions.getLeft();
				}
			}
			DocumentContentSynchronizer listener = new DocumentContentSynchronizer(this, theDocument, thePath, syncKind);
			theDocument.addDocumentListener(listener);
			ProjectSpecificLanguageServerWrapper.this.connectedDocuments.put(thePath, listener);
		});
	}

	public void disconnect(IPath path) {
		DocumentContentSynchronizer documentListener = this.connectedDocuments.remove(path);
		if (documentListener != null) {
			documentListener.getDocument().removeDocumentListener(documentListener);
			documentListener.documentClosed();
		}
		if (this.connectedDocuments.isEmpty()) {
			stop();
		}
	}

	/**
	 * checks if the wrapper is already connected to the document at the given path
	 */
	public boolean isConnectedTo(IPath location) {
		return connectedDocuments.containsKey(location);
	}

	@Nullable
	public LanguageServer getServer() {
		try {
			start();
		} catch (IOException ex) {
			LanguageServerPlugin.logError(ex);
		}
		if (initializeFuture != null && !this.initializeFuture.isDone()) {
			if (Display.getCurrent() != null) { // UI Thread
				Job waitForInitialization = new Job(Messages.initializeLanguageServer_job) {
					@Override
					protected IStatus run(IProgressMonitor monitor) {
						initializeFuture.join();
						return Status.OK_STATUS;
					}
				};
				waitForInitialization.setUser(true);
				waitForInitialization.setSystem(false);
				PlatformUI.getWorkbench().getProgressService().showInDialog(
						PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), waitForInitialization);
			} else {
				this.initializeFuture.join();
			}
		}
		return this.languageServer;
	}

	/**
	 * Warning: this is a long running operation
	 *
	 * @return the server capabilities, or null if initialization job didn't complete
	 */
	@Nullable
	public ServerCapabilities getServerCapabilities() {
		try {
			start();
			if (this.initializeFuture != null) {
				this.initializeFuture.get(capabilitiesAlreadyRequested ? 0 : 1000, TimeUnit.MILLISECONDS);
			}
		} catch (TimeoutException e) {
			if (System.currentTimeMillis() - initializeStartTime > 10000) {
				LanguageServerPlugin.logError("LanguageServer not initialized after 10s", e); //$NON-NLS-1$
			}
		} catch (IOException | InterruptedException | ExecutionException e) {
			LanguageServerPlugin.logError(e);
		}
		this.capabilitiesAlreadyRequested = true;
		if (this.initializeResult != null) {
			return this.initializeResult.getCapabilities();
		} else {
			return null;
		}
	}

	/**
	 * @return The language ID that this wrapper is dealing with if defined in the content type mapping for the language server
	 */
	@Nullable
	public String getLanguageId(IContentType[] contentTypes) {
		for (IContentType contentType : contentTypes) {
			String languageId = serverDefinition.langugeIdMappings.get(contentType);
			if (languageId != null) {
				return languageId;
			}
		}

		return null;
	}

}
