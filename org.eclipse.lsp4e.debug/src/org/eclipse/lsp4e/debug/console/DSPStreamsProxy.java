/*******************************************************************************
 * Copyright (c) 2017-2022 Kichwa Coders Ltd. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * Contributors:
 *  Pierre-Yves Bigourdan <pyvesdev@gmail.com> - Add frameId to EvaluateArguments request
 *******************************************************************************/
package org.eclipse.lsp4e.debug.console;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.runtime.Adapters;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.debug.core.model.IStreamsProxy2;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.lsp4e.debug.debugmodel.DSPStackFrame;
import org.eclipse.lsp4j.debug.EvaluateArguments;
import org.eclipse.lsp4j.debug.EvaluateArgumentsContext;
import org.eclipse.lsp4j.debug.EvaluateResponse;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;

public class DSPStreamsProxy implements IStreamsProxy2 {

	private final IDebugProtocolServer debugProtocolServer;
	private final DSPStreamMonitor outputStream;
	private final DSPStreamMonitor errorStream;

	public DSPStreamsProxy(IDebugProtocolServer debugProtocolServer) {
		this.debugProtocolServer = debugProtocolServer;
		this.errorStream = new DSPStreamMonitor();
		this.outputStream = new DSPStreamMonitor();
	}

	@Override
	public @NonNull DSPStreamMonitor getErrorStreamMonitor() {
		return errorStream;
	}

	@Override
	public @NonNull DSPStreamMonitor getOutputStreamMonitor() {
		return outputStream;
	}

	@Override
	public void write(String input) throws IOException {
		String trimmed = input.trim();
		if (!trimmed.isEmpty()) {
			final var args = new EvaluateArguments();
			args.setContext(EvaluateArgumentsContext.REPL);
			args.setExpression(trimmed);
			IAdaptable adaptable = DebugUITools.getDebugContext();
			if (adaptable != null) {
				DSPStackFrame frame = Adapters.adapt(adaptable, DSPStackFrame.class);
				if (frame != null) {
					args.setFrameId(frame.getFrameId());
				}
			}
			CompletableFuture<EvaluateResponse> future = debugProtocolServer.evaluate(args);
			future.thenAcceptAsync(response -> {
				// TODO support structured responses too?
				if (response != null && response.getResult() != null) {
					String result = response.getResult() + System.lineSeparator();
					outputStream.append(result);
				}
			}).exceptionally(t -> {
				if (t.getCause() instanceof ResponseErrorException exception) {
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
