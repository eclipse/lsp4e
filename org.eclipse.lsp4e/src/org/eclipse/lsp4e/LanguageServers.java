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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.swt.widgets.Display;

/**
 * Main entry point for accessors to run requests on the language servers, and some utilities
 * for manipulating the asynchronous response objects in streams
 */
public abstract class LanguageServers<E extends LanguageServers<E>> {

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
	 * will return a <code>CompletableFuture&lt;T&gt;</code>. This function additionally receives a {@link LanguageServerWrapper }
	 * allowing fine-grained interrogation of server capabilities, or the construction of objects that can use this
	 * handle to make further calls on the same server
	 *
	 * @return Async result
	 */
	@NonNull
	public <T> CompletableFuture<@NonNull List<@NonNull T>> collectAll(BiFunction<? super LanguageServerWrapper, LanguageServer, ? extends CompletionStage<T>> fn) {
		final CompletableFuture<List<T>> init = CompletableFuture.completedFuture(new ArrayList<T>());
		return getServers().stream()
			.map(wrapperFuture -> wrapperFuture
					.thenCompose(w -> w == null ? CompletableFuture.completedFuture((T) null) : w.executeImpl(ls -> fn.apply(w, ls))))
			.reduce(init, LanguageServers::combine, LanguageServers::concatResults)

			// Ensure any subsequent computation added by caller does not block further incoming messages from language servers
			.thenCompose(this::complete);
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
	 * will return a <code>CompletableFuture&lt;T&gt;</code>. This function additionally receives a {@link LanguageServerWrapper }
	 * allowing fine-grained interrogation of server capabilities, or the construction of objects that can use this
	 * handle to make further calls on the same server
	 *
	 * @return A list of pending results (note that these may be null or empty)
	 */
	@NonNull
	public <T> List<@NonNull CompletableFuture<@Nullable T>> computeAll(BiFunction<? super LanguageServerWrapper, LanguageServer, ? extends CompletionStage<T>> fn) {
		return getServers().stream()
				.map(wrapperFuture -> wrapperFuture
						.thenCompose(w -> w == null ? CompletableFuture.completedFuture(null) : w.executeImpl(ls -> fn.apply(w, ls)).thenCompose(this::complete)))
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
	 * will return a <code>CompletableFuture&lt;T&gt;</code>. This function additionally receives a {@link LanguageServerWrapper }
	 * allowing fine-grained interrogation of server capabilities, or the construction of objects that can use this
	 * handle to make further calls on the same server
	 *
	 * @return An asynchronous result that will complete with a populated <code>Optional&lt;T&gt;</code> from the first
	 * non-empty response, and with an empty <code>Optional</code> if none of the servers returned a non-empty result.
	 */
	public <T> CompletableFuture<Optional<T>> computeFirst(BiFunction<? super LanguageServerWrapper, LanguageServer, ? extends CompletionStage<T>> fn) {
		final List<CompletableFuture<LanguageServerWrapper>> servers = getServers();

		final CompletableFuture<Optional<T>> result = new CompletableFuture<>();

		// Dispatch the request to the servers, appending a step to each such that
		// the first to return a non-null result will be the overall result.
		// CompletableFuture.anyOf() almost does what we need, but we don't want
		// a quickly-returned null to trump a slowly-returned result
		final List<CompletableFuture<T>> intermediate = servers.stream()
				.map(wrapperFuture -> wrapperFuture
						.thenCompose(w -> w == null ? CompletableFuture.completedFuture((T) null) : w.executeImpl(ls -> fn.apply(w, ls))))
				.map(cf -> cf.thenApply(t -> {
					if (!isEmpty(t)) { // TODO: Does this need to be a supplied function to handle all cases?
						result.complete(Optional.of(t));
					}
					return t;
				})).collect(Collectors.toList());

		// Make sure that if the servers all return null - or complete exceptionally - then we give up and supply an empty result
		// rather than potentially waiting forever...
		CompletableFuture<Void> fallback = CompletableFuture.allOf(intermediate.toArray(new CompletableFuture[intermediate.size()]));
		fallback.whenComplete((v, t) -> {
			if (t != null) {
				result.completeExceptionally(t);
			} else {
				result.complete(Optional.empty());
			}
		});

		return result.thenCompose(this::complete);
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
	 * Causes any <code>CompletableFuture</code> returned by any calls to the language server
	 * from this instance to deliver their results on the UI thread of the supplied <code>display</code>
	 * rather than a random thread from the default ForkJoin pool.
	 *
	 * @param display SWT display owning the event thread to use
	 * @return
	 */
	public E completeOnUI(final @NonNull Display display) {
		this.display = display;
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
		// TODO: should maybe have a default timeout for this...?
		// Use CF::getNow with a default null?
		return getServers().stream().map(CompletableFuture::join).anyMatch(Objects::nonNull);
	}


	/**
	 * Executor that will run requests on the set of language servers appropriate for the supplied document
	 *
	 */
	public static class LSPDocumentExecutor extends LanguageServers<LSPDocumentExecutor> {

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
	 * <p>
	 * Project-level executors work slightly differently: there's (currently) no direct way
	 * of associating a LS with a project, and you can't find out a server's capabilities
	 * until it has started, so LSP4e relies on a document within the project having previously
	 * triggered a server to start. A server may shut down after inactivity, but capabilities are
	 * still available. Candidate LS for a project-level operation may include only currently-running LS,
	 * or can restart any previously-started ones that match the filter.
	 */
	public static class LSPProjectExecutor extends LanguageServers<LSPProjectExecutor> {

		private final IProject project;

		private boolean restartStopped = true;

		LSPProjectExecutor(final IProject project) {
			this.project = project;
		}

		/**
		 * If called, this executor will not attempt to restart any matching servers that previously started
		 * in this session but have since shut down
		 * @return
		 */
		public LSPProjectExecutor excludeInactive() {
			this.restartStopped = false;
			return this;
		}

		@Override
		protected List<CompletableFuture<LanguageServerWrapper>> getServers() {
			// Compute list of servers from project & filter
			List<@NonNull CompletableFuture<LanguageServerWrapper>> wrappers = new ArrayList<>();
			for (LanguageServerWrapper wrapper :  LanguageServiceAccessor.getStartedWrappers(project, getFilter(), !restartStopped)) {
				wrappers.add(wrapper.getInitializedServer().thenApply(ls -> wrapper));
			}
			return wrappers;
		}
	}


	private static <T> boolean isEmpty(final T t) {
		return t == null || ((t instanceof List) && ((List<?>)t).isEmpty());
	}



	// Pluggable strategy for getting the set of LSWrappers to dispatch operations on
	protected abstract List<CompletableFuture<LanguageServerWrapper>> getServers();

	/**
	 *
	 * Safely generate a stream that can be e.g. used with flatMap: caters for null (rather than empty)
	 * results from language servers that failed to start or were filtered out when invoked from <code>computeOnServers()</code>
	 * @param <T> Result type
	 * @param col
	 * @return A stream (empty if col is null)
	 */
	@NonNull
	public static <T> Stream<T> streamSafely(@Nullable Collection<T> col) {
		return col == null ? Stream.<T>of() : col.stream();
	}

	/**
	 *
	 * Accumulator that appends the result of an async computation onto an async aggregate result. Nulls will be excluded.
	 * @param <T> Result type
	 * @param result Async aggregate result
	 * @param next Pending result to include
	 * @return
	 */
	@NonNull
	public static <T> CompletableFuture<@NonNull List<@NonNull T>> combine(@NonNull CompletableFuture<? extends List<@NonNull T>> result, @NonNull CompletableFuture<@Nullable T> next) {
		return result.thenCombine(next, (List<T> a, T b) -> {
			if (b != null) {
				a.add(b);
			}
			return a;
		});
	}

	/**
	 * Merges two async sets of results into a single async result
	 * @param <T> Result type
	 * @param first First async result
	 * @param second Second async result
	 * @return Async combined result
	 */
	@NonNull
	public static <T> CompletableFuture<@NonNull List<T>> concatResults(@NonNull CompletableFuture<@NonNull List<T>> first, @NonNull CompletableFuture<@NonNull List<T>> second) {
		return first.thenCombine(second, (c, d) -> {
			c.addAll(d);
			return c;
		});
	}

	/**
	 *
	 * @param document
	 * @return Executor that will run requests on servers appropriate to the supplied document
	 */
	public static LSPDocumentExecutor forDocument(final @NonNull IDocument document) {
		return new LSPDocumentExecutor(document);
	}

	private <T> CompletableFuture<T> complete(final T t) {
		if (this.display != null) {
			CompletableFuture<T> tmp = new CompletableFuture<>();
			this.display.asyncExec(() -> {
				tmp.complete(t);
			});
			return tmp;
		}
		return CompletableFuture.supplyAsync(() -> t);
	}


	/**
	 *
	 * @param project
	 * @return Executor that will run requests on servers appropriate to the supplied project
	 */
	public static LSPProjectExecutor forProject(final IProject project) {
		return new LSPProjectExecutor(project);
	}

	private @NonNull Predicate<ServerCapabilities> filter = s -> true;

	private Display display;


}
