/*******************************************************************************
 * Copyright (c) 2017 Kichwa Coders Ltd. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.debug.debugmodel;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IBreakpointManager;
import org.eclipse.debug.core.model.DebugElement;
import org.eclipse.lsp4e.debug.DSPPlugin;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;

public abstract class DSPDebugElement extends DebugElement {

	private String errorMessage;

	public DSPDebugElement(DSPDebugTarget target) {
		super(target);
	}

	@Override
	public String getModelIdentifier() {
		return DSPPlugin.ID_DSP_DEBUG_MODEL;
	}

	@Override
	public DSPDebugTarget getDebugTarget() {
		return (DSPDebugTarget) super.getDebugTarget();
	}

	public IDebugProtocolServer getDebugProtocolServer() {
		return getDebugTarget().getDebugProtocolServer();
	}

	/**
	 * Returns the breakpoint manager
	 *
	 * @return the breakpoint manager
	 */
	protected IBreakpointManager getBreakpointManager() {
		return DebugPlugin.getDefault().getBreakpointManager();
	}

	/**
	 * TODO: This method was created for the prototyping work. It needs to be
	 * replaced now.
	 *
	 * Gets the response from the debug command and prints some debug info
	 *
	 * @param future
	 * @throws DebugException
	 */
	static <T> T complete(CompletableFuture<T> future) throws DebugException {
		try {
			return future.get();
		} catch (ExecutionException e) {
			throw newTargetRequestFailedException("Failed to get result from target", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw newTargetRequestFailedException("Failed to get result from target", e);
		}
	}

	static <T> T monitorGet(CompletableFuture<T> future, IProgressMonitor monitor) throws DebugException {
		try {
			while (true) {
				if (monitor.isCanceled()) {
					future.cancel(true);
					return future.get();
				}
				try {
					return future.get(100, TimeUnit.MILLISECONDS);
				} catch (TimeoutException e) {
					// check monitor cancelled and try again.
				}
			}
		} catch (CancellationException e) {
			throw new DebugException(new Status(IStatus.CANCEL, DSPPlugin.PLUGIN_ID,
					DebugException.TARGET_REQUEST_FAILED, "Cancelled", e));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			future.cancel(true);
			throw new DebugException(new Status(IStatus.CANCEL, DSPPlugin.PLUGIN_ID,
					DebugException.TARGET_REQUEST_FAILED, "Interrupted", e));
		} catch (ExecutionException e) {
			throw new DebugException(new Status(IStatus.ERROR, DSPPlugin.PLUGIN_ID,
					DebugException.TARGET_REQUEST_FAILED, "Unexpected exception", e));
		}

	}

	static DebugException newTargetRequestFailedException(String message, Throwable e) {
		return new DebugException(new Status(IStatus.ERROR, DebugPlugin.getUniqueIdentifier(),
				DebugException.TARGET_REQUEST_FAILED, message, e));
	}

	protected void setErrorMessage(String message) {
		errorMessage = message;
	}

	/**
	 * Return the error message for the current element, or <code>null</code> if
	 * none.
	 *
	 * @return error message to display to the user
	 */
	public String getErrorMessage() {
		// TODO Once set, we never clear an error on an element
		return errorMessage;
	}
}
