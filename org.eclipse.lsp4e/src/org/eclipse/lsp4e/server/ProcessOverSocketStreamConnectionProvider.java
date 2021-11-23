/*******************************************************************************
 * Copyright (c) 2019 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *  Pierre-Yves B. <pyvesdev@gmail.com> - Bug 545950 - Specifying the directory in ProcessStreamConnectionProvider should not be mandatory
 *  Pierre-Yves B. <pyvesdev@gmail.com> - Bug 508812 - Improve error and logging handling
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

	private final int port;
	private Socket socket;
	private InputStream inputStream;
	private OutputStream outputStream;

	public ProcessOverSocketStreamConnectionProvider(List<String> commands, int port) {
		super(commands);
		this.port = port;
	}

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
			Thread.currentThread().interrupt();
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

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof ProcessOverSocketStreamConnectionProvider)) {
			return false;
		}
		if (obj == this) {
			return true;
		}
		ProcessOverSocketStreamConnectionProvider other = (ProcessOverSocketStreamConnectionProvider) obj;
		return Objects.equals(this.getCommands(), other.getCommands())
				&& Objects.equals(this.getWorkingDirectory(), other.getWorkingDirectory())
				&& Objects.equals(this.socket, other.socket);
	}

	@Override
	public String toString() {
		return "ProcessOverSocketStreamConnectionProvider [socket=" + socket + ", commands=" + this.getCommands() //$NON-NLS-1$//$NON-NLS-2$
				+ ", workingDir=" + this.getWorkingDirectory() + "]"; //$NON-NLS-1$//$NON-NLS-2$
	}

}
