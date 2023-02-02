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
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.internal.DocumentUtil;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.services.LanguageServer;

/**
 * Main entry point for accessors to run requests on the language servers, and some utilities
 * for manipulating the asynchronous response objects in streams.
 *
 * Note that versioning support makes the executor classes stateful. Attempting to call request
 * methods multiple times on the same executor object will throw an <code>IllegalStateExeception</code>.
 * Either call <code>reset()</code> between intervening executions or <code>clone()</code> a fresh object.
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
	@NonNull
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
		checkHasRun();
		computeVersion();
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
	 * will return a <code>CompletableFuture&lt;T&gt;</code>. This function additionally receives a {@link LanguageServerWrapper }
	 * allowing fine-grained interrogation of server capabilities, or the construction of objects that can use this
	 * handle to make further calls on the same server
	 *
	 * @return A list of pending results (note that these may be null or empty)
	 */
	@NonNull
	public <T> List<@NonNull CompletableFuture<@Nullable T>> computeAll(BiFunction<? super LanguageServerWrapper, LanguageServer, ? extends CompletionStage<T>> fn) {
		checkHasRun();
		computeVersion();
		return getServers().stream()
				.map(cf -> cf
						.thenCompose(w -> w == null ? CompletableFuture.completedFuture(null) : w.executeImpl(ls -> fn.apply(w, ls)).thenApplyAsync(Function.identity())))
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
		checkHasRun();
		computeVersion();
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
	 * Creates a new executor from this one, with the same settings and filter, so a further request can be run
	 */
	@Override
	public abstract E clone();


	/**
	 * Executor that will run requests on the set of language servers appropriate for the supplied document
	 *
	 */
	public static class LanguageServerDocumentExecutor extends LanguageServers<LanguageServerDocumentExecutor> {

		private final @NonNull IDocument document;

		private long startVersion;

		LanguageServerDocumentExecutor(final @NonNull IDocument document) {
			this.document = document;
		}

		public @NonNull IDocument getDocument() {
			return this.document;
		}

		public VersionedEdits toVersionedEdits(List<? extends TextEdit> edits) {
			return VersionedEdits.toVersionedEdits(this, edits);
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

		@Override
		protected void computeVersion() {
			this.startVersion = DocumentUtil.getDocumentModificationStamp(document);
		}

		/**
		 *
		 * @return The document's timestamp at the start of the last request
		 */
		public long getStartVersion() {
			return this.startVersion;
		}

		public SingleLanguageServerDocumentExecutor toExecutor(final @NonNull LanguageServerWrapper serverWrapper) {
			return new SingleLanguageServerDocumentExecutor(this.document, serverWrapper);
		}

		@Override
		public LanguageServerDocumentExecutor clone() {
			return new LanguageServerDocumentExecutor(this.document).withFilter(getFilter());
		}

		@Override
		public void reset() {
			super.reset();
			this.startVersion = 0L;
		}
	}

	/**
	 * Executor for a single server - for follow-up requests that should go to the server that returned an earlier response.
	 * The multi-LS dispatch methods will work, and will take into account the filter, so will return exactly 1 or 0 response.
	 * The additional <code>execute()</code> method will bypass the filter and just run the request directly on the single wrapped
	 * language server.
	 *
	 */
	public static class SingleLanguageServerDocumentExecutor extends LanguageServerDocumentExecutor {

		private final @NonNull LanguageServerWrapper serverWrapper;

		SingleLanguageServerDocumentExecutor(final @NonNull IDocument document, final @NonNull LanguageServerWrapper serverWrapper) {
			super(document);
			this.serverWrapper = serverWrapper;
		}

		@Override
		@SuppressWarnings("null")
		protected @NonNull List<@NonNull CompletableFuture<@Nullable LanguageServerWrapper>> getServers() {
			// Take into account filter
 			if (getFilter().test(serverWrapper.getServerCapabilities())) {
 				return Collections.singletonList(CompletableFuture.completedFuture(serverWrapper));
 			}
			return Collections.emptyList();
		}

		/**
		 * Runs a request directly on the (single) wrapped language server. No filter is applied, but version checking is supported.
		 * @param <T>
		 * @param fn
		 * @return
		 */
		public <T> CompletableFuture<T> execute(@NonNull Function<LanguageServer, ? extends CompletionStage<T>> fn) {
			checkHasRun();
			computeVersion();
			return serverWrapper.execute(fn);
		}

		public boolean supports(final Predicate<ServerCapabilities> predicate) {
			return predicate.test(serverWrapper.getServerCapabilities());
		}

		public LanguageServerWrapper getServer() {
			return serverWrapper;
 		}

		@Override
		public SingleLanguageServerDocumentExecutor clone() {
			return new SingleLanguageServerDocumentExecutor(getDocument(), serverWrapper);
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

		@Override
		public LanguageServerProjectExecutor clone() {
			LanguageServerProjectExecutor cloned = new LanguageServerProjectExecutor(this.project).withFilter(getFilter());
			cloned.restartStopped = this.restartStopped;
			return cloned;
		}
	}

	private static <T> boolean isEmpty(final T t) {
		return t == null || ((t instanceof List) && ((List<?>)t).isEmpty());
	}



	// Pluggable strategy for getting the set of LSWrappers to dispatch operations on
	protected abstract @NonNull List<@NonNull CompletableFuture<@Nullable LanguageServerWrapper>> getServers();

	/**
	 * Hook called when requests are scheduled - for subclasses to implement optimistic locking
	 */
	protected void computeVersion() {}

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
	public static <T> CompletableFuture<@NonNull List<T>> addAll(@NonNull CompletableFuture<@NonNull List<T>> accumulator, @NonNull CompletableFuture<@NonNull List<T>> another) {
		return accumulator.thenCombine(another, (a, b) -> {
			a.addAll(b);
			return a;
		});
	}

	@NonNull
	private <T> Stream<CompletableFuture<T>> executeOnServers(
			BiFunction<? super LanguageServerWrapper, LanguageServer, ? extends CompletionStage<T>> fn) {
		return getServers().stream().map(cf -> cf.thenCompose(
				w -> w == null ? CompletableFuture.completedFuture((T) null) : w.executeImpl(ls -> fn.apply(w, ls)
		)));
	}

	/*
	 * Make sure that if the servers all return null - or complete exceptionally -
	 * then we give up and supply an empty result rather than potentially waiting
	 * forever...
	 */
	private <T> void completeEmptyOrWithException(final CompletableFuture<Optional<T>> completableFuture, final Throwable t) {
		if (t != null && !isRequestCancelledException(t)) {
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
	 * @return True if this executor has been used for a request
	 */
	public final boolean hasRun() {
		return hasRun;
	}

	public void reset() {
		hasRun = false;
	}

	protected void checkHasRun() {
		if (hasRun) {
			throw new IllegalStateException("Executor has already been used for a request"); //$NON-NLS-1$
		}
		hasRun = true;
	}

	private @NonNull Predicate<ServerCapabilities> filter = s -> true;

	private boolean hasRun = false;

	private boolean isRequestCancelledException(final Throwable throwable) {
		if (throwable instanceof final CompletionException completionException) {
			Throwable cause = completionException.getCause();
			if (cause instanceof final ResponseErrorException responseErrorException) {
				ResponseError responseError = responseErrorException.getResponseError();
				return responseError != null
						&& responseError.getCode() == ResponseErrorCode.RequestCancelled.getValue();
			}
		}
		return false;
	}
}
