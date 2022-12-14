package org.eclipse.lsp4e;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LSPExecutor.LSPDocumentExecutor;
import org.eclipse.lsp4e.LSPExecutor.LSPProjectExecutor;

/**
 * Main entry point for accessors to run requests on the language servers, and some utilities
 * for manipulating the asynchronous response objects in streams
 */
public class LanguageServers {

	/**
	 *
	 * @param project
	 * @return Executor that will run requests on servers appropriate to the supplied project
	 */
	public static LSPProjectExecutor forProject(final IProject project) {
		return new LSPProjectExecutor(project);
	}

	/**
	 *
	 * @param document
	 * @return Executor that will run requests on servers appropriate to the supplied document
	 */
	public static LSPDocumentExecutor forDocument(final @NonNull IDocument document) {
		return new LSPDocumentExecutor(document);
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


}
