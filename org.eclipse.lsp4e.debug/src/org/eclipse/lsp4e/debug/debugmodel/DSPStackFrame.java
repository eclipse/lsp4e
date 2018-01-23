/*******************************************************************************
 * Copyright (c) 2017 Kichwa Coders Ltd. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.lsp4e.debug.debugmodel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IRegisterGroup;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.lsp4j.debug.Scope;
import org.eclipse.lsp4j.debug.ScopesArguments;
import org.eclipse.lsp4j.debug.StackFrame;

public class DSPStackFrame extends DSPDebugElement implements IStackFrame {
	private DSPThread thread;
	private StackFrame stackFrame;
	private int depth;

	public DSPStackFrame(DSPThread thread, StackFrame stackFrame, int depth) {
		super(thread.getDebugTarget());
		this.thread = thread;
		this.stackFrame = stackFrame;
		this.depth = depth;
	}

	public DSPStackFrame replace(StackFrame newStackFrame, int newDepth) {
		if (newDepth == depth && Objects.equals(newStackFrame.getSource(), stackFrame.getSource())) {
			stackFrame = newStackFrame;
			return this;
		}
		return new DSPStackFrame(thread, newStackFrame, newDepth);
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

	@Override
	public void suspend() throws DebugException {
	}

	@Override
	public void resume() throws DebugException {
		getThread().resume();
	}

	@Override
	public boolean isSuspended() {
		return getDebugTarget().isSuspended();
	}

	@Override
	public boolean canSuspend() {
		return false;
	}

	@Override
	public boolean canResume() {
		return getThread().canResume();
	}

	@Override
	public void stepReturn() throws DebugException {
	}

	@Override
	public void stepOver() throws DebugException {
		getThread().stepOver();
	}

	@Override
	public void stepInto() throws DebugException {
	}

	@Override
	public boolean isStepping() {
		return false;
	}

	@Override
	public boolean canStepReturn() {
		return false;
	}

	@Override
	public boolean canStepOver() {
		return getThread().canStepOver();
	}

	@Override
	public boolean canStepInto() {
		return false;
	}

	@Override
	public boolean hasVariables() throws DebugException {
		return true;
	}

	@Override
	public boolean hasRegisterGroups() throws DebugException {
		return false;
	}

	@Override
	public IVariable[] getVariables() throws DebugException {
		ScopesArguments arguments = new ScopesArguments();
		arguments.setFrameId(stackFrame.getId());
		Scope[] scopes = complete(getDebugTarget().getDebugProtocolServer().scopes(arguments)).getScopes();
		List<DSPVariable> vars = new ArrayList<>();
		for (Scope scope : scopes) {
			DSPVariable variable = new DSPVariable(this, scope.getVariablesReference(), scope.getName(), "");
			vars.add(variable);
		}
		return vars.toArray(new IVariable[vars.size()]);
	}

	@Override
	public IThread getThread() {
		return thread;
	}

	@Override
	public IRegisterGroup[] getRegisterGroups() throws DebugException {
		return null;
	}

	@Override
	public String getName() throws DebugException {
		return stackFrame.getName();
	}

	@Override
	public int getLineNumber() throws DebugException {
		return (int) (long) stackFrame.getLine();
	}

	@Override
	public int getCharStart() throws DebugException {
		return -1;
	}

	@Override
	public int getCharEnd() throws DebugException {
		return -1;
	}

	public String getSourceName() {
		return stackFrame.getSource().getPath();
	}

	public Long getFrameId() {
		return stackFrame.getId();
	}

	@Override
	public String toString() {
		return "StackFrame [depth=" + depth + ", line=" + stackFrame.getLine() + ", thread=" + thread + ", stackFrame="
				+ stackFrame + "]";
	}

}
