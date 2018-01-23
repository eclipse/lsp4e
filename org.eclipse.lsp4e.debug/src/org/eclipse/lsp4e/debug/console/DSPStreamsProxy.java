/*******************************************************************************
 * Copyright (c) 2017 Kichwa Coders Ltd. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.lsp4e.debug.console;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import org.eclipse.debug.core.model.IStreamsProxy2;
import org.eclipse.lsp4j.debug.EvaluateArguments;
import org.eclipse.lsp4j.debug.EvaluateArgumentsContext;
import org.eclipse.lsp4j.debug.EvaluateResponse;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;

public class DSPStreamsProxy implements IStreamsProxy2 {

	private IDebugProtocolServer debugProtocolServer;
	private DSPStreamMonitor outputStream;
	private DSPStreamMonitor errorStream;

	public DSPStreamsProxy(IDebugProtocolServer debugProtocolServer) {
		this.debugProtocolServer = debugProtocolServer;
		this.errorStream = new DSPStreamMonitor();
		this.outputStream = new DSPStreamMonitor();
	}

	@Override
	public DSPStreamMonitor getErrorStreamMonitor() {
		return errorStream;
	}

	@Override
	public DSPStreamMonitor getOutputStreamMonitor() {
		return outputStream;
	}

	@Override
	public void write(String input) throws IOException {
		String trimmed = input.trim();
		if (!trimmed.isEmpty()) {
			EvaluateArguments args = new EvaluateArguments();
			args.setContext(EvaluateArgumentsContext.REPL);
			args.setExpression(trimmed);
			// TODO args.setFrameId(0);
			CompletableFuture<EvaluateResponse> future = debugProtocolServer.evaluate(args);
			future.thenAcceptAsync(response -> {
				// TODO support structured responses too?
				if (response != null && response.getResult() != null) {
					String result = response.getResult() + System.lineSeparator();
					outputStream.append(result);
				}
			}).exceptionally((t) -> {
				if (t.getCause() != null && t.getCause() instanceof ResponseErrorException) {
					ResponseErrorException exception = (ResponseErrorException) t.getCause();
					ResponseError error = exception.getResponseError();
					errorStream.append(error.getMessage() + System.lineSeparator());
				} else {
					errorStream.append(t.getLocalizedMessage() + System.lineSeparator());
				}
				return null;
			});
		}
	}

	@Override
	public void closeInputStream() throws IOException {
		// TODO
	}

}
