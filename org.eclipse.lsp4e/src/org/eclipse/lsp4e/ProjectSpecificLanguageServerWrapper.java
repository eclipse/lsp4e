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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.lsp4e.operations.diagnostics.LSPDiagnosticsToMarkers;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.internal.progress.ProgressMonitorFocusJobDialog;

/**
 * Wraps instantiation, initialization of project-specific instance of the
 * language server
 */
public class ProjectSpecificLanguageServerWrapper {

	private final class DocumentChangeListenenr implements IDocumentListener {
		private URI fileURI;
		private int version = 2;
		private DidChangeTextDocumentParams change;

		public DocumentChangeListenenr(URI fileURI) {
			this.fileURI = fileURI;
		}

		@Override
		public void documentChanged(DocumentEvent event) {
			if (this.change == null) {
				return;
			}
			this.change.getContentChanges().get(0).setText(event.getDocument().get());
			languageServer.getTextDocumentService().didChange(this.change);
			version++;
		}

		@Override
		public void documentAboutToBeChanged(DocumentEvent event) {
			// create change event according synch
			TextDocumentContentChangeEvent changeEvent = toChangeEvent(event);
			if (changeEvent == null) {
				return;
			}
			this.change = new DidChangeTextDocumentParams();
			VersionedTextDocumentIdentifier doc = new VersionedTextDocumentIdentifier();
			doc.setUri(fileURI.toString());
			doc.setVersion(version);
			this.change.setTextDocument(doc);
			this.change.setContentChanges(Arrays.asList(new TextDocumentContentChangeEvent[] { changeEvent }));
		}

		/**
		 * Convert Eclipse {@link DocumentEvent} to LS according
		 * {@link TextDocumentSyncKind}.
		 * {@link TextDocumentContentChangeEventImpl}.
		 * 
		 * @param event
		 *            Eclipse {@link DocumentEvent}
		 * @return the converted LS {@link TextDocumentContentChangeEventImpl}.
		 */
		private TextDocumentContentChangeEvent toChangeEvent(DocumentEvent event) {
			IDocument document = event.getDocument();
			TextDocumentContentChangeEvent changeEvent = null;
			TextDocumentSyncKind syncKind = getTextDocumentSyncKind();
			switch (syncKind) {
			case None:
				changeEvent = null;
				break;
			case Full:
				changeEvent = new TextDocumentContentChangeEvent();
				changeEvent.setText(document.get());
				break;
			case Incremental:
				changeEvent = new TextDocumentContentChangeEvent();
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
			return changeEvent;
		}

		/**
		 * Returns the text document sync kind capabilities of the server and
		 * {@link TextDocumentSyncKind#Full} otherwise.
		 * 
		 * @return the text document sync kind capabilities of the server and
		 *         {@link TextDocumentSyncKind#Full} otherwise.
		 */
		private TextDocumentSyncKind getTextDocumentSyncKind() {
			TextDocumentSyncKind syncKind = initializeResult != null
			        ? initializeResult.getCapabilities().getTextDocumentSync() : null;
			return syncKind != null ? syncKind : TextDocumentSyncKind.Full;
		}
	}

	final private StreamConnectionProvider lspStreamProvider;
	private LanguageServer languageServer;
	private IProject project;
	private Map<IPath, DocumentChangeListenenr> connectedFiles;
	private Map<IPath, IDocument> documents;

	private Job initializeJob;
	private InitializeResult initializeResult;
	private Future<?> launcherFuture;

	public ProjectSpecificLanguageServerWrapper(IProject project, StreamConnectionProvider connection) {
		this.project = project;
		this.lspStreamProvider = connection;
		this.connectedFiles = new HashMap<>();
		this.documents = new HashMap<>();
	}

	private void start() throws IOException {
		if (this.languageServer != null) {
			if (stillActive()) {
				return;
			} else {
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
			public CompletableFuture<Void> showMessageRequest(ShowMessageRequestParams requestParams) {
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
				ServerMessageHandler.logMessage(message);
			}
		};
		ExecutorService executorService = Executors.newCachedThreadPool();
		Launcher<LanguageServer> launcher = LSPLauncher.createClientLauncher(client,
				this.lspStreamProvider.getInputStream(),
				this.lspStreamProvider.getOutputStream(),
				executorService,
				consumer -> (
					message -> {
					System.err.println(message.toString());
					consumer.consume(message);
				}));
		this.languageServer = launcher.getRemoteProxy();
		this.launcherFuture = launcher.startListening();

		this.initializeJob = new Job(Messages.initializeLanguageServer_job) {
			protected IStatus run(IProgressMonitor monitor) {
				InitializeParams initParams = new InitializeParams();
				initParams.setRootPath(project.getLocation().toFile().getAbsolutePath());
				String name = "Eclipse IDE"; //$NON-NLS-1$
				if (Platform.getProduct() != null) {
					name = Platform.getProduct().getName();
				}
				initParams.setClientName(name);
				initParams.setCapabilities(new ClientCapabilities());
				CompletableFuture<InitializeResult> result = languageServer.initialize(initParams);
				try {
					initializeResult = result.get(3, TimeUnit.SECONDS);
				} catch (InterruptedException | ExecutionException | TimeoutException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return Status.OK_STATUS;
			}
		};
		this.initializeJob.setUser(true);
		this.initializeJob.setSystem(false);
		this.initializeJob.schedule();
		} catch (Exception ex) {
			ex.printStackTrace();
			stop();
		}
	}

	private boolean stillActive() {
		return this.launcherFuture != null && !this.launcherFuture.isDone() && !this.launcherFuture.isCancelled();
	}

	private void stop() {
		if (this.initializeJob != null) {
			this.initializeJob.cancel();
			this.initializeJob = null;
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
		while (!this.documents.isEmpty()) {
			disconnect(this.documents.keySet().iterator().next());
		}
		this.languageServer = null;
	}

	public void connect(IFile file, final IDocument document) throws IOException {
		start();
		if (this.connectedFiles.containsKey(file.getLocation())) {
			return;
		}
		// add a document buffer
		DidOpenTextDocumentParams open = new DidOpenTextDocumentParams();
		TextDocumentItem textDocument = new TextDocumentItem();
		textDocument.setUri(file.getLocationURI().toString());
		textDocument.setText(document.get());
		textDocument.setLanguageId(file.getFileExtension());
		open.setTextDocument(textDocument);
		try {
			this.initializeJob.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		this.languageServer.getTextDocumentService().didOpen(open);

		DocumentChangeListenenr listener = new DocumentChangeListenenr(file.getLocationURI());
		document.addDocumentListener(listener);
		this.connectedFiles.put(file.getLocation(), listener);
		this.documents.put(file.getLocation(), document);
	}

	public void disconnect(IPath path) {
		this.documents.get(path).removeDocumentListener(this.connectedFiles.get(path));
		this.connectedFiles.remove(path);
		this.documents.remove(path);
		if (this.connectedFiles.isEmpty()) {
			stop();
		}
	}

	@NonNull public LanguageServer getServer() {
		if (this.initializeJob.getState() != Job.NONE) {
			if (Display.getCurrent() != null) { // UI Thread
				ProgressMonitorFocusJobDialog dialog = new ProgressMonitorFocusJobDialog(null);
				dialog.setBlockOnOpen(true);
				dialog.show(this.initializeJob, null);
			}
			try {
				this.initializeJob.join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return this.languageServer;
	}

	public ServerCapabilities getServerCapabilities() {
		try {
			start();
			this.initializeJob.join(1000, new NullProgressMonitor());
		} catch (InterruptedException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
