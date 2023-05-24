/*******************************************************************************
 * Copyright (c) 2017 Kichwa Coders Ltd. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.debug.console;

import java.util.Optional;

import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.lsp4e.debug.debugmodel.DSPDebugTarget;
import org.eclipse.lsp4j.debug.ProcessEventArguments;

/**
 * Represents that Debug Adapter process and provides a console to see any
 * communication to/from the adapter.
 */
public class DSPProcess implements IProcess {

	private final DSPDebugTarget target;
	private final DSPStreamsProxy proxy;
	private final ProcessEventArguments processArgs;
	private final Optional<ProcessHandle> handle;

	public DSPProcess(DSPDebugTarget target) {
		this(target, null);
	}

	public DSPProcess(DSPDebugTarget dspDebugTarget, ProcessEventArguments args) {
		this.target = dspDebugTarget;
		this.proxy = new DSPStreamsProxy(target.getDebugProtocolServer());
		this.processArgs = args;
		handle = ProcessHandle.of(args.getSystemProcessId());
	}

	@Override
	public <T> T getAdapter(Class<T> adapter) {
		return target.getAdapter(adapter);
	}

	@Override
	public boolean canTerminate() {
		return target.canTerminate();
	}

	@Override
	public boolean isTerminated() {
		return target.isTerminated();
	}

	@Override
	public void terminate() throws DebugException {
		target.terminate();
	}

	@Override
	public String getLabel() {
		if (processArgs != null && processArgs.getName() != null) {
			return processArgs.getName();
		}
		return target.getName();
	}

	@Override
	public ILaunch getLaunch() {
		return target.getLaunch();
	}

	@Override
	public DSPStreamsProxy getStreamsProxy() {
		return proxy;
	}

	@Override
	public void setAttribute(String key, String value) {
		// TODO
	}

	@Override
	public String getAttribute(String key) {
		if (ATTR_PROCESS_ID.equals(key)) {
			return handle.map(ProcessHandle::pid).map(Object::toString).orElse(null);
		}
		return null;
	}

	@Override
	public int getExitValue() throws DebugException {
		if (handle.isPresent() && !handle.get().isAlive()) {
			throw new DebugException(Status.error(handle.get().pid() + " is still running"));
		}
		return 0;
	}

}
