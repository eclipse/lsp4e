/*******************************************************************************
 * Copyright (c) 2021 Vegard IT GmbH and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Sebastian Thomschke (Vegard IT GmbH) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.ui;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.MultiPageEditorPart;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * Common UI utilities
 */
public final class UI {

	@Nullable
	public static IWorkbenchPage getActivePage() {
		var window = getActiveWindow();
		return window == null ? null : window.getActivePage();
	}

	@Nullable
	public static IWorkbenchPart getActivePart() {
		var page = getActivePage();
		return page == null ? null : page.getActivePart();
	}

	@Nullable
	public static Shell getActiveShell() {
		var window = getActiveWindow();
		return window == null ? null : window.getShell();
	}

	@Nullable
	public static ITextEditor getActiveTextEditor() {
		var activePage = getActivePage();
		if (activePage == null) {
			return null;
		}
		var editorPart = activePage.getActiveEditor();
		if (editorPart instanceof ITextEditor) {
			return (ITextEditor) editorPart;
		} else if (editorPart instanceof MultiPageEditorPart) {
			MultiPageEditorPart multiPageEditorPart = (MultiPageEditorPart) editorPart;
			Object page = multiPageEditorPart.getSelectedPage();
			if (page instanceof ITextEditor) {
				return (ITextEditor) page;
			}
		}
		return null;
	}

	@Nullable
	public static IWorkbenchWindow getActiveWindow() {
		return PlatformUI.getWorkbench().getActiveWorkbenchWindow();
	}

	private UI() {
	}

}
