/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Lucia Jelinkova (Red Hat Inc.)  - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.utils;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class MockConnectionProviderWithStartException extends MockConnectionProvider {
	private static volatile CompletableFuture<Void> startFuture = new CompletableFuture<>();
	private static volatile int startCounter = 0;
	private static volatile int stopCounter = 0;

	public static void resetStartFuture() {
		startFuture = new CompletableFuture<>();
	}

	public static void waitForStart() throws ExecutionException, InterruptedException, TimeoutException {
		startFuture.get(2, TimeUnit.SECONDS);
	}

	public static void resetCounters() {
		startCounter = 0;
		stopCounter = 0;
	}

	public static int getStartCounter() {
		return startCounter;
	}

	public static int getStopCounter() {
		return stopCounter;
	}

	@Override
	public void start() throws IOException {
		startCounter++;
		startFuture.complete(null);
		throw new IOException("Start failed");
	}

	@Override
	public void stop() {
		stopCounter++;
	}
}
