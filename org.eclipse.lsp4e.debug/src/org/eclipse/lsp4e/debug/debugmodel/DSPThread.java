/*******************************************************************************
 * Copyright (c) 2017 Kichwa Coders Ltd. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.debug.debugmodel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.runtime.Assert;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.lsp4e.debug.DSPPlugin;
import org.eclipse.lsp4j.debug.ContinueArguments;
import org.eclipse.lsp4j.debug.NextArguments;
import org.eclipse.lsp4j.debug.PauseArguments;
import org.eclipse.lsp4j.debug.StackFrame;
import org.eclipse.lsp4j.debug.StackTraceArguments;
import org.eclipse.lsp4j.debug.StepInArguments;
import org.eclipse.lsp4j.debug.StepOutArguments;
import org.eclipse.lsp4j.debug.Thread;

public class DSPThread extends DSPDebugElement implements IThread {
	private final Integer id;
	/**
	 * The name may not be known, if it is requested we will ask for it from the
	 * target.
	 */
	private String name;
	private List<DSPStackFrame> frames = Collections.synchronizedList(new ArrayList<>());
	private AtomicBoolean refreshFrames = new AtomicBoolean(true);
	private boolean stepping;
	private boolean isSuspended = false;

	public DSPThread(DSPDebugTarget debugTarget, Thread thread) {
		super(debugTarget);
		this.id = thread.getId();
		this.name = thread.getName();
	}

	public DSPThread(DSPDebugTarget debugTarget, Integer threadId) {
		super(debugTarget);
		this.id = threadId;
	}

	/**
	 * Update properties of the thread. The ID can't be changed, so the passed in
	 * thread should match.
	 *
	 * @param thread
	 * @throws DebugException
	 */
	public void update(Thread thread) {
		Assert.isTrue(Objects.equals(this.id, thread.getId()));
		this.name = thread.getName();
		refreshFrames.set(true);
		// fireChangeEvent(DebugEvent.STATE);
	}

	@Override
	public void terminate() throws DebugException {
		getDebugTarget().terminate();
	}

	@Override
	public boolean isTerminated() {
		return getDebugTarget().isTerminated();
	}

	@Override
	public boolean canTerminate() {
		return getDebugTarget().canTerminate();
	}

	private <T> T handleExceptionalResume(Throwable t) {
		DSPPlugin.logError("Failed to resume debug adapter", t);
		setErrorMessage(t.getMessage());
		stopped();
		fireSuspendEvent(DebugEvent.CLIENT_REQUEST);
		return null;
	}

	public void continued() {
		isSuspended = false;
		refreshFrames.set(true);
	}

	public void stopped() {
		isSuspended = true;
		stepping = false;
		refreshFrames.set(true);
	}

	@Override
	public void stepReturn() throws DebugException {
		continued();
		stepping = true;
		fireResumeEvent(DebugEvent.STEP_OVER);
		StepOutArguments arguments = new StepOutArguments();
		arguments.setThreadId(id);
		getDebugProtocolServer().stepOut(arguments).exceptionally(this::handleExceptionalResume);
	}

	@Override
	public void stepOver() throws DebugException {
		continued();
		stepping = true;
		fireResumeEvent(DebugEvent.STEP_OVER);
		NextArguments arguments = new NextArguments();
		arguments.setThreadId(id);
		getDebugProtocolServer().next(arguments).exceptionally(this::handleExceptionalResume);
	}

	@Override
	public void stepInto() throws DebugException {
		continued();
		stepping = true;
		fireResumeEvent(DebugEvent.STEP_INTO);
		StepInArguments arguments = new StepInArguments();
		arguments.setThreadId(id);
		getDebugProtocolServer().stepIn(arguments).exceptionally(this::handleExceptionalResume);
	}

	@Override
	public void resume() throws DebugException {
		continued();
		stepping = true;
		fireResumeEvent(DebugEvent.RESUME);
		ContinueArguments arguments = new ContinueArguments();
		arguments.setThreadId(id);
		getDebugProtocolServer().continue_(arguments).thenAccept(response -> {
			if (response == null || response.getAllThreadsContinued() == null || response.getAllThreadsContinued()) {
				// turns out everything was resumed, so need to update other threads too
				getDebugTarget().fireResumeEvent(DebugEvent.CLIENT_REQUEST);
			}
		}).exceptionally(this::handleExceptionalResume);
	}

	@Override
	public boolean isStepping() {
		return stepping;
	}

	private boolean canResumeOrStep() {
		return isSuspended();
		// TODO add additional restrictions, like if an evaluation is happening?
	}

	@Override
	public boolean canStepReturn() {
		return canResumeOrStep();
	}

	@Override
	public boolean canStepOver() {
		return canResumeOrStep();
	}

	@Override
	public boolean canStepInto() {
		return canResumeOrStep();
	}

	@Override
	public boolean canResume() {
		return canResumeOrStep();
	}

	@Override
	public void suspend() throws DebugException {
		PauseArguments arguments = new PauseArguments();
		arguments.setThreadId(id);
		getDebugProtocolServer().pause(arguments).exceptionally(t -> {
			DSPPlugin.logError("Failed to suspend debug adapter", t);
			setErrorMessage(t.getMessage());
			fireChangeEvent(DebugEvent.STATE);
			return null;
		});
	}

	@Override
	public boolean isSuspended() {
		return isSuspended;
	}

	@Override
	public boolean canSuspend() {
		return !isSuspended();
	}

	@Override
	public String getModelIdentifier() {
		return getDebugTarget().getModelIdentifier();
	}

	@Override
	public ILaunch getLaunch() {
		return getDebugTarget().getLaunch();
	}

	@Override
	public boolean hasStackFrames() throws DebugException {
		return isSuspended();
	}

	@Override
	public IStackFrame getTopStackFrame() throws DebugException {
		IStackFrame[] stackFrames = getStackFrames();
		if (stackFrames.length > 0) {
			return stackFrames[0];
		} else {
			return null;
		}
	}

	@Override
	public IStackFrame[] getStackFrames() throws DebugException {
		if (!refreshFrames.getAndSet(false)) {
			synchronized (frames) {
				return frames.toArray(new DSPStackFrame[frames.size()]);
			}
		}
		try {
			StackTraceArguments arguments = new StackTraceArguments();
			arguments.setThreadId(id);
			// TODO implement paging to get rest of frames
			arguments.setStartFrame(0);
			arguments.setLevels(20);
			CompletableFuture<DSPStackFrame[]> future = getDebugTarget().getDebugProtocolServer().stackTrace(arguments)
					.thenApply(response -> {
						synchronized (frames) {
							StackFrame[] backendFrames = response.getStackFrames();
							for (int i = 0; i < backendFrames.length; i++) {
								if (i < frames.size()) {
									frames.set(i, frames.get(i).replace(backendFrames[i], i));
								} else {
									frames.add(new DSPStackFrame(this, backendFrames[i], i));
								}
							}
							frames.subList(backendFrames.length, frames.size()).clear();
							return frames.toArray(new DSPStackFrame[frames.size()]);
						}
					});
			return future.get();
		} catch (RuntimeException | ExecutionException e) {
			if (isTerminated()) {
				return new DSPStackFrame[0];
			}
			throw newTargetRequestFailedException(e.getMessage(), e);
		} catch (InterruptedException e) {
			java.lang.Thread.currentThread().interrupt();
			return new DSPStackFrame[0];
		}
	}

	@Override
	public int getPriority() throws DebugException {
		return 0;
	}

	@Override
	public String getName() {
		if (name == null) {
			// Queue up a refresh of the threads to get our name
			getDebugTarget().getThreads();
			return "<pending>";
		}
		return name;
	}

	@Override
	public IBreakpoint[] getBreakpoints() {
		// TODO update breakpoints from stopped messages from server
		return new IBreakpoint[0];
	}

	public Integer getId() {
		return id;
	}

}