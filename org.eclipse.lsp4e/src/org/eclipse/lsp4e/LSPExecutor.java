package org.eclipse.lsp4e;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.services.LanguageServer;

/**
 *
 */
public abstract class LSPExecutor<E extends LSPExecutor<E>> {

	/**
	 * Runs an operation on all applicable language servers, returning an async result that will consist
	 * of all non-empty individual results
	 *
	 * @param <T> Type of result being computed on the language server(s)
	 * @param fn An individual operation to be performed on the language server, which following the LSP4j API
	 * will return a <code>CompletableFuture&lt;T&gt;</code>
	 *
	 * @return Async result
	 */
	public <T> CompletableFuture<@NonNull List<@NonNull T>> collectAll(Function<LanguageServer, ? extends CompletionStage<T>> fn) {
		return collectAll((w, ls) -> fn.apply(ls));
	}

	/**
	 * Runs an operation on all applicable language servers, returning an async result that will consist
	 * of all non-empty individual results
	 *
	 * @param <T> Type of result being computed on the language server(s)
	 * @param fn An individual operation to be performed on the language server, which following the LSP4j API
	 * will return a <code>CompletableFuture&lt;T&gt;</code>. This function additionally receives a {@link ILSWrapper }
	 * allowing fine-grained interrogation of server capabilities, or the construction of objects that can use this
	 * handle to make further calls on the same server
	 *
	 * @return Async result
	 */
	@NonNull
	public <T> CompletableFuture<@NonNull List<@NonNull T>> collectAll(BiFunction<? super ILSWrapper, LanguageServer, ? extends CompletionStage<T>> fn) {
		final CompletableFuture<List<T>> init = CompletableFuture.completedFuture(new ArrayList<T>());
		return getServers().stream()
			.map(wrapperFuture -> wrapperFuture
					.thenCompose(w -> w == null ? CompletableFuture.completedFuture((T) null) : w.executeImpl(ls -> fn.apply(w, ls))))
			.reduce(init, LanguageServers::combine, LanguageServers::concatResults)

			// Ensure any subsequent computation added by caller does not block further incoming messages from language servers
			.thenApplyAsync(Function.identity());
	}


	/**
	 * Runs an operation on all applicable language servers, returning a list of asynchronous responses that can
	 * be used to instigate further processing as they complete individually
	 *
	 * @param <T> Type of result being computed on the language server(s)
	 * @param fn An individual operation to be performed on the language server, which following the LSP4j API
	 * will return a <code>CompletableFuture&lt;T&gt;</code>.
	 *
	 * @return A list of pending results (note that these may be null or empty)
	 */
	@NonNull
	public <T> List<@NonNull CompletableFuture<@Nullable T>> computeAll(Function<LanguageServer, ? extends CompletionStage<T>> fn) {
		return computeAll((w, ls) -> fn.apply(ls));
	}


	/**
	 * Runs an operation on all applicable language servers, returning a list of asynchronous responses that can
	 * be used to instigate further processing as they complete individually
	 *
	 * @param <T> Type of result being computed on the language server(s)
	 * @param fn An individual operation to be performed on the language server, which following the LSP4j API
	 * will return a <code>CompletableFuture&lt;T&gt;</code>. This function additionally receives a {@link ILSWrapper }
	 * allowing fine-grained interrogation of server capabilities, or the construction of objects that can use this
	 * handle to make further calls on the same server
	 *
	 * @return A list of pending results (note that these may be null or empty)
	 */
	@NonNull
	public <T> List<@NonNull CompletableFuture<@Nullable T>> computeAll(BiFunction<? super ILSWrapper, LanguageServer, ? extends CompletionStage<T>> fn) {
		return getServers().stream()
				.map(wrapperFuture -> wrapperFuture
						.thenCompose(w -> w == null ? null : w.executeImpl(ls -> fn.apply(w, ls)).thenApplyAsync(Function.identity())))
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
	}

	/**
	 * Runs an operation on all applicable language servers, returning an async result that will receive the first
	 * non-null response
	 * @param <T> Type of result being computed on the language server(s)
	 * @param fn An individual operation to be performed on the language server, which following the LSP4j API
	 * will return a <code>CompletableFuture&lt;T&gt;</code>.
	 *
	 * @return An asynchronous result that will complete with a populated <code>Optional&lt;T&gt;</code> from the first
	 * non-empty response, and with an empty <code>Optional</code> if none of the servers returned a non-empty result.
	 */
	public <T> CompletableFuture<Optional<T>> computeFirst(Function<LanguageServer, ? extends CompletionStage<T>> fn) {
		return computeFirst((w, ls) -> fn.apply(ls));
	}

