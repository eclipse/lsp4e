/*******************************************************************************
 * Copyright (c) 2017 Rogue Wave Software Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Objects;

import org.eclipse.lsp4e.LanguageServerPlugin;

/**
 *
 * @since 0.1.0
 */
public abstract class ProcessOverSocketStreamConnectionProvider extends ProcessStreamConnectionProvider {

	private int port;
	private Socket socket;
	private InputStream inputStream;
	private OutputStream outputStream;

	public ProcessOverSocketStreamConnectionProvider(List<String> commands, String workingDir, int port) {
		super(commands, workingDir);
		this.port = port;
	}

	@Override
	public void start() throws IOException {
		final ServerSocket serverSocket = new ServerSocket(port);
		Thread socketThread = new Thread(() -> {
			try {
				socket = serverSocket.accept();
			} catch (IOException e) {
				LanguageServerPlugin.logError(e);
			} finally {
				try {
					serverSocket.close();
				} catch (IOException e) {
					LanguageServerPlugin.logError(e);
				}
			}

		});
		socketThread.start();
		super.start();
		try {
			socketThread.join();
		} catch (InterruptedException e) {
			LanguageServerPlugin.logError(e);
		}

		if (socket == null) {
			throw new IOException("Unable to make socket connection: " + toString()); //$NON-NLS-1$
		}

		inputStream = socket.getInputStream();
		outputStream = socket.getOutputStream();
	}

	@Override
	public InputStream getInputStream() {
		return inputStream;
	}

	@Override
	public OutputStream getOutputStream() {
		return outputStream;
	}

	@Override
	public void stop() {
		super.stop();
		if (socket != null) {
			try {
				socket.close();
			} catch (IOException e) {
				LanguageServerPlugin.logError(e);
			}
		}
	}

	@Override
	public int hashCode() {
		int result = super.hashCode();
		return result ^ Objects.hashCode(this.port);
	}

}
