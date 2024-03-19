/*******************************************************************************
 * Copyright (c) 2016, 2018 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *  Martin Lippert (Pivotal Inc.) - Bug 531030 - fixed crash when initial project gets deleted in multi-root workspaces
 *******************************************************************************/
package org.eclipse.lsp4e.test.utils;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.Pipe;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.lsp4e.server.StreamConnectionProvider;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4e.tests.mock.MockLanguageServerMultiRootFolders;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;

public class MockConnectionProviderMultiRootFolders implements StreamConnectionProvider {

	private InputStream clientInputStream  ;
	private OutputStream clientOutputStream;
	private InputStream errorStream;
	private Collection<Closeable> streams = new ArrayList<>(4);

	@Override
	public void start() throws IOException {
		Pipe serverOutputToClientInput = Pipe.open();
		Pipe clientOutputToServerInput = Pipe.open();
		errorStream = new ByteArrayInputStream("Error output on console".getBytes(StandardCharsets.UTF_8));

		InputStream serverInputStream = Channels.newInputStream(clientOutputToServerInput.source());
		OutputStream serverOutputStream = Channels.newOutputStream(serverOutputToClientInput.sink());
		Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(MockLanguageServerMultiRootFolders.INSTANCE, serverInputStream,
				serverOutputStream);
		clientInputStream = Channels.newInputStream(serverOutputToClientInput.source());
		clientOutputStream = Channels.newOutputStream(clientOutputToServerInput.sink());
		launcher.startListening();
		MockLanguageServer.INSTANCE.addRemoteProxy(launcher.getRemoteProxy());
		streams.add(clientInputStream);
		streams.add(clientOutputStream);
		streams.add(serverInputStream);
		streams.add(serverOutputStream);
		streams.add(errorStream);
	}

	@Override
	public InputStream getInputStream() {
		return clientInputStream;
	}

	@Override
	public OutputStream getOutputStream() {
		return clientOutputStream;
	}

	@Override
	public InputStream getErrorStream() {
		return errorStream;
	}

	@Override
	public void stop() {
	}
}
