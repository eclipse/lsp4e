/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.operations.diagnostics.LSPDiagnosticsToMarkers;
import org.eclipse.lsp4e.server.StreamConnectionProvider;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.ExecuteCommandCapabilites;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.SymbolCapabilites;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.WorkspaceClientCapabilites;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
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

	private final StreamConnectionProvider lspStreamProvider;
	private LanguageServer languageServer;
	private IProject project;
	private String label;
	private Map<IPath, DocumentContentSynchronizer> connectedDocuments;

	private InitializeResult initializeResult;
	private Future<?> launcherFuture;
	private CompletableFuture<InitializeResult> initializeFuture;

	public ProjectSpecificLanguageServerWrapper(IProject project, String label, StreamConnectionProvider connection) {
		this.project = project;
		this.label = label;
		this.lspStreamProvider = connection;
		this.connectedDocuments = new HashMap<>();
		FileBuffers.getTextFileBufferManager().addFileBufferListener(fileBufferListener);
	}

	/**
	 * Starts a language server and triggers initialization. If language server is started and active, does nothing. If
	 * language server is inactive, restart it.
	 *
	 * @throws IOException
	 */
	public void start() throws IOException {
		Set<IPath> filesToReconnect = Collections.emptySet();
		if (this.languageServer != null) {
			if (isActive()) {
				return;
			} else {
				filesToReconnect =  new HashSet<>(this.connectedDocuments.keySet());
				stop();
			}
		}
		try {
			this.lspStreamProvider.start();
			LanguageClient client = new LanguageClient() {
				private LSPDiagnosticsToMarkers diagnosticHandler = new LSPDiagnosticsToMarkers(project);

				@Override
				public void telemetryEvent(Object object) {
					// TODO
				}

				@Override
				public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
					return ServerMessageHandler.showMessageRequest(requestParams);
				}

				@Override
				public void showMessage(MessageParams messageParams) {
					ServerMessageHandler.showMessage(messageParams);
				}

				@Override
				public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
					this.diagnosticHandler.accept(diagnostics);
				}

				@Override
				public void logMessage(MessageParams message) {
					ServerMessageHandler.logMessage(project, label, message);
				}

				@Override
				public CompletableFuture<ApplyWorkspaceEditResponse> applyEdit(ApplyWorkspaceEditParams params) {
					return CompletableFuture.supplyAsync(() -> {
						LSPEclipseUtils.applyWorkspaceEdit(params.getEdit());
						return new ApplyWorkspaceEditResponse(true);
					});
				}
			};
			ExecutorService executorService = Executors.newCachedThreadPool();
			final InitializeParams initParams = new InitializeParams();
			initParams.setRootUri(project.getLocation().toFile().toURI().toString());
			Launcher<LanguageServer> launcher = LSPLauncher.createClientLauncher(client,
					this.lspStreamProvider.getInputStream(), this.lspStreamProvider.getOutputStream(), executorService,
					consumer -> (message -> {
						consumer.consume(message);
						logMessage(message);
						this.lspStreamProvider.handleMessage(message, this.languageServer, URI.create(initParams.getRootUri()));
					}));
			this.languageServer = launcher.getRemoteProxy();
			this.launcherFuture = launcher.startListening();

			String name = "Eclipse IDE"; //$NON-NLS-1$
			if (Platform.getProduct() != null) {
				name = Platform.getProduct().getName();
			}
			WorkspaceClientCapabilites workspaceClientCapabilites = new WorkspaceClientCapabilites();
			workspaceClientCapabilites.setApplyEdit(Boolean.TRUE);
			workspaceClientCapabilites.setExecuteCommand(new ExecuteCommandCapabilites());
			workspaceClientCapabilites.setSymbol(new SymbolCapabilites());
			initParams.setCapabilities(new ClientCapabilities(workspaceClientCapabilites, null, null));
			initParams.setClientName(name);
			initParams.setCapabilities(new ClientCapabilities());
			initParams.setInitializationOptions(
					this.lspStreamProvider.getInitializationOptions(URI.create(initParams.getRootUri())));
			initializeFuture = languageServer.initialize(initParams).thenApply(res -> {
				initializeResult = res;
				return res;
			});
			for (IPath fileToReconnect : filesToReconnect) {
				connect(fileToReconnect);
			}
		} catch (Exception ex) {
			LanguageServerPlugin.logError(ex);
			stop();
		}
	}

	private void logMessage(Message message) {
		if (message instanceof ResponseMessage && ((ResponseMessage) message).getError() != null) {
			ResponseMessage responseMessage = (ResponseMessage) message;
			LanguageServerPlugin.logError(new ResponseErrorException(responseMessage.getError()));
		} else {
			LanguageServerPlugin.logInfo(message.getClass().getSimpleName() +'\n' + message.toString());
		}
	}

	/**
	 * @return whether the underlying connection to language server is still active
	 */
	public boolean isActive() {
		return this.launcherFuture != null && !this.launcherFuture.isDone() && !this.launcherFuture.isCancelled();
	}

	private void stop() {
		if (this.initializeFuture != null) {
			this.initializeFuture.cancel(true);
			this.initializeFuture = null;
		}
		this.initializeResult = null;
		if (this.languageServer != null) {
			try {
				this.languageServer.shutdown();
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

	public void connect(@NonNull IPath absolutePath)
			throws IOException, InterruptedException, ExecutionException, TimeoutException {
		start();
		IFile file = (IFile) LSPEclipseUtils.findResourceFor(absolutePath.toFile().toURI().toString());
		IDocument document = LSPEclipseUtils.getDocument(file);
		if (this.connectedDocuments.containsKey(file.getLocation())) {
			return;
		}

		this.initializeFuture.get(3, TimeUnit.SECONDS);

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
		DocumentContentSynchronizer listener = new DocumentContentSynchronizer(languageServer, document, absolutePath, syncKind);
		document.addDocumentListener(listener);
		this.connectedDocuments.put(file.getLocation(), listener);
	}

	public void disconnect(IPath path) {
		DocumentContentSynchronizer documentListener = this.connectedDocuments.remove(path);
		if (documentListener != null) {
			documentListener.getDocument().removeDocumentListener(documentListener);
			documentListener.documentClosed();
			this.connectedDocuments.remove(documentListener);
		}
		if (this.connectedDocuments.isEmpty()) {
			stop();
		}
	}

	@NonNull
	public LanguageServer getServer() {
		try {
			start();
		} catch (IOException ex) {
			LanguageServerPlugin.logError(ex);
		}
		if (!this.initializeFuture.isDone()) {
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
			}
			this.initializeFuture.join();
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
			this.initializeFuture.get(1000, TimeUnit.MILLISECONDS);
		} catch (TimeoutException | IOException | InterruptedException | ExecutionException e) {
			LanguageServerPlugin.logError(e);
		}
		if (this.initializeResult != null) {
			return this.initializeResult.getCapabilities();
		} else {
			return null;
		}
	}

	public StreamConnectionProvider getUnderlyingConnection() {
		return this.lspStreamProvider;
	}

}
