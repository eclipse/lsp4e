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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Assert;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LanguageServersRegistry.LanguageServerDefinition;
import org.eclipse.lsp4e.internal.DocumentUtil;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextEdit;
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
	@NonNull
	public <T> CompletableFuture<@NonNull List<@NonNull T>> collectAll(Function<LanguageServer, ? extends @NonNull CompletableFuture<T>> fn) {
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
	public <T> CompletableFuture<@NonNull List<@NonNull T>> collectAll(BiFunction<? super LanguageServerWrapper, LanguageServer, ? extends @NonNull CompletableFuture<T>> fn) {
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
	public <T> List<@NonNull CompletableFuture<@Nullable T>> computeAll(Function<LanguageServer, ? extends @NonNull CompletableFuture<T>> fn) {
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
	public <T> List<@NonNull CompletableFuture<@Nullable T>> computeAll(BiFunction<? super LanguageServerWrapper, LanguageServer, ? extends @NonNull CompletableFuture<T>> fn) {
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
	public <T> CompletableFuture<Optional<T>> computeFirst(Function<LanguageServer, ? extends @NonNull CompletableFuture<T>> fn) {
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
	public <T> CompletableFuture<Optional<T>> computeFirst(BiFunction<? super LanguageServerWrapper, LanguageServer, ? extends @NonNull CompletableFuture<T>> fn) {
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

	public @NonNull E withPreferredServer(final @Nullable LanguageServerDefinition serverDefinition) {
		Assert.isLegal(this.serverDefinition == null);
		this.serverDefinition = serverDefinition;
		return (E)this;
	}

	/**
	 * Specifies the capabilities that a server must have to process this request
	 * @param filter Server capabilities predicate
	 * @return
	 */
	public @NonNull E withFilter(final @NonNull Predicate<ServerCapabilities> filter) {
		Assert.isLegal(this.filter == NO_FILTER);
		this.filter = filter;
		return (E)this;
	}

	/**
	 * Specifies the capabilities that a server must have to process this request
	 * @param serverCapabilities
	 * @return
	 */
	public @NonNull E withCapability(final @NonNull Function<ServerCapabilities, Either<Boolean, ? extends Object>> serverCapabilities) {
		Assert.isLegal(this.filter == NO_FILTER);
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

	protected Boolean matches(@NonNull CompletableFuture<@Nullable LanguageServerWrapper> wrapperFuture) {
		try {
			return wrapperFuture.thenApply(Objects::nonNull).get(50, TimeUnit.MILLISECONDS);
		} catch (java.util.concurrent.ExecutionException e) {
			LanguageServerPlugin.logError(e);
		} catch (InterruptedException e) {
			LanguageServerPlugin.logError(e);
			Thread.currentThread().interrupt();
		} catch (TimeoutException e) {
			LanguageServerPlugin.logWarning("Could not get language server due to timeout after 50 milliseconds", e); //$NON-NLS-1$
			return Boolean.TRUE;
		}
		return Boolean.FALSE;
	}

	/**
	 *  Waits if necessary for at most 50 milliseconds for getting a server, if that is not enough it is assumed that
	 *  the server would be matching, and we rely on the next call of to executor to filter the server if needed.
	 *
	 * @return True if there is a language server for this project/document & server capabilities.
	 */
	public boolean anyMatching() {
		return getServers().stream().filter(this::matches).findFirst().isPresent();
	}

	/**
	 * Executor that will run requests on the set of language servers appropriate for the supplied document
	 *
	 */
	@SuppressWarnings("null")
	public static class LanguageServerDocumentExecutor extends LanguageServers<LanguageServerDocumentExecutor> {

		private final @NonNull IDocument document;

		private long startVersion;

		protected LanguageServerDocumentExecutor(final @NonNull IDocument document) {
			this.document = document;
		}

		public @NonNull IDocument getDocument() {
			return this.document;
		}

		public @NonNull VersionedEdits toVersionedEdits(List<? extends TextEdit> edits) {
			return VersionedEdits.toVersionedEdits(this, edits);
		}

		@NonNull CompletableFuture<@Nullable LanguageServerWrapper> connect(@NonNull CompletableFuture<@Nullable LanguageServerWrapper> wrapperFuture) {
			return wrapperFuture.thenCompose(wrapper -> {
				if (wrapper != null) {
					try {
						CompletableFuture<LanguageServerWrapper> serverFuture = wrapper.connectDocument(document);
						if (serverFuture != null) {
							return serverFuture;
						}
					} catch (IOException e) {
						LanguageServerPlugin.logError(e);
					}
				}
				return CompletableFuture.completedFuture(null);
			});
		}


		/**
		 * Test whether this server supports the requested <code>ServerCapabilities</code>.
		 */
		private @NonNull CompletableFuture<@Nullable LanguageServerWrapper> filter(@NonNull LanguageServerWrapper wrapper) {
			return wrapper.getInitializedServer()
					.thenCompose(server -> CompletableFuture
							.completedFuture(server != null && getFilter().test(wrapper.getServerCapabilities())))
					.thenApply(matches -> matches ? wrapper: null);
		}

		@Override
		protected @NonNull List<@NonNull CompletableFuture<@Nullable LanguageServerWrapper>> getServers() {
			// Compute list of servers from document & filter
			Collection<LanguageServerWrapper> wrappers = LanguageServiceAccessor.getLSWrappers(document);
			return order(wrappers).stream().map(this::filter).map(this::connect).toList();
		}

		@Override
		public boolean anyMatching() {
			return LanguageServiceAccessor.getLSWrappers(document).stream()
					.map(this::filter).filter(this::matches).findFirst().isPresent();
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
		public @NonNull LanguageServerProjectExecutor excludeInactive() {
			this.restartStopped = false;
			return this;
		}

		@Override
		protected @NonNull List<@NonNull CompletableFuture<@Nullable LanguageServerWrapper>> getServers() {
			// Compute list of servers from project & filter
			Collection<@NonNull LanguageServerWrapper> startedWrappers = order(LanguageServiceAccessor.getStartedWrappers(project, getFilter(), !restartStopped));
			List<@NonNull CompletableFuture<LanguageServerWrapper>> wrappers = new ArrayList<>(startedWrappers.size());
			for (LanguageServerWrapper wrapper :  startedWrappers) {
				wrappers.add(wrapper.getInitializedServer().thenApply(ls -> wrapper));
			}
			return wrappers;
		}
	}

	private static <T> boolean isEmpty(final T t) {
		return t == null || ((t instanceof List) && ((List<?>)t).isEmpty());
	}

	protected Collection<LanguageServerWrapper> order(Collection<LanguageServerWrapper> wrappers) {
		if (serverDefinition != null && wrappers.size() > 1) {
			List<LanguageServerWrapper> temp = new ArrayList<>(wrappers);
			for (int i = 0; i < temp.size(); i++) {
				LanguageServerWrapper wrapper = temp.get(i);
				if (wrapper != null && wrapper.serverDefinition != null && Objects.equals(serverDefinition, wrapper.serverDefinition)) {
					Collections.swap(temp, 0, i);
					return temp;
				}
			};
		}
		return wrappers;
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
			BiFunction<? super LanguageServerWrapper, LanguageServer, ? extends CompletableFuture<T>> fn) {
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
	public static @NonNull LanguageServerDocumentExecutor forDocument(final @NonNull IDocument document) {
		return new LanguageServerDocumentExecutor(document);
	}

	/**
	 *
	 * @param project
	 * @return Executor that will run requests on servers appropriate to the supplied project
	 */
	public static @NonNull LanguageServerProjectExecutor forProject(final IProject project) {
		return new LanguageServerProjectExecutor(project);
	}

	private static final @NonNull Predicate<ServerCapabilities> NO_FILTER = s -> true;
	private @NonNull Predicate<ServerCapabilities> filter = NO_FILTER;

	protected @Nullable LanguageServerDefinition serverDefinition;
}
