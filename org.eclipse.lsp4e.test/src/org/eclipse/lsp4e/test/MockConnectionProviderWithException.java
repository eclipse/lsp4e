/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Lucia Jelinkova (Red Hat Inc.)  - initial implementation
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
import org.eclipse.lsp4e.tests.mock.MockLanguageSever;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;

public class MockConnectionProviderWithException implements StreamConnectionProvider {

	private InputStream inputStream;
	private OutputStream outputStream;
	private InputStream errorStream;

	public MockConnectionProviderWithException() {
		throw new IllegalStateException("Testing error from constructor");
	}
	@Override
	public void start() throws IOException {
		PipedInputStream in = new PipedInputStream();
		PipedOutputStream out = new PipedOutputStream();
		PipedInputStream in2 = new PipedInputStream();
		PipedOutputStream out2 = new PipedOutputStream();
		errorStream = new ByteArrayInputStream("Error output on console".getBytes(StandardCharsets.UTF_8));

		in.connect(out2);
		out.connect(in2);

		Launcher<LanguageClient> l = LSPLauncher.createServerLauncher(MockLanguageSever.INSTANCE, in2, out2);
		inputStream = in;
		outputStream = out;
		l.startListening();
		MockLanguageSever.INSTANCE.addRemoteProxy(l.getRemoteProxy());
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
