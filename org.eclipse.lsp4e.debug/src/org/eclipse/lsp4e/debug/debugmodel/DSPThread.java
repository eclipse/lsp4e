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
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4e.debug.DSPPlugin;
import org.eclipse.lsp4e.internal.ArrayUtil;
import org.eclipse.lsp4j.debug.ContinueArguments;
import org.eclipse.lsp4j.debug.NextArguments;
import org.eclipse.lsp4j.debug.PauseArguments;
import org.eclipse.lsp4j.debug.StackFrame;
import org.eclipse.lsp4j.debug.StackTraceArguments;
import org.eclipse.lsp4j.debug.StepInArguments;
import org.eclipse.lsp4j.debug.StepOutArguments;
import org.eclipse.lsp4j.debug.Thread;

public class DSPThread extends DSPDebugElement implements IThread {

	private static final IStackFrame[] NO_STACK_FRAMES = new IStackFrame[0];
	private static final IBreakpoint[] NO_BREAKPOINTS = new IBreakpoint[0];

	private final Integer id;
	/**
	 * The name may not be known, if it is requested we will ask for it from the
	 * target.
	 */
	private @Nullable String name;
	private final List<DSPStackFrame> frames = Collections.synchronizedList(new ArrayList<>());
	private final AtomicBoolean refreshFrames = new AtomicBoolean(true);
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
		if (!Objects.equals(this.name, thread.getName())) {
			fireChangeEvent(DebugEvent.STATE);
			this.name = thread.getName();
		}
		refreshFrames.set(true);
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

	private <T> @Nullable T handleExceptionalResume(Throwable t) {
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
		frames.clear();
	}

	@Override
	public void stepReturn() throws DebugException {
		continued();
		stepping = true;
		fireResumeEvent(DebugEvent.STEP_OVER);
		final var arguments = new StepOutArguments();
		arguments.setThreadId(id);
		getDebugProtocolServer().stepOut(arguments).exceptionally(this::handleExceptionalResume);
	}

	@Override
	public void stepOver() throws DebugException {
		continued();
		stepping = true;
		fireResumeEvent(DebugEvent.STEP_OVER);
		final var arguments = new NextArguments();
		arguments.setThreadId(id);
		getDebugProtocolServer().next(arguments).exceptionally(this::handleExceptionalResume);
	}

	@Override
	public void stepInto() throws DebugException {
		continued();
		stepping = true;
		fireResumeEvent(DebugEvent.STEP_INTO);
		final var arguments = new StepInArguments();
		arguments.setThreadId(id);
		getDebugProtocolServer().stepIn(arguments).exceptionally(this::handleExceptionalResume);
	}

	@Override
	public void resume() throws DebugException {
		continued();
		stepping = true;
		fireResumeEvent(DebugEvent.RESUME);
		final var arguments = new ContinueArguments();
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
		final var arguments = new PauseArguments();
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
		return !isTerminated() && isSuspended;
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
	public @Nullable IStackFrame getTopStackFrame() throws DebugException {
		return ArrayUtil.findFirst(getStackFrames());
	}

	@Override
	public IStackFrame[] getStackFrames() throws DebugException {
		if (!isSuspended()) {
			return NO_STACK_FRAMES;
		}
		if (!refreshFrames.getAndSet(false)) {
			synchronized (frames) {
				return frames.toArray(DSPStackFrame[]::new);
			}
		}
		try {
			final var arguments = new StackTraceArguments();
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
							return frames.toArray(DSPStackFrame[]::new);
						}
					});
			return future.get();
		} catch (RuntimeException | ExecutionException e) {
			if (isTerminated()) {
				return NO_STACK_FRAMES;
			}
			throw newTargetRequestFailedException(e.getMessage(), e);
		} catch (InterruptedException e) {
			java.lang.Thread.currentThread().interrupt();
			return NO_STACK_FRAMES;
		}
	}

	@Override
	public int getPriority() throws DebugException {
		return 0;
	}

	@Override
	public String getName() {
		final var name = this.name;
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
		return NO_BREAKPOINTS;
	}

	public Integer getId() {
		return id;
	}

}