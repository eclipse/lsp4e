/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Lucas Bullen (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.outline;

import org.eclipse.core.expressions.PropertyTester;
import org.eclipse.ui.views.contentoutline.ContentOutline;

public class HasCNFOutlinePage extends PropertyTester {

	@Override
	public boolean test(Object receiver, String property, Object[] args, Object expectedValue) {
		if (receiver instanceof ContentOutline) {
			ContentOutline outline = (ContentOutline) receiver;
			return outline.getCurrentPage() instanceof CNFOutlinePage;
		}
		return false;
	}

}
