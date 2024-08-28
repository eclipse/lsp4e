/*******************************************************************************
 * Copyright (c) 2024 Vegard IT GmbH and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Sebastian Thomschke (Vegard IT GmbH) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.internal;

import static org.junit.Assert.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.eclipse.lsp4e.internal.FutureUtil;
import org.junit.Test;

public class FutureUtilTest {

	@Test
	public void testJoin() throws InterruptedException, ExecutionException {
		final var fut1 = CompletableFuture.supplyAsync(() -> List.of("a", "b"));
		final var fut2 = CompletableFuture.supplyAsync(() -> List.of("c", "d"));
		final var fut3 = CompletableFuture.supplyAsync(() -> List.of("e", "f"));
		final var fut4 = CompletableFuture.supplyAsync(() -> List.of("g", "h"));

		assertEquals(List.of("a", "b"), FutureUtil.join(fut1).get());
		assertEquals(List.of("a", "b", "c", "d"), FutureUtil.join(fut1, fut2).get());
		assertEquals(List.of("a", "b", "c", "d", "e", "f"), FutureUtil.join(fut1, fut2, fut3).get());
		assertEquals(List.of("a", "b", "c", "d", "e", "f", "g", "h"), FutureUtil.join(fut1, fut2, fut3, fut4).get());
	}

	@Test
	public void testJoinWithCancel() throws InterruptedException {
		final var fut1 = CompletableFuture.supplyAsync(() -> List.of("a", "b"));
		final var fut2 = CompletableFuture.supplyAsync(() -> {
			try {
				Thread.sleep(5000);
			} catch (InterruptedException ex) {
				throw new RuntimeException(ex);
			}
			return List.of("c", "d");
		});
		final var fut3 = CompletableFuture.supplyAsync(() -> {
			try {
				Thread.sleep(5000);
			} catch (InterruptedException ex) {
				throw new RuntimeException(ex);
			}
			return List.of("e", "f");
		});

		Thread.sleep(1000);
		FutureUtil.join(fut1, fut2, fut3).cancel(true);
		assertTrue(fut1.isDone());
		assertTrue(fut2.isCancelled());
		assertTrue(fut3.isCancelled());
	}
}
