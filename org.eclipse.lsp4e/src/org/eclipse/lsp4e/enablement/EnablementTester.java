/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Rastislav Wagner (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.enablement;

import org.eclipse.core.expressions.EvaluationContext;
import org.eclipse.core.expressions.EvaluationResult;
import org.eclipse.core.expressions.Expression;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.lsp4e.LanguageServerPlugin;

/**
 * Represents enabledWhen element from plugin.xml
 *
 * @author rawagner
 *
 */
public class EnablementTester {

	Expression expression;
	String description;

	public EnablementTester(Expression expression, String description) {
		this.description = description;
		this.expression = expression;
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
	public boolean evaluate() {
		try {
			return expression.evaluate(new EvaluationContext(null, new Object())).equals(EvaluationResult.TRUE);
		} catch (CoreException e) {
			LanguageServerPlugin.logError("Error occured during evaluation of enablement expression", e); //$NON-NLS-1$
		}
		return false;
	}

}
