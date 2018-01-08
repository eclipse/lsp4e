/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.services.LanguageServer;

/**
 * Abstraction of a connection which we can start/stop and connect to via streams.
 * It's typically used to wrap startup of language servers and to retrieve their
 * streams.
 * There most likely an existing Java class already taking care of this somewhere
 * in a popular API. In such case, we should consider getting read of this one and
 * use a more popular similar interface.
 * Note that in the context of Eclipse, the ILaunch might be such interface but I'm
 * not sure we want to bind to org.eclipse.debug from this Language Server bindings.
 *
 * This method MUST implement meaningful {@link #hashCode()} and {@link #equals(Object)}
 * to prevent multiple connections to be initiated multiple times.
 *
 * @since 0.1.0
 */
public interface StreamConnectionProvider {

	public void start() throws IOException;

	public InputStream getInputStream();

	public OutputStream getOutputStream();

	/**
	 * User provided initialization options.
	 */
	public default Object getInitializationOptions(URI rootUri){
		return null;
	}

	/**
	 * Provides trace level to be set on language server initialization.<br>
	 * Legal values: "off" | "messages" | "verbose".
	 *
	 * @param rootUri
	 *            the workspace root URI.
	 *
	 * @return the initial trace level to set
	 * @see "https://microsoft.github.io/language-server-protocol/specification#initialize"
	 */
	public default String getTrace(URI rootUri) {
		return "off"; //$NON-NLS-1$
	}

	public void stop();

	/**
	 * Allows to hook custom behavior on messages.
	 * @param message a message
	 * @param languageServer the language server receiving/sending the message.
	 * @param rootUri
	 */
	public default void handleMessage(Message message, LanguageServer languageServer, URI rootURI) {}

}
