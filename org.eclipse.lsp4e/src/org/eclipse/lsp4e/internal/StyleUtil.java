/*******************************************************************************
 * Copyright (c) 2023 Avaloq Group AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Rubén Porras Campo (Avaloq Group AG) - Initial Implementation
 *******************************************************************************/
package org.eclipse.lsp4e.internal;

import org.eclipse.jface.viewers.StyledString.Styler;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.TextStyle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;

public class StyleUtil {

	private StyleUtil() {
		// this class shouldn't be instantiated
	}

	private static Color darkGray;

	static {
		Display display = PlatformUI.getWorkbench().getDisplay();
		display.asyncExec(() -> darkGray = display.getSystemColor(SWT.COLOR_DARK_GRAY));
	}

	public static final Styler DEPRECATE = new Styler() {
		@Override
		public void applyStyles(TextStyle textStyle) {
			textStyle.strikeout = true;
			textStyle.background = darkGray;
		};
	};
}
