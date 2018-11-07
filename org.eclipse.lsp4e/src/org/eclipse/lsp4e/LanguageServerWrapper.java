/*******************************************************************************
 * Copyright (c) 2016, 2018 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Miro Spoenemann (TypeFox) - extracted LanguageClientImpl
 *  Jan Koehnlein (TypeFox) - bug 521744
 *  Martin Lippert (Pivotal, Inc.) - bug 531030, 527902, 534637
 *  Kris De Volder (Pivotal, Inc.) - dynamic command registration
 *******************************************************************************/
package org.eclipse.lsp4e;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Assert;
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
import org.eclipse.lsp4j.CodeActionLiteralSupportCapabilities;
import org.eclipse.lsp4j.CodeLensCapabilities;
import org.eclipse.lsp4j.ColorProviderCapabilities;
import org.eclipse.lsp4j.CompletionCapabilities;
import org.eclipse.lsp4j.CompletionItemCapabilities;
import org.eclipse.lsp4j.DefinitionCapabilities;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.DocumentHighlightCapabilities;
import org.eclipse.lsp4j.DocumentLinkCapabilities;
import org.eclipse.lsp4j.DocumentSymbolCapabilities;
import org.eclipse.lsp4j.ExecuteCommandCapabilities;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.FailureHandlingKind;
import org.eclipse.lsp4j.FormattingCapabilities;
import org.eclipse.lsp4j.HoverCapabilities;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.RangeFormattingCapabilities;
import org.eclipse.lsp4j.ReferencesCapabilities;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.RenameCapabilities;
import org.eclipse.lsp4j.ResourceOperationKind;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SignatureHelpCapabilities;
import org.eclipse.lsp4j.SymbolCapabilities;
import org.eclipse.lsp4j.SynchronizationCapabilities;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.WorkspaceEditCapabilities;
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent;
import org.eclipse.lsp4j.WorkspaceFoldersOptions;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class LanguageServerWrapper {

	private IFileBufferListener fileBufferListener = new FileBufferListenerAdapter() {
			@Override
			public void bufferDisposed(IFileBuffer buffer) {
				Path filePath = new Path(buffer.getFileStore().toURI().getPath());
				if (!isInWatchedProject(filePath)) {
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
				if (!isInWatchedProject(filePath)) {
					return;
				}

				DocumentContentSynchronizer documentListener = connectedDocuments.get(filePath);
				if (documentListener != null && documentListener.getModificationStamp() < buffer.getModificationStamp()) {
					documentListener.documentSaved(buffer.getModificationStamp());
				}
			}

			private boolean isInWatchedProject(IPath path) {
				for (@NonNull IProject watchedProject : allWatchedProjects) {
					if (watchedProject.getLocation() != null && watchedProject.getLocation().isPrefixOf(path)) {
						return true;
					}
				}
				return false;
			}

		};

	@NonNull public final LanguageServerDefinition serverDefinition;
	@Nullable protected final IProject initialProject;
	@NonNull protected final Set<@NonNull IProject> allWatchedProjects;
	@NonNull protected Map<@NonNull IPath, @NonNull DocumentContentSynchronizer> connectedDocuments;

	protected StreamConnectionProvider lspStreamProvider;
	private Future<?> launcherFuture;
	private CompletableFuture<Void> initializeFuture;
	private LanguageServer languageServer;
	private ServerCapabilities serverCapabilities;

	/**
	 * Map containing unregistration handlers for dynamic capability registrations.
	 */
	private @NonNull Map<@NonNull String, @NonNull Runnable> dynamicRegistrations = new HashMap<>();
	private boolean initiallySupportsWorkspaceFolders = false;

	public LanguageServerWrapper(@Nullable IProject project, @NonNull LanguageServerDefinition serverDefinition)
			throws IllegalStateException {
		this.initialProject = project;
		this.allWatchedProjects = new HashSet<>();
		this.serverDefinition = serverDefinition;
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
			if (LoggingStreamConnectionProviderProxy.shouldLog(serverDefinition.id)) {
				this.lspStreamProvider = new LoggingStreamConnectionProviderProxy(
						serverDefinition.createConnectionProvider(), serverDefinition.id);
			} else {
				this.lspStreamProvider = serverDefinition.createConnectionProvider();
			}
			this.lspStreamProvider.start();

			LanguageClientImpl client = serverDefinition.createLanguageClient();
			ExecutorService executorService = Executors.newCachedThreadPool();
			final InitializeParams initParams = new InitializeParams();
			initParams.setProcessId(getCurrentProcessId());

			URI rootURI = null;
			IProject project = this.initialProject;
			if (project != null && project.exists()) {
				rootURI = LSPEclipseUtils.toUri(this.initialProject);
				initParams.setRootUri(rootURI.toString());
				initParams.setRootPath(rootURI.getPath());
			}
			Launcher<? extends LanguageServer> launcher = Launcher.createLauncher(client, serverDefinition.getServerInterface(),
					this.lspStreamProvider.getInputStream(), this.lspStreamProvider.getOutputStream(), executorService,
					consumer -> (message -> {
						consumer.consume(message);
						logMessage(message);
						URI root = initParams.getRootUri() != null ? URI.create(initParams.getRootUri()) : null;
						this.lspStreamProvider.handleMessage(message, this.languageServer, root);
					}));

			this.languageServer = launcher.getRemoteProxy();
			client.connect(languageServer, this);
			this.launcherFuture = launcher.startListening();

			String name = "Eclipse IDE"; //$NON-NLS-1$
			if (Platform.getProduct() != null) {
				name = Platform.getProduct().getName();
			}
			WorkspaceClientCapabilities workspaceClientCapabilities = new WorkspaceClientCapabilities();
			workspaceClientCapabilities.setApplyEdit(Boolean.TRUE);
			workspaceClientCapabilities.setExecuteCommand(new ExecuteCommandCapabilities(Boolean.TRUE));
			workspaceClientCapabilities.setSymbol(new SymbolCapabilities());
			workspaceClientCapabilities.setWorkspaceFolders(Boolean.TRUE);
			WorkspaceEditCapabilities editCapabilities = new WorkspaceEditCapabilities();
			editCapabilities.setDocumentChanges(Boolean.TRUE);
			editCapabilities.setResourceOperations(Arrays.asList(new String[] { ResourceOperationKind.Create,
					ResourceOperationKind.Delete, ResourceOperationKind.Rename }));
			editCapabilities.setFailureHandling(FailureHandlingKind.Undo);
			workspaceClientCapabilities.setWorkspaceEdit(editCapabilities);
			TextDocumentClientCapabilities textDocumentClientCapabilities = new TextDocumentClientCapabilities();
			CodeActionCapabilities codeActionCapabilities = new CodeActionCapabilities();
			codeActionCapabilities.setCodeActionLiteralSupport(new CodeActionLiteralSupportCapabilities());
			textDocumentClientCapabilities.setCodeAction(codeActionCapabilities);
			textDocumentClientCapabilities.setCodeLens(new CodeLensCapabilities());
			textDocumentClientCapabilities.setColorProvider(new ColorProviderCapabilities());
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
			initParams.setCapabilities(
					new ClientCapabilities(workspaceClientCapabilities, textDocumentClientCapabilities, null));
			initParams.setClientName(name);

			initParams.setInitializationOptions(
					this.lspStreamProvider.getInitializationOptions(rootURI));
			initParams.setTrace(this.lspStreamProvider.getTrace(rootURI));

			initializeFuture = languageServer.initialize(initParams).thenAccept(res -> {
				serverCapabilities = res.getCapabilities();
				this.initiallySupportsWorkspaceFolders = supportsWorkspaceFolders(serverCapabilities);
			}).thenRun(() -> this.languageServer.initialized(new InitializedParams()));

			final Map<IPath, IDocument> toReconnect = filesToReconnect;
			initializeFuture.thenRun(() -> {
				if (this.initialProject != null) {
					watchProject(this.initialProject, true);
				}
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

	private static boolean supportsWorkspaceFolders(ServerCapabilities serverCapabilities) {
		return serverCapabilities != null
				&& serverCapabilities.getWorkspace() != null
				&& serverCapabilities.getWorkspace().getWorkspaceFolders() != null
				&& Boolean.TRUE.equals(serverCapabilities.getWorkspace().getWorkspaceFolders().getSupported());
	}

	private Integer getCurrentProcessId() {
		String segment = ManagementFactory.getRuntimeMXBean().getName().split("@")[0]; //$NON-NLS-1$
		try {
			return Integer.valueOf(segment);
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private void logMessage(Message message) {
		if (message instanceof ResponseMessage && ((ResponseMessage) message).getError() != null
				&& ((ResponseMessage) message).getId() == Integer
						.toString(ResponseErrorCode.RequestCancelled.getValue())) {
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
		this.serverCapabilities = null;
		this.dynamicRegistrations.clear();

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

	public void connect(@NonNull IFile file, IDocument document) throws IOException {
		watchProject(file.getProject(), false);
		connect(file.getLocation(), document);
	}

	protected synchronized void watchProject(IProject project, boolean isInitializationRootProject) {
		if (this.allWatchedProjects.contains(project)) {
			return;
		}
		if (isInitializationRootProject && !this.allWatchedProjects.isEmpty()) {
			return; // there can be only one root project
		}
		if (!isInitializationRootProject && !supportsWorkspaceFolderCapability()) {
			// multi project and WorkspaceFolder notifications not supported by this server
			// instance
			return;
		}
		this.allWatchedProjects.add(project);
		project.getWorkspace().addResourceChangeListener(event -> {
			if (project.equals(event.getResource()) && (event.getDelta().getKind() == IResourceDelta.MOVED_FROM
					|| event.getDelta().getKind() == IResourceDelta.REMOVED)) {
				unwatchProject(project);
			}
		}, IResourceChangeEvent.POST_CHANGE);
		if (supportsWorkspaceFolderCapability()) {
			WorkspaceFoldersChangeEvent event = new WorkspaceFoldersChangeEvent();
			event.getAdded().add(LSPEclipseUtils.toWorkspaceFolder(project));
			DidChangeWorkspaceFoldersParams params = new DidChangeWorkspaceFoldersParams();
			params.setEvent(event);
			this.languageServer.getWorkspaceService().didChangeWorkspaceFolders(params);
		}
	}

	private synchronized void unwatchProject(@NonNull IProject project) {
		this.allWatchedProjects.remove(project);
		// TODO? disconnect resources?
		if (supportsWorkspaceFolderCapability()) {
			WorkspaceFoldersChangeEvent event = new WorkspaceFoldersChangeEvent();
			event.getRemoved().add(LSPEclipseUtils.toWorkspaceFolder(project));
			DidChangeWorkspaceFoldersParams params = new DidChangeWorkspaceFoldersParams();
			params.setEvent(event);
			this.languageServer.getWorkspaceService().didChangeWorkspaceFolders(params);
		}
	}

	/**
	 * Check whether this LS is suitable for provided project. Starts the LS if not already started.
	 *
	 * @return whether this language server can operate on the given project
	 * @since 0.5
	 */
	public boolean canOperate(IProject project) {
		if (project.equals(this.initialProject) || this.allWatchedProjects.contains(project)) {
			return true;
		}

		return supportsWorkspaceFolderCapability();
	}

	/**
	 * @return true, if the server supports multi-root workspaces via workspace
	 *         folders
	 * @since 0.6
	 */
	private boolean supportsWorkspaceFolderCapability() {
		if (this.initializeFuture != null) {
			try {
				this.initializeFuture.get(1, TimeUnit.SECONDS);
			} catch (InterruptedException | ExecutionException | TimeoutException e) {
				e.printStackTrace();
			}
		}
		return initiallySupportsWorkspaceFolders || supportsWorkspaceFolders(serverCapabilities);
	}

	/**
	 * To make public when we support non IFiles
	 *
	 * @noreference internal so far
	 */
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
			synchronized (connectedDocuments) {
				if (this.connectedDocuments.containsKey(thePath)) {
					return;
				}
				Either<TextDocumentSyncKind, TextDocumentSyncOptions> syncOptions = initializeFuture == null ? null
						: this.serverCapabilities.getTextDocumentSync();
				TextDocumentSyncKind syncKind = null;
				if (syncOptions != null) {
					if (syncOptions.isRight()) {
						syncKind = syncOptions.getRight().getChange();
					} else if (syncOptions.isLeft()) {
						syncKind = syncOptions.getLeft();
					}
				}
				DocumentContentSynchronizer listener = new DocumentContentSynchronizer(this, theDocument, thePath,
						syncKind);
				theDocument.addDocumentListener(listener);
				LanguageServerWrapper.this.connectedDocuments.put(thePath, listener);
			}
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

	public void disconnectContentType(@NonNull IContentType contentType) {
		List<IPath> pathsToDisconnect = new ArrayList<>();
		for (IPath path : connectedDocuments.keySet()) {
			IFile[] foundFiles = ResourcesPlugin.getWorkspace().getRoot().findFilesForLocationURI(path.toFile().toURI());
			if(foundFiles.length != 0) {
				if (LSPEclipseUtils.getFileContentTypes(foundFiles[0]).stream()
						.anyMatch(foundContentType -> contentType.equals(foundContentType))) {
					pathsToDisconnect.add(path);
				}
			}
		}
		for (IPath path : pathsToDisconnect) {
			disconnect(path);
		}
	}

	/**
	 * checks if the wrapper is already connected to the document at the given path
	 *
	 * @noreference test only
	 */
	public boolean isConnectedTo(IPath location) {
		return connectedDocuments.containsKey(location);
	}

	/**
	 * Starts and returns the language server, regardless of if it is initialized.
	 * If not in the UI Thread, will wait to return the initialized server.
	 *
	 * @deprecated use {@link #getInitializedServer()} instead.
	 */
	@Deprecated
	@Nullable
	public LanguageServer getServer() {
		CompletableFuture<LanguageServer> languagServerFuture = getInitializedServer();
		if (Display.getCurrent() != null) { // UI Thread
			return this.languageServer;
		} else {
			return languagServerFuture.join();
		}
	}

	/**
	 * Starts the language server and returns a CompletableFuture waiting for the
	 * server to be initialized. If done in the UI stream, a job will be created
	 * displaying that the server is being initialized
	 */
	@NonNull
	public CompletableFuture<LanguageServer> getInitializedServer() {
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
			}
			return initializeFuture.thenApply(r -> this.languageServer);
		}
		return CompletableFuture.completedFuture(this.languageServer);
	}

	/**
	 * Warning: this is a long running operation
	 *
	 * @return the server capabilities, or null if initialization job didn't complete
	 */
	@Nullable
	public ServerCapabilities getServerCapabilities() {
		try {
			getInitializedServer().get(10, TimeUnit.SECONDS);
		} catch (TimeoutException e) {
			LanguageServerPlugin.logError("LanguageServer not initialized after 10s", e); //$NON-NLS-1$
		} catch (InterruptedException | ExecutionException e) {
			LanguageServerPlugin.logError(e);
		}

		return this.serverCapabilities;
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

	void registerCapability(RegistrationParams params) {
		params.getRegistrations().forEach(reg -> {
			if ("workspace/didChangeWorkspaceFolders".equals(reg.getMethod())) { //$NON-NLS-1$
				Assert.isNotNull(serverCapabilities,
						"Dynamic capability registration failed! Server not yet initialized?"); //$NON-NLS-1$
				if (initiallySupportsWorkspaceFolders) {
					// Can treat this as a NOP since nothing can disable it dynamically if it was
					// enabled on initialization.
				} else if (supportsWorkspaceFolders(serverCapabilities)) {
					LanguageServerPlugin.logWarning(
							"Dynamic registration of 'workspace/didChangeWorkspaceFolders' ignored. It was already enabled before", //$NON-NLS-1$
							null);
				} else {
					addRegistration(reg, () -> setWorkspaceFoldersEnablement(false));
					setWorkspaceFoldersEnablement(true);
				}
			} else if ("workspace/executeCommand".equals(reg.getMethod())) { //$NON-NLS-1$
				Gson gson = new Gson(); // TODO? retrieve the GSon used by LS
				ExecuteCommandOptions executeCommandOptions = gson.fromJson((JsonObject) reg.getRegisterOptions(),
						ExecuteCommandOptions.class);
				List<String> newCommands = executeCommandOptions.getCommands();
				if (!newCommands.isEmpty()) {
					addRegistration(reg, () -> unregisterCommands(newCommands));
					registerCommands(newCommands);
				}
			}
		});
	}

	private void addRegistration(@NonNull Registration reg, @NonNull Runnable unregistrationHandler) {
		String regId = reg.getId();
		synchronized (dynamicRegistrations) {
			Assert.isLegal(!dynamicRegistrations.containsKey(regId), "Registration id is not unique"); //$NON-NLS-1$
			dynamicRegistrations.put(regId, unregistrationHandler);
		}
	}

	synchronized void setWorkspaceFoldersEnablement(boolean enable) {
		if (serverCapabilities == null) {
			this.serverCapabilities = new ServerCapabilities();
		}
		WorkspaceServerCapabilities workspace = serverCapabilities.getWorkspace();
		if (workspace == null) {
			serverCapabilities.setWorkspace(workspace = new WorkspaceServerCapabilities());
		}
		WorkspaceFoldersOptions folders = workspace.getWorkspaceFolders();
		if (folders == null) {
			workspace.setWorkspaceFolders(folders = new WorkspaceFoldersOptions());
		}
		folders.setSupported(enable);
	}

	synchronized void registerCommands(List<String> newCommands) {
		ServerCapabilities caps = this.getServerCapabilities();
		if (caps != null) {
			ExecuteCommandOptions commandProvider = caps.getExecuteCommandProvider();
			if (commandProvider == null) {
				caps.setExecuteCommandProvider(commandProvider = new ExecuteCommandOptions(new ArrayList<>()));
			}
			List<String> existingCommands = commandProvider.getCommands();
			for (String newCmd : newCommands) {
				Assert.isLegal(!existingCommands.contains(newCmd), "Command already registered '" + newCmd + "'"); //$NON-NLS-1$ //$NON-NLS-2$
				existingCommands.add(newCmd);
			}
		} else {
			throw new IllegalStateException("Dynamic command registration failed! Server not yet initialized?"); //$NON-NLS-1$
		}
	}

	void unregisterCapability(UnregistrationParams params) {
		params.getUnregisterations().forEach(reg -> {
			String id = reg.getId();
			Runnable unregistrator;
			synchronized (dynamicRegistrations) {
				unregistrator = dynamicRegistrations.get(id);
				dynamicRegistrations.remove(id);
			}
			if (unregistrator != null) {
				unregistrator.run();
			}
		});
	}

	void unregisterCommands(List<String> cmds) {
		ServerCapabilities caps = this.getServerCapabilities();
		if (caps != null) {
			ExecuteCommandOptions commandProvider = caps.getExecuteCommandProvider();
			if (commandProvider != null) {
				List<String> existingCommands = commandProvider.getCommands();
				existingCommands.removeAll(cmds);
			}
		}
	}

	int getVersion(IFile file) {
		if (file != null && file.getLocation() != null) {
			DocumentContentSynchronizer documentContentSynchronizer = connectedDocuments.get(file.getLocation());
			if (documentContentSynchronizer != null) {
				return documentContentSynchronizer.getVersion();
			}
		}
		return -1;
	}

}