	/**
	 * Runs an operation on all applicable language servers, returning an async result that will receive the first
	 * non-null response
	 * @param <T> Type of result being computed on the language server(s)
	 * @param fn An individual operation to be performed on the language server, which following the LSP4j API
	 * will return a <code>CompletableFuture&lt;T&gt;</code>. This function additionally receives a {@link ILSWrapper }
	 * allowing fine-grained interrogation of server capabilities, or the construction of objects that can use this
	 * handle to make further calls on the same server
	 *
	 * @return An asynchronous result that will complete with a populated <code>Optional&lt;T&gt;</code> from the first
	 * non-empty response, and with an empty <code>Optional</code> if none of the servers returned a non-empty result.
	 */
	public <T> CompletableFuture<Optional<T>> computeFirst(BiFunction<? super ILSWrapper, LanguageServer, ? extends CompletionStage<T>> fn) {
		final List<CompletableFuture<LanguageServerWrapper>> servers = getServers();
		if (servers.isEmpty()) {
			return CompletableFuture.completedFuture(Optional.empty());
		}
		final CompletableFuture<Optional<T>> result = new CompletableFuture<>();

		// Dispatch the request to the servers, appending a step to each such that
		// the first to return a non-null result will be the overall result.
		// CompletableFuture.anyOf() almost does what we need, but we don't want
		// a quickly-returned null to trump a slowly-returned result
		final List<CompletableFuture<T>> intermediate = servers.stream()
				.map(wrapperFuture -> wrapperFuture
						.thenCompose(w -> w == null ? null : w.executeImpl(ls -> fn.apply(w, ls))))
				.filter(Objects::nonNull)
				.map(cf -> cf.thenApply(t -> {
					if (!isEmpty(t)) { // TODO: Does this need to be a supplied function to handle all cases?
						result.complete(Optional.of(t));
					}
					return t;
				})).collect(Collectors.toList());

		// Make sure that if the servers all return null then we give up and supply an empty result
		// rather than potentially waiting forever...
		final CompletableFuture<?> fallback = CompletableFuture.allOf(intermediate.toArray(new CompletableFuture[intermediate.size()]));
		fallback.thenRun(() -> result.complete(Optional.empty()));

		return result.thenApplyAsync(Function.identity());
	}

	/**
	 * Specifies the capabilities that a server must have to process this request
	 * @param filter Server capabilities predicate
	 * @return
	 */
	public E withFilter(final @NonNull Predicate<ServerCapabilities> filter) {
		this.filter = filter;
		return (E)this;
	}

	/**
	 *
	 * @return Predicate that will be used to determine which servers this executor will use
	 */
	public @NonNull Predicate<ServerCapabilities> getFilter() {
		return this.filter;
	}

	/**
	 *
	 * @return True if there is a language server for this project/document & server capabilities
	 */
	public boolean anyMatching() {
		return !getServers().isEmpty();
	}


	/**
	 * Executor that will run requests on the set of language servers appropriate for the supplied document
	 *
	 */
	public static class LSPDocumentExecutor extends LSPExecutor<LSPDocumentExecutor> {

		private final @NonNull IDocument document;

		LSPDocumentExecutor(final @NonNull IDocument document) {
			this.document = document;
		}

		public @NonNull IDocument getDocument() {
			return this.document;
		}

		@Override
		protected List<CompletableFuture<LanguageServerWrapper>> getServers() {
			// Compute list of servers from document & filter
			return LanguageServiceAccessor.getLSWrappers(this.document).stream()
				.map(wrapper -> wrapper.connectIf(this.document, getFilter()))
				.collect(Collectors.toList());
		}

	}

	/**
	 * Executor that will run requests on the set of language servers appropriate for the supplied project
	 *
	 */
	public static class LSPProjectExecutor extends LSPExecutor<LSPProjectExecutor> {

		private final IProject project;

		private boolean restartStopped = true;

		LSPProjectExecutor(final IProject project) {
			this.project = project;
		}

		/**
		 * If called, this executor will not attempt to any servers that previously started in this session
		 * but have since shut down
		 * @return
		 */
		public LSPProjectExecutor excludeInactive() {
			this.restartStopped = false;
			return this;
		}

		@Override
		protected List<CompletableFuture<LanguageServerWrapper>> getServers() {
			// Compute list of servers from project & filter
			return LanguageServiceAccessor.getWrappers(this.project, getFilter(), !this.restartStopped);
		}
	}


	private static <T> boolean isEmpty(final T t) {
		return t == null || ((t instanceof List) && ((List<?>)t).isEmpty());
	}



	// Pluggable strategy for getting the set of LSWrappers to dispatch operations on
	protected abstract List<CompletableFuture<LanguageServerWrapper>> getServers();

	private @NonNull Predicate<ServerCapabilities> filter = s -> true;


}
