/*******************************************************************************
 * Copyright (c) 2022 VMware Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Alex Boyko (VMware Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.enablement;

import java.util.function.Supplier;

import org.eclipse.core.expressions.EvaluationContext;
import org.eclipse.core.expressions.EvaluationResult;
import org.eclipse.core.expressions.Expression;
import org.eclipse.core.expressions.IEvaluationContext;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.lsp4e.LanguageServerPlugin;

public final class InputEnablementTester {

	private final Expression expression;
	private final String description;
	private final Supplier<IEvaluationContext> parent;

	public InputEnablementTester(Supplier<IEvaluationContext> parent, Expression expression, String description) {
		this.description = description;
		this.expression = expression;
		this.parent = parent;
	}

	/**
	 *
	 * @return enablement test description
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * Evaluates enablement expression
	 *
	 * @return true if expression evaluates to true, false otherwise
	 */
	public boolean evaluate(Object input) {
		try {
			EvaluationContext context = new EvaluationContext(parent.get(), input);
			context.setAllowPluginActivation(true);
			return expression.evaluate(context).equals(EvaluationResult.TRUE);
		} catch (CoreException e) {
			LanguageServerPlugin.logError("Error occured during evaluation of enablement for the input expression", e); //$NON-NLS-1$
		}
		return false;
	}


}
