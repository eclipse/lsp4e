/*******************************************************************************
 * Copyright (c) 2017 Kichwa Coders Ltd. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.debug.debugmodel;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IValue;
import org.eclipse.debug.core.model.IVariable;

public class DSPVariable extends DSPDebugElement implements IVariable {

	private Long variablesReference;
	private String name;
	private String value;

	public DSPVariable(DSPDebugElement parent, Long variablesReference, String name, String value) {
		super(parent.getDebugTarget());
		this.variablesReference = variablesReference;
		this.name = name;
		this.value = value;
	}

	@Override
	public void setValue(String expression) throws DebugException {
		// TODO
	}

	@Override
	public void setValue(IValue value) throws DebugException {
		// TODO
	}

	@Override
	public boolean supportsValueModification() {
		// TODO
		return false;
	}

	@Override
	public boolean verifyValue(String expression) throws DebugException {
		// TODO
		return false;
	}

	@Override
	public boolean verifyValue(IValue value) throws DebugException {
		// TODO
		return false;
	}

	@Override
	public IValue getValue() throws DebugException {
		return new DSPValue(this, variablesReference, name, value);
	}

	@Override
	public String getName() throws DebugException {
		return name;
	}

	@Override
	public String getReferenceTypeName() throws DebugException {
		// TODO
		return name;
	}

	@Override
	public boolean hasValueChanged() throws DebugException {
		return false;
	}
}
