/*******************************************************************************
 * Copyright (c) 2019 Fraunhofer FOKUS and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.command.internal;

import java.util.Collections;

import org.eclipse.core.commands.IParameter;
import org.eclipse.core.commands.IParameterValues;
import org.eclipse.core.commands.ITypedParameter;
import org.eclipse.core.commands.ParameterType;
import org.eclipse.core.commands.ParameterValuesException;

/**
 * This parameter class is needed for defining an Eclipse command (a handler can
 * be registered for) for an LSP Command.
 */
public class CommandEventParameter implements IParameter, ITypedParameter {

	private final ParameterType paramType;
	private final String name;
	private final String id;

	public CommandEventParameter(ParameterType paramType, String name, String id) {
		super();
		this.paramType = paramType;
		this.name = name;
		this.id = id;
	}

	@Override
	public ParameterType getParameterType() {
		return paramType;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public IParameterValues getValues() throws ParameterValuesException {
		return Collections::emptyMap;
	}

	@Override
	public boolean isOptional() {
		return false;
	}

}