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

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.lsp4e.LanguageClientImpl;
import org.eclipse.lsp4e.server.StreamConnectionProvider;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.ServerInfo;
import org.eclipse.lsp4j.services.LanguageServer;

/**
 * POJO holding the state for communicating with a particular server instance
 */
public final class ServerConnection {

	public Future<?> launcherFuture;

	public LanguageServer languageServer;

	public final InitializeParams initParams = new InitializeParams();


	public ServerCapabilities serverCapabilities;

	public ServerInfo serverInfo;

	public LanguageClientImpl languageClient;

	public StreamConnectionProvider lspStreamProvider;

	public final AtomicBoolean stopping = new AtomicBoolean(false);

	/**
	 *
	 * @return True if server still running, false if it has crashed or exited for some reason
	 */
	public boolean isActive() {

		 // If the server crashes or otherwise exits from under us then the stream should close and
		 // launcher future complete
		return this.launcherFuture != null && !this.launcherFuture.isDone() && !this.launcherFuture.isCancelled();
	}

}
