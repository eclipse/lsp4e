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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageServer;

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
		final CompletableFuture<@NonNull List<T>> init = CompletableFuture.completedFuture(new ArrayList<T>());
		return executeOnServers(fn).reduce(init, LanguageServers::add, LanguageServers::addAll)
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
	 * will return a <code>CompletableFuture&lt;T&gt;</code>. This function additionally receives a {@link LanguageServerWrapper}
	 * allowing fine-grained interrogation of server capabilities, or the construction of objects that can use this
	 * handle to make further calls on the same server
	 *
	 * @return A list of pending results (note that these may be null or empty)
	 */
	@NonNull
	public <T> List<@NonNull CompletableFuture<@Nullable T>> computeAll(BiFunction<? super LanguageServerWrapper, LanguageServer, ? extends CompletionStage<T>> fn) {
		return getServers().stream()
				.map(cf -> cf
						.thenCompose(w -> w == null ? CompletableFuture.completedFuture(null) : w.execute(ls -> fn.apply(w, ls))
								// Ensure any subsequent computation added by caller does not block further incoming messages from language servers
								.thenApplyAsync(Function.identity())))
				.toList();
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
		final CompletableFuture<Optional<T>> result = new CompletableFuture<>();

		// Dispatch the request to the servers, appending a step to each such that
		// the first to return a non-null result will be the overall result.
		// CompletableFuture.anyOf() almost does what we need, but we don't want
		// a quickly-returned null to trump a slowly-returned result
		CompletableFuture.allOf(
				executeOnServers(fn)
				.map(cf -> cf.thenApply(t -> {
					if (!isEmpty(t)) { // TODO: Does this need to be a supplied function to handle all cases?
						result.complete(Optional.of(t));
					}
					return t;
				})).toArray(CompletableFuture[]::new)
				).whenComplete((v, t) -> completeEmptyOrWithException(result, t));

		// Ensure any subsequent computation added by caller does not block further incoming messages from language servers
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
	 * Specifies the capabilities that a server must have to process this request
	 * @param serverCapabilities
	 * @return
	 */
	public E withCapability(final @NonNull Function<ServerCapabilities, Either<Boolean, ? extends Object>> serverCapabilities) {
		this.filter = f -> LSPEclipseUtils.hasCapability(serverCapabilities.apply(f));
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
	public static class LanguageServerDocumentExecutor extends LanguageServers<LanguageServerDocumentExecutor> {

		private final @NonNull IDocument document;

		LanguageServerDocumentExecutor(final @NonNull IDocument document) {
			this.document = document;
		}

		public @NonNull IDocument getDocument() {
			return this.document;
		}

		/**
		 * Test whether this server supports the requested <code>ServerCapabilities</code>, and ensure
		 * that it is connected to the document if so.
		 *
		 * NB result is a future on this <emph>wrapper</emph> rather than the wrapped language server directly,
		 * to support accessing the server on the single-threaded dispatch queue.
		 */
		@NonNull CompletableFuture<@Nullable LanguageServerWrapper> connectIf(@NonNull LanguageServerWrapper wrapper) {
			return wrapper.getInitializedServer().thenCompose(server -> {
				if (server != null && getFilter().test(wrapper.getServerCapabilities())) {
					try {
						return wrapper.connect(document);
					} catch (IOException ex) {
						LanguageServerPlugin.logError(ex);
					}
				}
				return CompletableFuture.completedFuture(null);
			}).thenApply(server -> server == null ? null : wrapper);
		}

		@Override
		protected @NonNull List<@NonNull CompletableFuture<@Nullable LanguageServerWrapper>> getServers() {
			// Compute list of servers from document & filter
			return LanguageServiceAccessor.getLSWrappers(this.document).stream()
				.map(this::connectIf)
				.toList();
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
	public static class LanguageServerProjectExecutor extends LanguageServers<LanguageServerProjectExecutor> {

		private final IProject project;

		private boolean restartStopped = true;

		LanguageServerProjectExecutor(final IProject project) {
			this.project = project;
		}

		/**
		 * If called, this executor will not attempt to restart any matching servers that previously started
		 * in this session but have since shut down
		 * @return
		 */
		public LanguageServerProjectExecutor excludeInactive() {
			this.restartStopped = false;
			return this;
		}

		@Override
		protected @NonNull List<@NonNull CompletableFuture<@Nullable LanguageServerWrapper>> getServers() {
			// Compute list of servers from project & filter
			List<@NonNull CompletableFuture<LanguageServerWrapper>> wrappers = new ArrayList<>();
			for (LanguageServerWrapper wrapper :  LanguageServiceAccessor.getStartedWrappers(project, getFilter(), !restartStopped)) {
				wrappers.add(wrapper.getInitializedServer().thenApply(ls -> wrapper));
			}
			return wrappers;
		}
	}


	/**
	 * Executor that will run on the supplied wrapper
	 */
	public static class LanguageServerWrapperExecutor extends LanguageServers<LanguageServerWrapperExecutor> {

		private final @NonNull LanguageServerWrapper wrapper;

		private LanguageServerWrapperExecutor(final @NonNull LanguageServerWrapper wrapper) {
			this.wrapper = wrapper;
		}

		@Override
		protected @NonNull List<@NonNull CompletableFuture<@Nullable LanguageServerWrapper>> getServers() {
			return Collections.singletonList(CompletableFuture.completedFuture(wrapper));
		}
	}

	private static <T> boolean isEmpty(final T t) {
		return t == null || ((t instanceof List) && ((List<?>)t).isEmpty());
	}



	// Pluggable strategy for getting the set of LSWrappers to dispatch operations on
	protected abstract @NonNull List<@NonNull CompletableFuture<@Nullable LanguageServerWrapper>> getServers();

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
	 * Combines the result of an async computation and an async list of results by adding the element to the list. Null elements will be excluded.
	 * @param <T> Result type
	 * @param accumulator One async result
	 * @param element Another async result
	 * @return
	 */
	@SuppressWarnings("null")
	@NonNull
	private static <T> CompletableFuture<@NonNull List<@NonNull T>> add(@NonNull CompletableFuture<? extends @NonNull List<@NonNull T>> accumulator, @NonNull CompletableFuture<@Nullable T> element) {
		return accumulator.thenCombine(element, (a, b) -> {
			if (b != null) {
				a.add(b);
			}
			return a;
		});
	}

	/**
	 * Combines two async lists of results into a single list by adding all the elements of the second list to the first one.
	 * @param <T> Result type
	 * @param accumulator One async result
	 * @param another Another async result
	 * @return Async combined result
	 */
	@SuppressWarnings("null")
	@NonNull
	private static <T> CompletableFuture<@NonNull List<T>> addAll(@NonNull CompletableFuture<@NonNull List<T>> accumulator, @NonNull CompletableFuture<@NonNull List<T>> another) {
		return accumulator.thenCombine(another, (a, b) -> {
			a.addAll(b);
			return a;
		});
	}

	@NonNull
	private <T> Stream<CompletableFuture<T>> executeOnServers(
			BiFunction<? super LanguageServerWrapper, LanguageServer, ? extends CompletionStage<T>> fn) {
		return getServers().stream().map(cf -> cf.thenCompose(
				w -> w == null ? CompletableFuture.completedFuture((T) null) : w.execute(ls -> fn.apply(w, ls))));
	}

	/*
	 * Make sure that if the servers all return null - or complete exceptionally -
	 * then we give up and supply an empty result rather than potentially waiting
	 * forever...
	 */
	private <T> void completeEmptyOrWithException(final CompletableFuture<Optional<T>> completableFuture, final Throwable t) {
		if (t != null) {
			completableFuture.completeExceptionally(t);
		} else {
			completableFuture.complete(Optional.empty());
		}
	}

	/**
	 *
	 * @param document
	 * @return Executor that will run requests on servers appropriate to the supplied document
	 */
	public static LanguageServerDocumentExecutor forDocument(final @NonNull IDocument document) {
		return new LanguageServerDocumentExecutor(document);
	}

	/**
	 *
	 * @param project
	 * @return Executor that will run requests on servers appropriate to the supplied project
	 */
	public static LanguageServerProjectExecutor forProject(final IProject project) {
		return new LanguageServerProjectExecutor(project);
	}

	/**
	 *
	 * @param wrapper
	 * @return Executor that will run requests on servers appropriate to the supplied wrapper
	 */
	public static LanguageServerWrapperExecutor forWrapper(final @NonNull LanguageServerWrapper wrapper) {
		return new LanguageServerWrapperExecutor(wrapper);
	}

	private @NonNull Predicate<ServerCapabilities> filter = s -> true;


}
