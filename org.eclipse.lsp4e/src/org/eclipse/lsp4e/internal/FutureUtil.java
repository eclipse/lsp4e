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
 *******************************************************************************/
package org.eclipse.lsp4e.internal;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class FutureUtil {

	/**
	 * Combines two async lists of results into a single list by adding all the
	 * elements of the second list to the first one.
	 *
	 * @param <T>
	 *            Result type
	 * @param accumulator
	 *            One async result
	 * @param another
	 *            Another async result
	 * @return Async combined result
	 */
	public static <T> CompletableFuture<List<T>> addAll(CompletableFuture<List<T>> accumulator,
			CompletableFuture<List<T>> another) {
		CompletableFuture<List<T>> res = accumulator.thenCombine(another, (a, b) -> {
			a.addAll(b);
			return a;
		});
		forwardCancellation(res, accumulator, another);
		return res;
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
	 * creates a future that is running on the common async pool, ensuring it's not
	 * blocking UI Thread
	 */
	public static <T> CompletableFuture<T> onCommonPool(CompletableFuture<T> source) {
		CompletableFuture<T> res = source.thenApplyAsync(Function.identity());
		forwardCancellation(res, source);
		return res;
	}

	private FutureUtil() {
	}
}
