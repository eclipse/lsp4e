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
public interface ILSWrapper {

	/**
	 * @return The language ID that this wrapper is dealing with if defined in the
	 *         content type mapping for the language server
	 */
	String getLanguageId(IContentType[] contentTypes);

	/**
	 * Warning: this is a long running operation
	 *
	 * @return the server capabilities, or null if initialization job didn't
	 *         complete
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
	 * @since 0.5
	 */
	boolean canOperate(@NonNull IProject project);

	/**
	 * @return whether the underlying connection to language server is still active
	 */
	boolean isActive();

}
