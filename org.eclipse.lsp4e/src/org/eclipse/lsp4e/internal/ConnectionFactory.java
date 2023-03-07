/*******************************************************************************
 * Copyright (c) 2022-3 Cocotec Ltd and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Ahmed Hussain (Cocotec Ltd) - initial implementation
 *
 *******************************************************************************/
package org.eclipse.lsp4e.internal;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServersRegistry.LanguageServerDefinition;
import org.eclipse.lsp4e.LoggingStreamConnectionProviderProxy;
import org.eclipse.lsp4e.server.StreamConnectionProvider;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.ClientInfo;
import org.eclipse.lsp4j.WindowClientCapabilities;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;

/**
 * Support class to help manage the lifecycle of a connection to a language server, in particular for
 * clearing up any resources if an error occurs or the calling code cancels midway through startup.
 *
 */
public class ConnectionFactory {

	private final URI rootURI;

	private CompletableFuture<@NonNull ServerConnection> init = new CompletableFuture<>();

	private final AtomicBoolean cancelled = new AtomicBoolean();

	private final Consumer<Message> logger;

	private final LanguageServerDefinition serverDefinition;

	private final ExecutorService listenerThreadPool;

	private final LanguageServerWrapper wrapper;

	public ConnectionFactory(final URI rootURI, final LanguageServerWrapper wrapper, final Consumer<Message> logger, final ExecutorService listenerThreadPool) {
		this.rootURI = rootURI;
		this.serverDefinition = wrapper.serverDefinition;
		this.logger = logger;
		this.listenerThreadPool = listenerThreadPool;
		this.wrapper = wrapper;

	}

	/**
	 * Creates the various resources needed by the connection.
	 *
	 * @return Connection object that will be guaranteed to be ready for interaction when the future completes.
	 * It is the consumer's responsibility to add stages to handle cancellation/abnormal termination and call
	 * abortStartup() to clean up
	 */
	public CompletableFuture<@NonNull ServerConnection> start() {
		// Set up the sequence of operations that need to run in order to start the server and have it ready for requests
		final CompletableFuture<@NonNull ServerConnection> result = init.thenApplyAsync(this::startStream)
				.thenApply(this::startClient)
				.thenCompose(this::initServer);

		// Trigger the sequence above to run
		init.complete(new ServerConnection());
		return result;
	}


