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
 *  Miro Spoenemann (TypeFox) - extracted LanguageClientImpl
 *  Jan Koehnlein (TypeFox) - bug 521744
 *  Martin Lippert (Pivotal, Inc.) - bug 531030, 527902, 534637
 *  Kris De Volder (Pivotal, Inc.) - dynamic command registration
 *  Tamas Miklossy (itemis) - bug 571162
 *  Rubén Porras Campo (Avaloq Evolution AG) - documentAboutToBeSaved implementation
 *******************************************************************************/
package org.eclipse.lsp4e;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.IFileBuffer;
import org.eclipse.core.filebuffers.IFileBufferListener;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
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
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.ClientInfo;
import org.eclipse.lsp4j.CodeActionCapabilities;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionKindCapabilities;
import org.eclipse.lsp4j.CodeActionLiteralSupportCapabilities;
import org.eclipse.lsp4j.CodeActionOptions;
import org.eclipse.lsp4j.CodeActionResolveSupportCapabilities;
import org.eclipse.lsp4j.CodeLensCapabilities;
import org.eclipse.lsp4j.ColorProviderCapabilities;
import org.eclipse.lsp4j.CompletionCapabilities;
import org.eclipse.lsp4j.CompletionItemCapabilities;
import org.eclipse.lsp4j.CompletionItemInsertTextModeSupportCapabilities;
import org.eclipse.lsp4j.CompletionItemResolveSupportCapabilities;
import org.eclipse.lsp4j.DefinitionCapabilities;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.DocumentFormattingOptions;
import org.eclipse.lsp4j.DocumentHighlightCapabilities;
import org.eclipse.lsp4j.DocumentLinkCapabilities;
import org.eclipse.lsp4j.DocumentRangeFormattingOptions;
import org.eclipse.lsp4j.DocumentSymbolCapabilities;
import org.eclipse.lsp4j.ExecuteCommandCapabilities;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.FailureHandlingKind;
import org.eclipse.lsp4j.FoldingRangeCapabilities;
import org.eclipse.lsp4j.FormattingCapabilities;
import org.eclipse.lsp4j.HoverCapabilities;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.InlayHintCapabilities;
import org.eclipse.lsp4j.InsertTextMode;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.RangeFormattingCapabilities;
import org.eclipse.lsp4j.ReferencesCapabilities;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.RenameCapabilities;
import org.eclipse.lsp4j.ResourceOperationKind;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.ShowDocumentCapabilities;
import org.eclipse.lsp4j.SignatureHelpCapabilities;
import org.eclipse.lsp4j.SymbolCapabilities;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.SymbolKindCapabilities;
import org.eclipse.lsp4j.SynchronizationCapabilities;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.TypeDefinitionCapabilities;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.WindowClientCapabilities;
import org.eclipse.lsp4j.WindowShowMessageRequestCapabilities;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.WorkspaceEditCapabilities;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent;
import org.eclipse.lsp4j.WorkspaceFoldersOptions;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;

