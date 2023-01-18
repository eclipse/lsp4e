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
package org.eclipse.lsp4e;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.services.LanguageServer;

/**
 * Represents a running language server, with access to the server's capabilities and
 * methods to dispatch requests/notifications.
 *
 */
public interface ILanguageServerWrapper {

	/**
	 * @return The language ID that this wrapper is dealing with if defined in the
	 *         content type mapping for the language server
	 */
	String getLanguageId(IContentType[] contentTypes);

	/**
	 * If called within one of the <code>LanguageServers.compute</code> callbacks (or afterwards),
	 * will return the server capabilities as the LS will have been started and its capabilities cached.
	 * If called directly on <code>LanguageServerWrapper</code>
	 * then might be a long running operation that could time out and return null.
	 *
	 * @return the server capabilities
	 */
	ServerCapabilities getServerCapabilities();

	/**
	 * Runs a request on the language server
	 *
	 * @param <T> LS response type
	 * @param fn Code block that will be supplied the LS in a state where it is guaranteed to have been initialized
	 *
	 * @return Async result
	 */
	<T> CompletableFuture<T> execute(@NonNull Function<LanguageServer, ? extends CompletionStage<T>> fn);

	/**
	 * Sends a notification to the wrapped language server
	 *
	 * @param fn LS notification to send
	 */
	void sendNotification(@NonNull Consumer<LanguageServer> fn);

	/**
	 * Check whether this LS is suitable for provided project. Starts the LS if not
	 * already started.
	 *
	 * @return whether this language server can operate on the given project
	 */
	boolean canOperate(@NonNull IProject project);

	/**
	 * @return whether the underlying connection to language server is still active
	 */
	boolean isActive();

}