	/**
	 * Set up the input/output streams for the server and start the external process if applicable
	 * @param connection
	 * @return
	 */
	private @NonNull ServerConnection startStream(final @NonNull ServerConnection connection) {
		checkCancelled();
		StreamConnectionProvider provider = serverDefinition.createConnectionProvider();

		if (LoggingStreamConnectionProviderProxy.shouldLog(serverDefinition.id)) {
			provider = new LoggingStreamConnectionProviderProxy(
					provider, serverDefinition.id);
		}
		connection.initParams.setInitializationOptions(provider.getInitializationOptions(rootURI));
		try {
			provider.start();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		connection.lspStreamProvider = provider;
		return connection;
	}

	/**
	 * Set up the client side of the connection, including starting the listener thread (for
	 * responses and server-initiated requests/notifications)
	 * @param connection
	 * @return
	 */
	private @NonNull ServerConnection startClient(final @NonNull ServerConnection connection) {
		checkCancelled();

		connection.languageClient = serverDefinition.createLanguageClient();

		connection.initParams.setProcessId((int) ProcessHandle.current().pid());

		if (rootURI != null) {
			connection.initParams.setRootUri(rootURI.toString());
			connection.initParams.setRootPath(rootURI.getPath());
		}

		UnaryOperator<MessageConsumer> wrappedConsumer = consumer -> (message -> {
			this.logger.accept(message);
			consumer.consume(message);
			final StreamConnectionProvider currentConnectionProvider = connection.lspStreamProvider;
			if (currentConnectionProvider != null && connection.isActive()) {
				currentConnectionProvider.handleMessage(message, connection.languageServer, rootURI);
			}
		});
		connection.initParams.setWorkspaceFolders(getRelevantWorkspaceFolders(connection.languageClient));
		Launcher<LanguageServer> launcher = serverDefinition.createLauncherBuilder() //
				.setLocalService(connection.languageClient)//
				.setRemoteInterface(serverDefinition.getServerInterface())//
				.setInput(connection.lspStreamProvider.getInputStream())//
				.setOutput(connection.lspStreamProvider.getOutputStream())//
				.setExecutorService(listenerThreadPool)//
				.wrapMessages(wrappedConsumer)//
				.create();
		connection.languageServer = launcher.getRemoteProxy();
		connection.languageClient.connect(connection.languageServer, this.wrapper);
		connection.launcherFuture = launcher.startListening();

		return connection;
	}

	/**
	 * Send the init message to the server with the list of features we support, and store the server's
	 * supported features when we receive the response
	 *
	 * @param connection
	 * @return
	 */
	private CompletableFuture<@NonNull ServerConnection> initServer(final @NonNull ServerConnection connection) {
		checkCancelled();
		final String name = Platform.getProduct() != null ? Platform.getProduct().getName() : "Eclipse IDE"; //$NON-NLS-1$

		final var workspaceClientCapabilities = SupportedFeatures.getWorkspaceClientCapabilities();
		final var textDocumentClientCapabilities = SupportedFeatures.getTextDocumentClientCapabilities();

		WindowClientCapabilities windowClientCapabilities = SupportedFeatures.getWindowClientCapabilities();
		connection.initParams.setCapabilities(new ClientCapabilities(
				workspaceClientCapabilities,
				textDocumentClientCapabilities,
				windowClientCapabilities,
				connection.lspStreamProvider.getExperimentalFeaturesPOJO()));
		connection.initParams.setClientInfo(getClientInfo(name));
		connection.initParams.setTrace(connection.lspStreamProvider.getTrace(rootURI));

		// no then...Async future here as we want this chain of operation to be sequential and "atomic"-ish
		return connection.languageServer.initialize(connection.initParams).thenApply(initializeResult -> {
			connection.serverCapabilities = initializeResult.getCapabilities();

			// Unused at present, but might as well store it
			connection.serverInfo = initializeResult.getServerInfo();

			return connection;
		});
	}


	private static ClientInfo getClientInfo(String name) {
		String pluginVersion = Platform.getBundle(LanguageServerPlugin.PLUGIN_ID).getVersion().toString();
		final var clientInfo = new ClientInfo(name, pluginVersion);
		return clientInfo;
	}

	/**
	 * @return the workspace folder to be announced to the language server
	 */
	public static List<WorkspaceFolder> getRelevantWorkspaceFolders(final @Nullable LanguageClient languageClient) {
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
	 * Sets a cancellation flag so that any startup steps yet to run are skipped. If the future returned from <code>start()</code>
	 * has yet to complete then it will complete with a <code>CancellationException</code>
	 *
	 * Clears up all resources that were already started
	 */
	public synchronized void abortStartup() {
		this.cancelled.set(true);
		if (!this.init.isDone()) {
			return;
		}
		stop(this.init.join());
	}

	/**
	 * Shuts down all resources managed by the supplied connection object
	 * @param connection
	 */
	public static void stop(final @NonNull ServerConnection connection) {
		final boolean alreadyStopping = connection.stopping.getAndSet(true);
		if (alreadyStopping) {
			return;
		}
		Runnable shutdownKillAndStopFutureAndProvider = () -> {
			if (connection.languageServer != null) {
				CompletableFuture<Object> shutdown = connection.languageServer.shutdown();
				try {
					shutdown.get(5, TimeUnit.SECONDS);
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				} catch (Exception ex) {
					LanguageServerPlugin.logError(ex);
				}
			}

			if (connection.languageServer != null) {
				connection.languageServer.exit();
			}

			if (connection.lspStreamProvider != null) {
				connection.lspStreamProvider.stop();
			}
			if (connection.launcherFuture != null) {
				connection.launcherFuture.cancel(true);
			}
		};
		CompletableFuture.runAsync(shutdownKillAndStopFutureAndProvider);

	}

	/**
	 * Throws a <code>CancellationException</code> if startup has been aborted
	 */
	public void checkCancelled() {
		if (cancelled.get()) {
			throw new CancellationException();
		}
	}
}
