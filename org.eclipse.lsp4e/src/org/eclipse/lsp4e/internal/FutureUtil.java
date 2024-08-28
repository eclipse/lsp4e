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
 *  Sebastian Thomschke (Vegard IT GmbH) - extracted code from LanguageServers class
 *  Sebastian Thomschke (Vegard IT GmbH) - changed addAll() method to join()
 *******************************************************************************/
package org.eclipse.lsp4e.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Function;

import org.eclipse.jdt.annotation.Nullable;

public class FutureUtil {

	public static void cancel(final @Nullable Future<?> futureToCancel) {
		if (futureToCancel != null && !futureToCancel.isDone()) {
			futureToCancel.cancel(true);
		}
	}

	public static void cancel(final @Nullable List<? extends Future<?>> futuresToCancel) {
		if (futuresToCancel == null)
			return;
		for (final Future<?> futureToCancel : futuresToCancel) {
			cancel(futureToCancel);
		}
	}

	/**
	 * Combines two async lists of results into a new single list containing all
	 * elements of the first and the second result.
	 *
	 * @return Async combined result
	 */
	public static <T> CompletableFuture<List<T>> join(final CompletableFuture<List<T>> firstResult,
			final CompletableFuture<List<T>> secondResult) {
		final CompletableFuture<List<T>> result = firstResult.thenCombine(secondResult, (firstList, secondList) -> {
			final List<T> combinedList = new ArrayList<>(firstList);
			combinedList.addAll(secondList);
			return combinedList;
		});
		forwardCancellation(result, firstResult, secondResult);
		return result;
	}

	@SuppressWarnings("null")
	public static void forwardCancellation(CompletableFuture<?> from, CompletableFuture<?>... to) {
		from.exceptionally(t -> {
			if (t instanceof CancellationException) {
				ArrayUtil.forEach(to, f -> f.cancel(true));
			}
			return null;
		});
	}

	/**
	 * Combines two async lists of results into a new single list containing all
	 * elements of all results.
	 */
	@SafeVarargs
	public static <T> CompletableFuture<List<T>> join(final CompletableFuture<List<T>> firstResult,
			final CompletableFuture<List<T>>... additionalResults) {
		CompletableFuture<List<T>> result = firstResult.thenApply(firstList -> new ArrayList<>(firstList));
		for (final CompletableFuture<List<T>> additionalResult : additionalResults) {
			result = result.thenCombine(additionalResult, (combinedList, additionalList) -> {
				combinedList.addAll(additionalList);
				return combinedList;
			});
		}
		forwardCancellation(result, firstResult);
		forwardCancellation(result, additionalResults);
		return result;
	}

	/**
	 * Creates a future that is running on the common async pool, ensuring it's not
	 * blocking UI Thread.
	 */
	public static <T> CompletableFuture<T> onCommonPool(CompletableFuture<T> source) {
		CompletableFuture<T> res = source.thenApplyAsync(Function.identity());
		forwardCancellation(res, source);
		return res;
	}

	private FutureUtil() {
	}
}
