/*******************************************************************************
 * Copyright (c) 2019 Fraunhofer FOKUS and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.command.internal;

import java.util.Objects;

import org.eclipse.core.commands.AbstractParameterValueConverter;
import org.eclipse.core.commands.ParameterValueConversionException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

/**
 * Serializes {@link IPath} instances and de-serializes them to {@link Path} instances.
 */
public class PathConverter extends AbstractParameterValueConverter {

	@Override
	public Object convertToObject(String parameterValue) throws ParameterValueConversionException {
		return new Path(parameterValue);
	}

	@Override
	public String convertToString(Object parameterValue) throws ParameterValueConversionException {
		return Objects.toString(parameterValue);
	}

}
