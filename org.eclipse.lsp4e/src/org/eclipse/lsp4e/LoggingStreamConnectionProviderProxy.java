/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
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
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.lsp4e.server.StreamConnectionProvider;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.services.LanguageServer;

public class LoggingStreamConnectionProviderProxy implements StreamConnectionProvider {
	private static final boolean DEFAULT_FILE_KEY_VALUE = true;
	private static final boolean DEFAULT_STDERR_KEY_VALUE = false;
	public static final String FILE_KEY = "logging.enabled"; //$NON-NLS-1$
	public static final String STDERR_KEY = "stderr.logging.enabled"; //$NON-NLS-1$
	public static final String LOG_DIRECTORY = "languageServers-log"; //$NON-NLS-1$

	private StreamConnectionProvider provider;
	private InputStream inputStream;
	private OutputStream outputStream;
	private InputStream errorStream;
	private String id;
	private File currentFile;
	private File folder;
	private boolean logToFile;
	private boolean logToStderr;

	/**
	 * Returns whether currently created connections should be logged to file or the
	 * standard error stream.
	 *
	 * @return If connections should be logged
	 */
	public static boolean shouldLog() {
		IPreferenceStore store = LanguageServerPlugin.getDefault().getPreferenceStore();
		return shouldLogToFile(store) || shouldLogToStdErr(store);
	}

	private static boolean shouldLogToFile(IPreferenceStore store) {
		if (store.contains(FILE_KEY)) {
			return store.getBoolean(FILE_KEY);
		}
		return DEFAULT_FILE_KEY_VALUE;
	}

	public static boolean shouldLogToStdErr(IPreferenceStore store) {
		if (store.contains(STDERR_KEY)) {
			return store.getBoolean(STDERR_KEY);
		}
		return DEFAULT_STDERR_KEY_VALUE;
	}

	public LoggingStreamConnectionProviderProxy(StreamConnectionProvider provider, String logId) {
		this.id = logId;
		this.provider = provider;

		IPreferenceStore store = LanguageServerPlugin.getDefault().getPreferenceStore();
		logToFile = shouldLogToFile(store);
		logToStderr = shouldLogToStdErr(store);
		store.addPropertyChangeListener(new IPropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent event) {
				if (event.getProperty().equals(FILE_KEY)) {
					logToFile = (boolean) event.getNewValue();
				} else if (event.getProperty().equals(STDERR_KEY)) {
					logToStderr = (boolean) event.getNewValue();
				}
			}
		});
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
					if (logToStderr) {
						System.err.println(id + " to LSP4E:" + new String(payload)); //$NON-NLS-1$
					}
					if (logToFile) {
						log("\n" + id + " to LSP4E:" + new String(payload)); //$NON-NLS-1$ //$NON-NLS-2$
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
					if (logToStderr) {
						System.err.println(id + " to LSP4E:" + new String(payload)); //$NON-NLS-1$
					}
					if (logToFile) {
						log(new String("\n" + id + " to LSP4E:" + payload)); //$NON-NLS-1$ //$NON-NLS-2$
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
					if (logToStderr) {
						System.err.println("LSP4E to " + id + ":" + new String(b)); //$NON-NLS-1$ //$NON-NLS-2$
					}
					if (logToFile) {
						log("\nLSP4E to " + id + ":" + new String(b)); //$NON-NLS-1$ //$NON-NLS-2$
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
			e.printStackTrace();
		}
	}

	private void log(String string) {
		if (currentFile == null || !currentFile.exists() || !currentFile.isFile() || !currentFile.canWrite()) {
			generateNewLogFile();
			if (currentFile == null) {
				return;
			}
		}
		try {
			Files.write(currentFile.toPath(), string.getBytes(), StandardOpenOption.APPEND);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return;
	}

	private void generateNewLogFile() {
		File folder = getFolder();
		if (folder == null) {
			return;
		}
		currentFile = new File(folder, id + ".log"); //$NON-NLS-1$
		try {
			currentFile.createNewFile();
		} catch (IOException e) {
			currentFile = null;
			e.printStackTrace();
		}
	}

	private File getFolder() {
		if (folder != null && folder.exists() && folder.isDirectory() && folder.canWrite()) {
			return folder;
		}
		IPath root = ResourcesPlugin.getWorkspace().getRoot().getLocation();
		if (root == null) {
			return null;
		}
		File folder = new File(root.addTrailingSeparator().toPortableString() + LOG_DIRECTORY);
		if (folder != null && (folder.exists() || folder.mkdirs()) && folder.isDirectory() && folder.canWrite()) {
			return folder;
		}
		return null;
	}
}
