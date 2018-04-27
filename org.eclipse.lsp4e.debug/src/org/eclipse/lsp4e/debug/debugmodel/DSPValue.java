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
import java.util.List;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IValue;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.lsp4j.debug.Variable;
import org.eclipse.lsp4j.debug.VariablesArguments;

public final class DSPValue extends DSPDebugElement implements IValue {

	private Long variablesReference;
	private String name;
	private String value;

	public DSPValue(DSPDebugElement parent, Long variablesReference, String name, String value) {
		super(parent.getDebugTarget());
		this.variablesReference = variablesReference;
		this.name = name;
		this.value = value;
	}

	@Override
	public IVariable[] getVariables() throws DebugException {
		if (!hasVariables()) {
			return new IVariable[0];
		}
		VariablesArguments arguments = new VariablesArguments();
		arguments.setVariablesReference(variablesReference);
		Variable[] targetVariables = complete(getDebugTarget().getDebugProtocolServer().variables(arguments))
				.getVariables();

		List<DSPVariable> variables = new ArrayList<>();
		for (Variable variable : targetVariables) {
			variables.add(
					new DSPVariable(this, variable.getVariablesReference(), variable.getName(), variable.getValue()));
		}

		return variables.toArray(new DSPVariable[variables.size()]);
	}

	@Override
	public String getReferenceTypeName() throws DebugException {
		// TODO
		return name;
	}

	@Override
	public String getValueString() throws DebugException {
		return value;
	}

	@Override
	public boolean isAllocated() throws DebugException {
		// TODO
		return true;
	}

	@Override
	public boolean hasVariables() throws DebugException {
		return variablesReference != null && variablesReference > 0;
	}
}