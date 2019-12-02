/*******************************************************************************
 * Copyright (c) 2019 Fraunhofer FOKUS and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.command.internal;

import org.eclipse.core.commands.AbstractParameterValueConverter;
import org.eclipse.core.commands.ParameterValueConversionException;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4j.Command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * This converter can be used to serialize/deserailize instances of LSP {@link Command}s.
 */
public class CommandConverter extends AbstractParameterValueConverter {

	private final Gson gson;

	public CommandConverter() {
		this.gson = new GsonBuilder().create();
	}

	@Override
	public @Nullable Object convertToObject(String parameterValue) throws ParameterValueConversionException {
		return gson.fromJson(parameterValue, Command.class);
	}

	@Override
	public String convertToString(Object parameterValue) throws ParameterValueConversionException {
		return gson.toJson(parameterValue);
	}

}
