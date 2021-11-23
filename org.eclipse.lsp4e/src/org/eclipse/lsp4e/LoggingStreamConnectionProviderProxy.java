/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Lucas Bullen (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e;

import java.io.File;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.lsp4e.server.StreamConnectionProvider;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;

public class LoggingStreamConnectionProviderProxy implements StreamConnectionProvider {

	public static File getLogDirectory() {
		IPath root = ResourcesPlugin.getWorkspace().getRoot().getLocation();
		if (root == null) {
			return null;
		}
		File logFolder = new File(root.addTrailingSeparator().toPortableString(), "languageServers-log"); //$NON-NLS-1$
		if (!(logFolder.exists() || logFolder.mkdirs()) || !logFolder.isDirectory() || !logFolder.canWrite()) {
			return null;
		}
		return logFolder;
	}

	private static final String FILE_KEY = "file.logging.enabled"; //$NON-NLS-1$
	private static final String STDERR_KEY = "stderr.logging.enabled"; //$NON-NLS-1$

	private final StreamConnectionProvider provider;
	private InputStream inputStream;
	private OutputStream outputStream;
	private InputStream errorStream;
	private final String id;
	private final File logFile;
	private boolean logToFile;
	private boolean logToConsole;

	/**
	 * Converts a language server ID to the preference ID for logging communications
	 * to file from the language server
	 *
	 * @return language server's preference ID for file logging
	 */
	public static String lsToFileLoggingId(String serverId) {
		return serverId + "." + FILE_KEY;//$NON-NLS-1$
	}

	/**
	 * Converts a language server ID to the preference ID for logging communications
	 * to console from the language server
	 *
	 * @return language server's preference ID for console logging
	 */
	public static String lsToConsoleLoggingId(String serverId) {
		return serverId + "." + STDERR_KEY;//$NON-NLS-1$
	}

	/**
	 * Returns whether currently created connections should be logged to file or the
	 * standard error stream.
	 *
	 * @return If connections should be logged
	 */
	public static boolean shouldLog(String serverId) {
		IPreferenceStore store = LanguageServerPlugin.getDefault().getPreferenceStore();
		return store.getBoolean(lsToFileLoggingId(serverId)) || store.getBoolean(lsToConsoleLoggingId(serverId));
	}

	public LoggingStreamConnectionProviderProxy(StreamConnectionProvider provider, String serverId) {
		this.id = serverId;
		this.provider = provider;

		IPreferenceStore store = LanguageServerPlugin.getDefault().getPreferenceStore();
		logToFile = store.getBoolean(lsToFileLoggingId(serverId));
		logToConsole = store.getBoolean(lsToConsoleLoggingId(serverId));
		store.addPropertyChangeListener(event -> {
			if (event.getProperty().equals(FILE_KEY)) {
				logToFile = (boolean) event.getNewValue();
			} else if (event.getProperty().equals(STDERR_KEY)) {
				logToConsole = (boolean) event.getNewValue();
			}
		});
		this.logFile = getLogFile();
	}

	@Override
	public InputStream getInputStream() {
		if (inputStream != null) {
			return inputStream;
		}
		if (provider.getInputStream() != null) {
			inputStream = new FilterInputStream(provider.getInputStream()) {
				@Override
				public int read(byte[] b, int off, int len) throws IOException {
					int bytes = super.read(b, off, len);
					byte[] payload = new byte[bytes];
					System.arraycopy(b, off, payload, 0, bytes);
					if (logToConsole || logToFile) {
						String s = "\n[t=" + System.currentTimeMillis() + "] " + id + " to LSP4E:\n" + new String(payload); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						if (logToConsole) {
							logToConsole(s);
						}
						if (logToFile) {
							logToFile(s);
						}
					}
					return bytes;
				}
			};
		}
		return inputStream;
	}

	@Override
	public InputStream getErrorStream() {
		if (errorStream != null) {
			return errorStream;
		}
		if (provider.getErrorStream() != null) {
			errorStream = new FilterInputStream(provider.getErrorStream()) {
				@Override
				public int read(byte[] b, int off, int len) throws IOException {
					int bytes = super.read(b, off, len);
					byte[] payload = new byte[bytes];
					System.arraycopy(b, off, payload, 0, bytes);
					if (logToConsole || logToFile) {
						String s = "\n[t=" + System.currentTimeMillis() + "] Error from " + id + ":\n" + new String(payload); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						if (logToConsole) {
							logToConsole(s);
						}
						if (logToFile) {
							logToFile(s);
						}
					}
					return bytes;
				}
			};
		}
		return errorStream;
	}

	@Override
	public OutputStream getOutputStream() {
		if (outputStream != null) {
			return outputStream;
		}
		if (provider.getOutputStream() != null) {
			outputStream = new FilterOutputStream(provider.getOutputStream()) {
				@Override
				public void write(byte[] b) throws IOException {
					if (logToConsole || logToFile) {
						String s = "\n[t=" + System.currentTimeMillis() + "] LSP4E to " + id + ":\n" + new String(b);  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						if (logToConsole) {
							logToConsole(s);
						}
						if (logToFile) {
							logToFile(s);
						}
					}
					super.write(b);
				}
			};
		}
		return outputStream;
	}

	@Override
	public void start() throws IOException {
		provider.start();
	}

	@Override
	public InputStream forwardCopyTo(InputStream input, OutputStream output) {
		return provider.forwardCopyTo(input, output);
	}

	@Override
	public Object getInitializationOptions(URI rootUri) {
		return provider.getInitializationOptions(rootUri);
	}

	@Override
	public String getTrace(URI rootUri) {
		return provider.getTrace(rootUri);
	}

	@Override
	public void handleMessage(Message message, LanguageServer languageServer, URI rootURI) {
		provider.handleMessage(message, languageServer, rootURI);
	}

	@Override
	public void stop() {
		provider.stop();
		try {
			if (outputStream != null) {
				outputStream.close();
				outputStream = null;
			}
			if (inputStream != null) {
				inputStream.close();
				inputStream = null;
			}
			if (errorStream != null) {
				errorStream.close();
				errorStream = null;
			}
		} catch (IOException e) {
			LanguageServerPlugin.logError(e);
		}
	}

	private void logToConsole(String string) {
		if (consoleStream == null || consoleStream.isClosed()) {
			consoleStream = findConsole().newMessageStream();
		}
		consoleStream.println(string);
	}

	private MessageConsoleStream consoleStream;
	private MessageConsole findConsole() {
		ConsolePlugin plugin = ConsolePlugin.getDefault();
		IConsoleManager conMan = plugin.getConsoleManager();
		IConsole[] existing = conMan.getConsoles();
		for (int i = 0; i < existing.length; i++)
			if (LanguageServerPlugin.PLUGIN_ID.equals(existing[i].getName()))
				return (MessageConsole) existing[i];
		// no console found, so create a new one
		MessageConsole myConsole = new MessageConsole(LanguageServerPlugin.PLUGIN_ID, null);
		conMan.addConsoles(new IConsole[] { myConsole });
		return myConsole;
	}

	private void logToFile(String string) {
		if (logFile == null) {
			return;
		}
		if (!logFile.exists()) {
			try {
				if (!logFile.createNewFile()) {
					throw new IOException(String.format("Failed to create file %s", logFile.toString())); //$NON-NLS-1$
				}
			} catch (IOException e) {
				LanguageServerPlugin.logError(e);
			}
		}
		try {
			Files.write(logFile.toPath(), string.getBytes(), StandardOpenOption.APPEND);
		} catch (IOException e) {
			LanguageServerPlugin.logError(e);
		}
	}

	private File getLogFile() {
		if (logFile != null) {
			return logFile;
		}
		File logFolder = getLogDirectory();
		if (logFolder == null) {
			return null;
		}
		File file = new File(logFolder, id + ".log"); //$NON-NLS-1$
		if (file.exists() && !(file.isFile() && file.canWrite())) {
			return null;
		}
		return file;
	}

}