import com.google.common.base.Functions;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class LanguageServerWrapper implements ILSWrapper {

	private final IFileBufferListener fileBufferListener = new FileBufferListenerAdapter() {
		@Override
		public void bufferDisposed(IFileBuffer buffer) {
			disconnect(LSPEclipseUtils.toUri(buffer));
		}

		@Override
		public void stateChanging(IFileBuffer buffer) {
			if (buffer.isDirty()) {
				DocumentContentSynchronizer documentListener = connectedDocuments.get(LSPEclipseUtils.toUri(buffer));
				if (documentListener != null) {
					documentListener.documentAboutToBeSaved();
				}
			}
		}

		@Override
		public void dirtyStateChanged(IFileBuffer buffer, boolean isDirty) {
			if (isDirty) {
				return;
			}
			DocumentContentSynchronizer documentListener = connectedDocuments.get(LSPEclipseUtils.toUri(buffer));
			if (documentListener != null) {
				documentListener.documentSaved(buffer);
			}
		}

	};

	@NonNull
	public final LanguageServerDefinition serverDefinition;
	@Nullable
	protected final IProject initialProject;
	@NonNull
	protected Map<@NonNull URI, @NonNull DocumentContentSynchronizer> connectedDocuments;
	@Nullable
	protected final IPath initialPath;
	protected final InitializeParams initParams = new InitializeParams();

	protected StreamConnectionProvider lspStreamProvider;
	private Future<?> launcherFuture;
	private CompletableFuture<Void> initializeFuture;
	private LanguageServer languageServer;
	private LanguageClientImpl languageClient;
	private ServerCapabilities serverCapabilities;
	private Timer timer;
	private AtomicBoolean stopping = new AtomicBoolean(false);

	private final ExecutorService dispatcher;

	private final ExecutorService listener;

	/**
	 * Map containing unregistration handlers for dynamic capability registrations.
	 */
	private final @NonNull Map<@NonNull String, @NonNull Runnable> dynamicRegistrations = new HashMap<>();
	private boolean initiallySupportsWorkspaceFolders = false;
	private final @NonNull IResourceChangeListener workspaceFolderUpdater = new WorkspaceFolderListener();

	/* Backwards compatible constructor */
	public LanguageServerWrapper(@NonNull IProject project, @NonNull LanguageServerDefinition serverDefinition) {
		this(project, serverDefinition, null);
	}

	public LanguageServerWrapper(@NonNull LanguageServerDefinition serverDefinition, @Nullable IPath initialPath) {
		this(null, serverDefinition, initialPath);
	}

	/** Unified private constructor to set sensible defaults in all cases */
	private LanguageServerWrapper(@Nullable IProject project, @NonNull LanguageServerDefinition serverDefinition,
			@Nullable IPath initialPath) {
		this.initialProject = project;
		this.initialPath = initialPath;
		this.serverDefinition = serverDefinition;
		this.connectedDocuments = new HashMap<>();
		String projectName = (project != null && project.getName() != null && !serverDefinition.isSingleton) ? ("@" + project.getName()) : "";  //$NON-NLS-1$//$NON-NLS-2$
		String dispatcherThreadNameFormat = "LS-" + serverDefinition.id + projectName + "#dispatcher"; //$NON-NLS-1$ //$NON-NLS-2$
		this.dispatcher = Executors
				.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(dispatcherThreadNameFormat).build());

		// Executor service passed through to the LSP4j layer when we attempt to start the LS. It will be used
		// to create a listener that sits on the input stream and processes inbound messages (responses, or server-initiated
		// requests).
		String listenerThreadNameFormat = "LS-" + serverDefinition.id + projectName + "#listener-%d"; //$NON-NLS-1$ //$NON-NLS-2$
		this.listener = Executors
				.newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat(listenerThreadNameFormat).build());
	}

	void stopDispatcher() {
		this.dispatcher.shutdownNow();

		// Only really needed for testing - the listener (an instance of ConcurrentMessageProcessor) should exit
		// as soon as the input stream from the LS is closed, and a cached thread pool will recycle idle
		// threads after a 60 second timeout - or immediately in response to JVM shutdown.
		// If we don't do this then a full test run will generate a lot of threads because we create new
		// instances of this class for each test
		this.listener.shutdownNow();
	}

	/**
	 * @return the workspace folder to be announced to the language server
	 */
	private List<WorkspaceFolder> getRelevantWorkspaceFolders() {
		final var languageClient = this.languageClient;
		List<WorkspaceFolder> folders = null;
		if (languageClient != null) {
			try {
				folders = languageClient.workspaceFolders().get(5, TimeUnit.SECONDS);
			} catch (final ExecutionException | TimeoutException ex) {
				LanguageServerPlugin.logError(ex);
			} catch (final InterruptedException ex) {
				LanguageServerPlugin.logError(ex);
				Thread.currentThread().interrupt();
			}
		}
		if (folders == null) {
			folders = LSPEclipseUtils.getWorkspaceFolders();
		}
		return folders;
	}

	/**
	 * Starts a language server and triggers initialization. If language server is
	 * started and active, does nothing. If language server is inactive, restart it.
	 *
	 * @throws IOException
	 */
	public synchronized void start() throws IOException {
		final var filesToReconnect = new HashMap<URI, IDocument>();
		if (this.languageServer != null) {
			if (isActive()) {
				return;
			} else {
				for (Entry<URI, DocumentContentSynchronizer> entry : this.connectedDocuments.entrySet()) {
					filesToReconnect.put(entry.getKey(), entry.getValue().getDocument());
				}
				stop();
			}
		}
		if (this.initializeFuture == null) {
			final URI rootURI = getRootURI();
			this.initializeFuture = CompletableFuture.supplyAsync(() -> {
				if (LoggingStreamConnectionProviderProxy.shouldLog(serverDefinition.id)) {
					this.lspStreamProvider = new LoggingStreamConnectionProviderProxy(
							serverDefinition.createConnectionProvider(), serverDefinition.id);
				} else {
					this.lspStreamProvider = serverDefinition.createConnectionProvider();
				}
				initParams.setInitializationOptions(this.lspStreamProvider.getInitializationOptions(rootURI));
				try {
					lspStreamProvider.start();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
				return null;
			}).thenApply(unused -> {
				languageClient = serverDefinition.createLanguageClient();

				initParams.setProcessId((int) ProcessHandle.current().pid());

				if (rootURI != null) {
					initParams.setRootUri(rootURI.toString());
					initParams.setRootPath(rootURI.getPath());
				}

				UnaryOperator<MessageConsumer> wrapper = consumer -> (message -> {
					logMessage(message);
					consumer.consume(message);
					final StreamConnectionProvider currentConnectionProvider = this.lspStreamProvider;
					if (currentConnectionProvider != null && isActive()) {
						currentConnectionProvider.handleMessage(message, this.languageServer, rootURI);
					}
				});
				initParams.setWorkspaceFolders(getRelevantWorkspaceFolders());
				Launcher<LanguageServer> launcher = serverDefinition.createLauncherBuilder() //
						.setLocalService(languageClient)//
						.setRemoteInterface(serverDefinition.getServerInterface())//
						.setInput(lspStreamProvider.getInputStream())//
						.setOutput(lspStreamProvider.getOutputStream())//
						.setExecutorService(listener)//
						.wrapMessages(wrapper)//
						.create();
				this.languageServer = launcher.getRemoteProxy();
				languageClient.connect(languageServer, this);
				this.launcherFuture = launcher.startListening();
				return null;
			}).thenCompose(unused -> {
				String name = "Eclipse IDE"; //$NON-NLS-1$
				if (Platform.getProduct() != null) {
					name = Platform.getProduct().getName();
				}
				final var workspaceClientCapabilities = new WorkspaceClientCapabilities();
				workspaceClientCapabilities.setApplyEdit(Boolean.TRUE);
				workspaceClientCapabilities.setConfiguration(Boolean.TRUE);
				workspaceClientCapabilities.setExecuteCommand(new ExecuteCommandCapabilities(Boolean.TRUE));
				workspaceClientCapabilities.setSymbol(new SymbolCapabilities(Boolean.TRUE));
				workspaceClientCapabilities.setWorkspaceFolders(Boolean.TRUE);
				WorkspaceEditCapabilities editCapabilities = new WorkspaceEditCapabilities();
				editCapabilities.setDocumentChanges(Boolean.TRUE);
				editCapabilities.setResourceOperations(Arrays.asList(ResourceOperationKind.Create,
						ResourceOperationKind.Delete, ResourceOperationKind.Rename));
				editCapabilities.setFailureHandling(FailureHandlingKind.Undo);
				workspaceClientCapabilities.setWorkspaceEdit(editCapabilities);
				final var textDocumentClientCapabilities = new TextDocumentClientCapabilities();
				final var codeAction = new CodeActionCapabilities(new CodeActionLiteralSupportCapabilities(
						new CodeActionKindCapabilities(Arrays.asList(CodeActionKind.QuickFix, CodeActionKind.Refactor,
								CodeActionKind.RefactorExtract, CodeActionKind.RefactorInline,
								CodeActionKind.RefactorRewrite, CodeActionKind.Source,
								CodeActionKind.SourceOrganizeImports))),
						true);
				codeAction.setDataSupport(true);
				codeAction.setResolveSupport(new CodeActionResolveSupportCapabilities(List.of("edit"))); //$NON-NLS-1$
				textDocumentClientCapabilities.setCodeAction(codeAction);
				textDocumentClientCapabilities.setCodeLens(new CodeLensCapabilities());
				textDocumentClientCapabilities.setInlayHint(new InlayHintCapabilities());
				textDocumentClientCapabilities.setColorProvider(new ColorProviderCapabilities());
				final var completionItemCapabilities = new CompletionItemCapabilities(Boolean.TRUE);
				completionItemCapabilities
						.setDocumentationFormat(Arrays.asList(MarkupKind.MARKDOWN, MarkupKind.PLAINTEXT));
				completionItemCapabilities.setInsertTextModeSupport(new CompletionItemInsertTextModeSupportCapabilities(List.of(InsertTextMode.AsIs, InsertTextMode.AdjustIndentation)));
				completionItemCapabilities.setResolveSupport(new CompletionItemResolveSupportCapabilities(List.of("documentation", "detail", "additionalTextEdits"))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				textDocumentClientCapabilities.setCompletion(new CompletionCapabilities(completionItemCapabilities));
				final var definitionCapabilities = new DefinitionCapabilities();
				definitionCapabilities.setLinkSupport(Boolean.TRUE);
				textDocumentClientCapabilities.setDefinition(definitionCapabilities);
				final var typeDefinitionCapabilities = new TypeDefinitionCapabilities();
				typeDefinitionCapabilities.setLinkSupport(Boolean.TRUE);
				textDocumentClientCapabilities.setTypeDefinition(typeDefinitionCapabilities);
				textDocumentClientCapabilities.setDocumentHighlight(new DocumentHighlightCapabilities());
				textDocumentClientCapabilities.setDocumentLink(new DocumentLinkCapabilities());
				final var documentSymbol = new DocumentSymbolCapabilities();
				documentSymbol.setHierarchicalDocumentSymbolSupport(true);
				documentSymbol.setSymbolKind(new SymbolKindCapabilities(Arrays.asList(SymbolKind.Array,
						SymbolKind.Boolean, SymbolKind.Class, SymbolKind.Constant, SymbolKind.Constructor,
						SymbolKind.Enum, SymbolKind.EnumMember, SymbolKind.Event, SymbolKind.Field, SymbolKind.File,
						SymbolKind.Function, SymbolKind.Interface, SymbolKind.Key, SymbolKind.Method, SymbolKind.Module,
						SymbolKind.Namespace, SymbolKind.Null, SymbolKind.Number, SymbolKind.Object,
						SymbolKind.Operator, SymbolKind.Package, SymbolKind.Property, SymbolKind.String,
						SymbolKind.Struct, SymbolKind.TypeParameter, SymbolKind.Variable)));
				textDocumentClientCapabilities.setDocumentSymbol(documentSymbol);
				textDocumentClientCapabilities.setFoldingRange(new FoldingRangeCapabilities());
				textDocumentClientCapabilities.setFormatting(new FormattingCapabilities(Boolean.TRUE));
				final var hoverCapabilities = new HoverCapabilities();
				hoverCapabilities.setContentFormat(Arrays.asList(MarkupKind.MARKDOWN, MarkupKind.PLAINTEXT));
				textDocumentClientCapabilities.setHover(hoverCapabilities);
				textDocumentClientCapabilities.setOnTypeFormatting(null); // TODO
				textDocumentClientCapabilities.setRangeFormatting(new RangeFormattingCapabilities());
				textDocumentClientCapabilities.setReferences(new ReferencesCapabilities());
				final var renameCapabilities = new RenameCapabilities();
				renameCapabilities.setPrepareSupport(true);
				textDocumentClientCapabilities.setRename(renameCapabilities);
				textDocumentClientCapabilities.setSignatureHelp(new SignatureHelpCapabilities());
				textDocumentClientCapabilities
						.setSynchronization(new SynchronizationCapabilities(Boolean.TRUE, Boolean.TRUE, Boolean.TRUE));

				WindowClientCapabilities windowClientCapabilities = getWindowClientCapabilities();
				initParams.setCapabilities(new ClientCapabilities(
						workspaceClientCapabilities,
						textDocumentClientCapabilities,
						windowClientCapabilities,
						lspStreamProvider.getExperimentalFeaturesPOJO()));
				initParams.setClientInfo(getClientInfo(name));
				initParams.setTrace(this.lspStreamProvider.getTrace(rootURI));

				// no then...Async future here as we want this chain of operation to be sequential and "atomic"-ish
				return languageServer.initialize(initParams);
			}).thenAccept(res -> {
				serverCapabilities = res.getCapabilities();
				this.initiallySupportsWorkspaceFolders = supportsWorkspaceFolders(serverCapabilities);
			}).thenRun(() -> {
				this.languageServer.initialized(new InitializedParams());
			}).thenRun(() -> {
				final Map<URI, IDocument> toReconnect = filesToReconnect;
				initializeFuture.thenRunAsync(() -> {
					watchProjects();
					for (Entry<URI, IDocument> fileToReconnect : toReconnect.entrySet()) {
						try {
							connect(fileToReconnect.getKey(), fileToReconnect.getValue());
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
					}
				});
				FileBuffers.getTextFileBufferManager().addFileBufferListener(fileBufferListener);
			}).exceptionally(e -> {
				LanguageServerPlugin.logError(e);
				initializeFuture.completeExceptionally(e);
				stop();
				return null;
			});
		}
	}

	private ClientInfo getClientInfo(String name) {
		String pluginVersion = Platform.getBundle(LanguageServerPlugin.PLUGIN_ID).getVersion().toString();
		final var clientInfo = new ClientInfo(name, pluginVersion);
		return clientInfo;
	}

	private WindowClientCapabilities getWindowClientCapabilities() {
		final var windowClientCapabilities = new WindowClientCapabilities();
		windowClientCapabilities.setShowDocument(new ShowDocumentCapabilities(true));
		windowClientCapabilities.setWorkDoneProgress(true);
		windowClientCapabilities.setShowMessage(new WindowShowMessageRequestCapabilities());
		return windowClientCapabilities;
	}

	@Nullable
	private URI getRootURI() {
		final IProject project = this.initialProject;
		if (project != null && project.exists()) {
			return LSPEclipseUtils.toUri(project);
		}

		final IPath path = this.initialPath;
		if (path != null) {
			File projectDirectory = path.toFile();
			if (projectDirectory.isFile()) {
				projectDirectory = projectDirectory.getParentFile();
			}
			return LSPEclipseUtils.toUri(projectDirectory);
		}
		return null;
	}

	private static boolean supportsWorkspaceFolders(ServerCapabilities serverCapabilities) {
		return serverCapabilities != null && serverCapabilities.getWorkspace() != null
				&& serverCapabilities.getWorkspace().getWorkspaceFolders() != null
				&& Boolean.TRUE.equals(serverCapabilities.getWorkspace().getWorkspaceFolders().getSupported());
	}

	private void logMessage(Message message) {
		if (message instanceof ResponseMessage responseMessage && responseMessage.getError() != null
				&& responseMessage.getId()
						.equals(Integer.toString(ResponseErrorCode.RequestCancelled.getValue()))) {
			LanguageServerPlugin.logError(new ResponseErrorException(responseMessage.getError()));
		} else if (LanguageServerPlugin.DEBUG) {
			LanguageServerPlugin.logInfo(message.getClass().getSimpleName() + '\n' + message);
		}
	}

	/**
	 * @return whether the underlying connection to language server is still active
	 */
	@Override
	public boolean isActive() {
		return this.launcherFuture != null && !this.launcherFuture.isDone() && !this.launcherFuture.isCancelled();
	}

	private void removeStopTimer() {
		if (timer != null) {
			timer.cancel();
			timer = null;
		}
	}

	private void startStopTimer() {
		timer = new Timer("Stop Language Server Timer"); //$NON-NLS-1$

		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				stop();
			}
		}, TimeUnit.SECONDS.toMillis(this.serverDefinition.lastDocumentDisconnectedTimeout));
	}

	/**
	 * Internal hook so that the unwrapped remote proxy can be matched to the corresponding
	 * wrapper, which tracks things like whether it is still running or not
	 * @param server LanguageServer to match on
	 * @return True if this is the wrapper for the given server
	 */
	boolean isWrapperFor(LanguageServer server) {
		return server == this.languageServer;
	}

	public synchronized void stop() {
		final boolean alreadyStopping = this.stopping.getAndSet(true);
		if (alreadyStopping) {
			return;
		}
		removeStopTimer();
		if (this.initializeFuture != null) {
			this.initializeFuture.cancel(true);
			this.initializeFuture = null;
		}

		this.serverCapabilities = null;
		this.dynamicRegistrations.clear();

		final Future<?> serverFuture = this.launcherFuture;
		final StreamConnectionProvider provider = this.lspStreamProvider;
		final LanguageServer languageServerInstance = this.languageServer;
		ResourcesPlugin.getWorkspace().removeResourceChangeListener(workspaceFolderUpdater);

		Runnable shutdownKillAndStopFutureAndProvider = () -> {
			if (languageServerInstance != null) {
				CompletableFuture<Object> shutdown = languageServerInstance.shutdown();
				try {
					shutdown.get(5, TimeUnit.SECONDS);
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				} catch (Exception ex) {
					LanguageServerPlugin.logError(ex);
				}
			}

			if (serverFuture != null) {
				serverFuture.cancel(true);
			}

			if (languageServerInstance != null) {
				languageServerInstance.exit();
			}

			if (provider != null) {
				provider.stop();
			}
			this.stopping.set(false);
		};

		CompletableFuture.runAsync(shutdownKillAndStopFutureAndProvider);

		this.launcherFuture = null;
		this.lspStreamProvider = null;

		while (!this.connectedDocuments.isEmpty()) {
			disconnect(this.connectedDocuments.keySet().iterator().next());
		}
		this.languageServer = null;

		FileBuffers.getTextFileBufferManager().removeFileBufferListener(fileBufferListener);
	}

	/**
	 * @param file
	 * @param document
	 * @return null if not connection has happened, a future tracking the connection state otherwise
	 * @throws IOException
	 */
	public @Nullable CompletableFuture<LanguageServer> connect(@NonNull IFile file, IDocument document)
			throws IOException {
		final URI uri = LSPEclipseUtils.toUri(file);
		return uri == null ? null : connect(uri, document);
	}

	/**
	 * @param document
	 * @return null if not connection has happened, a future tracking the connection state otherwise
	 * @throws IOException
	 */
	public @Nullable CompletableFuture<LanguageServer> connect(IDocument document) throws IOException {
		IFile file = LSPEclipseUtils.getFile(document);

		if (file != null && file.exists()) {
			return connect(file, document);
		}

		final URI uri = LSPEclipseUtils.toUri(document);
		return uri == null ? null : connect(uri, document);
	}

	private void watchProjects() {
		if (!supportsWorkspaceFolderCapability()) {
			return;
		}
		final LanguageServer currentLS = this.languageServer;
		new WorkspaceJob("Setting watch projects on server " + serverDefinition.label) { //$NON-NLS-1$
			@Override
			public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
				WorkspaceFoldersChangeEvent wsFolderEvent = new WorkspaceFoldersChangeEvent();
				wsFolderEvent.getAdded().addAll(getRelevantWorkspaceFolders());
				if (currentLS != null && currentLS == LanguageServerWrapper.this.languageServer) {
					currentLS.getWorkspaceService()
							.didChangeWorkspaceFolders(new DidChangeWorkspaceFoldersParams(wsFolderEvent));
				}
				ResourcesPlugin.getWorkspace().addResourceChangeListener(workspaceFolderUpdater,
						IResourceChangeEvent.POST_CHANGE | IResourceChangeEvent.PRE_DELETE);
				return Status.OK_STATUS;
			}
		}.schedule();
	}

	/**
	 * Check whether this LS is suitable for provided project. Starts the LS if not
	 * already started.
	 *
	 * @return whether this language server can operate on the given project
	 * @since 0.5
	 */
	@Override
	public boolean canOperate(@NonNull IProject project) {
		return project.equals(this.initialProject) || serverDefinition.isSingleton || supportsWorkspaceFolderCapability();
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
			} catch (ExecutionException e) {
				LanguageServerPlugin.logError(e);
			} catch (InterruptedException e) {
				LanguageServerPlugin.logError(e);
				Thread.currentThread().interrupt();
			} catch (TimeoutException e) {
				LanguageServerPlugin.logWarning("Could not get if the workspace folder capability is supported due to timeout after 1 second", e); //$NON-NLS-1$
			}
		}
		return initiallySupportsWorkspaceFolders || supportsWorkspaceFolders(serverCapabilities);
	}

	/**
	 * To make public when we support non IFiles
	 *
	 * @return null if not connection has happened, a future that completes when file is initialized otherwise
	 * @noreference internal so far
	 */
	private CompletableFuture<LanguageServer> connect(@NonNull URI uri, IDocument document) throws IOException {
		removeStopTimer();
		if (this.connectedDocuments.containsKey(uri)) {
			return CompletableFuture.completedFuture(languageServer);
		}
		start();
		if (this.initializeFuture == null) {
			return null;
		}
		if (document == null) {
			final var docFile = (IFile) LSPEclipseUtils.findResourceFor(uri);
			document = LSPEclipseUtils.getDocument(docFile);
		}
		if (document == null) {
			return null;
		}
		final IDocument theDocument = document;
		return initializeFuture.thenAcceptAsync(theVoid -> {
			synchronized (connectedDocuments) {
				if (this.connectedDocuments.containsKey(uri)) {
					return;
				}
				TextDocumentSyncKind syncKind = initializeFuture == null ? null
						: serverCapabilities.getTextDocumentSync().map(Functions.identity(), TextDocumentSyncOptions::getChange);
				final var listener = new DocumentContentSynchronizer(this, languageServer, theDocument, syncKind);
				theDocument.addDocumentListener(listener);
				LanguageServerWrapper.this.connectedDocuments.put(uri, listener);
			}
		}).thenApply(theVoid -> languageServer);
	}

	/**
	 * @param uri
	 * @return null if not disconnection has happened, a future tracking the disconnection state otherwise
	 */
	public CompletableFuture<Void> disconnect(URI uri) {
		DocumentContentSynchronizer documentListener = this.connectedDocuments.remove(uri);
		CompletableFuture<Void> documentClosedFuture = null;
		if (documentListener != null) {
			documentListener.getDocument().removeDocumentListener(documentListener);
			documentClosedFuture = documentListener.documentClosed();
		}
		if (this.connectedDocuments.isEmpty()) {
			if (this.serverDefinition.lastDocumentDisconnectedTimeout != 0) {
				removeStopTimer();
				startStopTimer();
			} else {
				stop();
			}
		}
		return documentClosedFuture;
	}

	public void disconnectContentType(@NonNull IContentType contentType) {
		final var urisToDisconnect = new ArrayList<URI>();
		for (URI uri : connectedDocuments.keySet()) {
			IFile[] foundFiles = ResourcesPlugin.getWorkspace().getRoot()
					.findFilesForLocationURI(uri);
			if (foundFiles.length != 0
					&& LSPEclipseUtils.getFileContentTypes(foundFiles[0]).stream().anyMatch(contentType::equals)) {
				urisToDisconnect.add(uri);
			}
		}
		for (URI uri : urisToDisconnect) {
			disconnect(uri);
		}
	}

	/**
	 * checks if the wrapper is already connected to the document at the given uri
	 *
	 * @noreference test only
	 */
	public boolean isConnectedTo(URI uri) {
		return connectedDocuments.containsKey(uri);
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
	 * Starts the language server, ensure it's and returns a CompletableFuture waiting for the
	 * server to be initialized and up-to-date (all related pending document changes
	 * notifications are sent).
	 * <p>If done in the UI stream, a job will be created
	 * displaying that the server is being initialized</p>
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
				final var waitForInitialization = new Job(Messages.initializeLanguageServer_job) {
					@Override
					protected IStatus run(IProgressMonitor monitor) {
						initializeFuture.join();
						return Status.OK_STATUS;
					}
				};
				waitForInitialization.setUser(true);
				waitForInitialization.setSystem(false);
				PlatformUI.getWorkbench().getProgressService().showInDialog(UI.getActiveShell(), waitForInitialization);
			}
			if (initializeFuture != null) {
				return initializeFuture.thenApply(r -> this.languageServer);
			}
		}
		return CompletableFuture.completedFuture(this.languageServer);
	}

	/**
	 * Sends a notification to the wrapped language server
	 *
	 * @param fn LS notification to send
	 */
	@Override
	public void sendNotification(@NonNull Consumer<LanguageServer> fn) {
		// Enqueues a notification on the dispatch thread associated with the wrapped language server. This
		// ensures the interleaving of document updates and other requests in the UI is mirrored in the
		// order in which they get dispatched to the server
		getInitializedServer().thenAcceptAsync(fn, this.dispatcher);
	}

	@Override
	/**
	 * Runs a request on the language server
	 *
	 * @param <T> LS response type
	 * @param fn Code block that will be supplied the LS in a state where it is guaranteed to have been initialized
	 *
	 * @return Async result
	 */
	@NonNull
	public <T> CompletableFuture<T> execute(@NonNull Function<LanguageServer, ? extends CompletionStage<T>> fn) {
		// Send the request on the dispatch thread, then additionally make sure the response is delivered
		// on a thread from the default ForkJoinPool. This makes sure the user can't chain on an arbitrary
		// long-running block of code that would tie up the server response listener and prevent any more
		// inbound messages being read
		return executeImpl(fn).thenApplyAsync(Function.identity());
	}

	/**
	 * Runs a request on the language server. Internal hook for the LSPexecutor implementations
	 *
	 * @param <T> LS response type
	 * @param fn LS method to invoke
	 * @return Async result
	 */
	@NonNull
	 <T> CompletableFuture<T> executeImpl(@NonNull Function<LanguageServer, ? extends CompletionStage<T>> fn) {
		// Run the supplied function, ensuring that it is enqueued on the dispatch thread associated with the
		// wrapped language server, and is thus guarannteed to be seen in the correct order with respect
		// to e.g. previous document changes
		//
		// Note this doesn't get the .thenApplyAsync(Function.identity()) chained on additionally, unlike
		// the public-facing version of this method, because we trust the LSPExecutor implementations to
		// make sure the server response thread doesn't get blocked by any further work
		return getInitializedServer().thenComposeAsync(fn, this.dispatcher);
	}

	/**
	 * Test whether this server supports the requested <code>ServerCapabilities</code>, and ensure
	 * that it is connected to the document if so.
	 *
	 * NB result is a future on this <emph>wrapper</emph> rather than the wrapped language server directly,
	 * to support accessing the server on the single-threaded dispatch queue.
	 * @param document Document to connect
	 * @param filter Constraint on server capabilities
	 * @return Async result that guarantees the wrapped server will be active and connected to the document. Wraps
	 * null if the server does not support the requested capabilities or could not be started.
	 */
	@NonNull CompletableFuture<@Nullable LanguageServerWrapper> connectIf(@NonNull IDocument document, @NonNull Predicate<ServerCapabilities> filter) {
		return getInitializedServer().thenCompose(server -> {
			if (server != null && (filter == null || filter.test(getServerCapabilities()))) {
				try {
					return connect(document);
				} catch (IOException ex) {
					LanguageServerPlugin.logError(ex);
				}
			}
			return CompletableFuture.completedFuture(null);
		}).thenApply(server -> server == null ? null : this);
	}

	@Override
	/**
	 * Warning: this is a long running operation
	 *
	 * @return the server capabilities, or null if initialization job didn't
	 *         complete
	 */
	@Nullable
	public ServerCapabilities getServerCapabilities() {
		try {
			getInitializedServer().get(10, TimeUnit.SECONDS);
		} catch (TimeoutException e) {
			LanguageServerPlugin.logError("LanguageServer not initialized within 10s", e); //$NON-NLS-1$
		} catch (ExecutionException | CancellationException e) {
			LanguageServerPlugin.logError(e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			LanguageServerPlugin.logError(e);
		}

		return this.serverCapabilities;
	}

	@Override
	/**
	 * @return The language ID that this wrapper is dealing with if defined in the
	 *         content type mapping for the language server
	 */
	@Nullable
	public String getLanguageId(IContentType[] contentTypes) {
		for (IContentType contentType : contentTypes) {
			String languageId = serverDefinition.languageIdMappings.get(contentType);
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
				final var gson = new Gson(); // TODO? retrieve the GSon used by LS
				ExecuteCommandOptions executeCommandOptions = gson.fromJson((JsonObject) reg.getRegisterOptions(),
						ExecuteCommandOptions.class);
				List<String> newCommands = executeCommandOptions.getCommands();
				if (!newCommands.isEmpty()) {
					addRegistration(reg, () -> unregisterCommands(newCommands));
					registerCommands(newCommands);
				}
			} else if ("textDocument/formatting".equals(reg.getMethod())) { //$NON-NLS-1$
				Either<Boolean, DocumentFormattingOptions> documentFormattingProvider = serverCapabilities
						.getDocumentFormattingProvider();
				if (documentFormattingProvider == null || documentFormattingProvider.isLeft()) {
					serverCapabilities.setDocumentFormattingProvider(Boolean.TRUE);
					addRegistration(reg, () -> serverCapabilities.setDocumentFormattingProvider(documentFormattingProvider));
				} else {
					serverCapabilities.setDocumentFormattingProvider(documentFormattingProvider.getRight());
					addRegistration(reg, () -> serverCapabilities.setDocumentFormattingProvider(documentFormattingProvider));
				}
			} else if ("textDocument/rangeFormatting".equals(reg.getMethod())) { //$NON-NLS-1$
				Either<Boolean, DocumentRangeFormattingOptions> documentRangeFormattingProvider = serverCapabilities
						.getDocumentRangeFormattingProvider();
				if (documentRangeFormattingProvider == null || documentRangeFormattingProvider.isLeft()) {
					serverCapabilities.setDocumentRangeFormattingProvider(Boolean.TRUE);
					addRegistration(reg, () -> serverCapabilities
							.setDocumentRangeFormattingProvider(documentRangeFormattingProvider));
				} else {
					serverCapabilities.setDocumentRangeFormattingProvider(documentRangeFormattingProvider.getRight());
					addRegistration(reg, () -> serverCapabilities
							.setDocumentRangeFormattingProvider(documentRangeFormattingProvider));
				}
			} else if ("textDocument/codeAction".equals(reg.getMethod())) { //$NON-NLS-1$
				final Either<Boolean, CodeActionOptions> beforeRegistration = serverCapabilities.getCodeActionProvider();
				serverCapabilities.setCodeActionProvider(Boolean.TRUE);
				addRegistration(reg, () -> serverCapabilities.setCodeActionProvider(beforeRegistration));
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
		if (enable == supportsWorkspaceFolderCapability()) {
			return;
		}
		if (serverCapabilities == null) {
			this.serverCapabilities = new ServerCapabilities();
		}
		WorkspaceServerCapabilities workspace = serverCapabilities.getWorkspace();
		if (workspace == null) {
			workspace = new WorkspaceServerCapabilities();
			serverCapabilities.setWorkspace(workspace);
		}
		WorkspaceFoldersOptions folders = workspace.getWorkspaceFolders();
		if (folders == null) {
			folders = new WorkspaceFoldersOptions();
			workspace.setWorkspaceFolders(folders);
		}
		folders.setSupported(enable);
		if (enable) {
			watchProjects();
		}
	}

	synchronized void registerCommands(List<String> newCommands) {
		ServerCapabilities caps = this.getServerCapabilities();
		if (caps != null) {
			ExecuteCommandOptions commandProvider = caps.getExecuteCommandProvider();
			if (commandProvider == null) {
				commandProvider = new ExecuteCommandOptions(new ArrayList<>());
				caps.setExecuteCommandProvider(commandProvider);
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

	int getVersion(URI uri) {
		DocumentContentSynchronizer documentContentSynchronizer = connectedDocuments.get(uri);
		if (documentContentSynchronizer != null) {
			return documentContentSynchronizer.getVersion();
		}
		return -1;
	}

	public boolean canOperate(@NonNull IDocument document) {
		URI documentUri = LSPEclipseUtils.toUri(document);
		if (documentUri == null) {
			return false;
		}
		if (this.isConnectedTo(documentUri)) {
			return true;
		}
		if (this.initialProject == null && this.connectedDocuments.isEmpty()) {
			return true;
		}
		IFile file = LSPEclipseUtils.getFile(document);
		if (file != null && file.exists() && canOperate(file.getProject())) {
			return true;
		}
		return serverDefinition.isSingleton || supportsWorkspaceFolderCapability();
	}

	/**
	 * Resource listener that translates Eclipse resource events into LSP workspace folder events
	 * and dispatches them if the language server is still active
	 */
	private final class WorkspaceFolderListener implements IResourceChangeListener {
		@Override
		public void resourceChanged(IResourceChangeEvent event) {
			WorkspaceFoldersChangeEvent workspaceFolderEvent = toWorkspaceFolderEvent(event);
			if (workspaceFolderEvent == null
					|| (workspaceFolderEvent.getAdded().isEmpty() && workspaceFolderEvent.getRemoved().isEmpty())) {
				return;
			}
			// If shutting down, language server will be set to null, so ignore the event
			final LanguageServer currentServer = LanguageServerWrapper.this.languageServer;
			if (currentServer != null) {
				currentServer.getWorkspaceService()
						.didChangeWorkspaceFolders(new DidChangeWorkspaceFoldersParams(workspaceFolderEvent));
			}
		}

		private @Nullable WorkspaceFoldersChangeEvent toWorkspaceFolderEvent(IResourceChangeEvent e) {
			if (!isPostChangeEvent(e) && !isPreDeletEvent(e)) {
				return null;
			}

			// If a project delete then the delta is null, but we get the project in the top-level resource
			final var wsFolderEvent = new WorkspaceFoldersChangeEvent();
			if (isPreDeletEvent(e)) {
				final IResource resource = e.getResource();
				if (resource instanceof IProject project) {
					wsFolderEvent.getRemoved().add(LSPEclipseUtils.toWorkspaceFolder(project));
					return wsFolderEvent;
				} else {
					return null;
				}
			}

			// Use the visitor implementation to extract the low-level detail from delta
			var relevantFolders = getRelevantWorkspaceFolders();
			try {
				e.getDelta().accept(delta -> {
					if (delta.getResource() instanceof IProject project) {
						final WorkspaceFolder wsFolder = LSPEclipseUtils.toWorkspaceFolder(project);
						if (relevantFolders.contains(wsFolder)
								&& (isAddEvent(delta) || isProjectOpenCloseEvent(delta))
								&& project.isAccessible()
								&& isValid(wsFolder)) {
							wsFolderEvent.getAdded().add(wsFolder);
						} else if ((isRemoveEvent(delta)|| isProjectOpenCloseEvent(delta))
										&& !project.isAccessible()
										&& isValid(wsFolder)) {
							wsFolderEvent.getRemoved().add(wsFolder);
						}
						// TODO: handle renamed/moved (on filesystem)
					}
					return delta.getResource().getType() == IResource.ROOT;
				});
			} catch (CoreException ex) {
				LanguageServerPlugin.logError(ex);
			}
			if (wsFolderEvent.getAdded().isEmpty() && wsFolderEvent.getRemoved().isEmpty()) {
				return null;
			}
			return wsFolderEvent;
		}

		/**
		 *
		 * @return True if this event is being fired after a change (e.g. a project open/close)
		 */
		private boolean isPostChangeEvent(IResourceChangeEvent e) {
			return e.getType() == IResourceChangeEvent.POST_CHANGE;
		}

		/**
		 *
		 * @return True if this event is being fired prior to a project resource being deleted
		 */
		private boolean isPreDeletEvent(IResourceChangeEvent e) {
			return e.getType() == IResourceChangeEvent.PRE_DELETE;
		}

		/**
		 *
		 * @return True if this delta corresponds to a project resource being added
		 */
		private boolean isAddEvent(IResourceDelta delta) {
			return delta.getKind() == IResourceDelta.ADDED;
		}

		/**
		 *
		 * @return True if this delta corresponds to a project resource being removed
		 */
		private boolean isRemoveEvent(IResourceDelta delta) {
			return delta.getKind() == IResourceDelta.REMOVED;
		}

		/**
		 * Decode the bitmask + enum to work out if this is a project open event
		 *
		 * @param delta
		 * @return True if it is a project open event
		 */
		private boolean isProjectOpenCloseEvent(IResourceDelta delta) {
			return delta.getKind() == IResourceDelta.CHANGED
					&& (delta.getFlags() & IResourceDelta.OPEN) == IResourceDelta.OPEN;
		}

		/**
		 *
		 * @return True if this workspace folder is non-null and has non-empty content
		 */
		private boolean isValid(WorkspaceFolder wsFolder) {
			return wsFolder != null && wsFolder.getUri() != null && !wsFolder.getUri().isEmpty();
		}

	}

}