/*******************************************************************************
 * Copyright (c) 2016, 2017 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.lsp4e.server.StreamConnectionProvider;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;

public class MockConnectionProvider implements StreamConnectionProvider {

	private InputStream inputStream  ;
	private OutputStream outputStream;
	private InputStream errorStream;

	@Override
	public void start() throws IOException {
		PipedInputStream clientInput = new PipedInputStream();
		PipedOutputStream clientOutput = new PipedOutputStream();
		PipedInputStream serverInput = new PipedInputStream();
		PipedOutputStream serverOutput = new PipedOutputStream();
		errorStream = new ByteArrayInputStream("Error output on console".getBytes(StandardCharsets.UTF_8));

		clientInput.connect(serverOutput);
		clientOutput.connect(serverInput);

		Launcher<LanguageClient> l = LSPLauncher.createServerLauncher(MockLanguageServer.INSTANCE, serverInput,
				serverOutput);
		inputStream = clientInput;
		outputStream = clientOutput;
		l.startListening();
		MockLanguageServer.INSTANCE.addRemoteProxy(l.getRemoteProxy());
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
	public InputStream getErrorStream() {
		return errorStream;
	}

	@Override
	public void stop() {
	}

}
