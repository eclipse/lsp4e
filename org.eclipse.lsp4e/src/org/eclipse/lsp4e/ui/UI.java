/*******************************************************************************
 * Copyright (c) 2021, 2024 Vegard IT GmbH and others.
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
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
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
		return asTextEditor(activePage.getActiveEditor());
	}

	@Nullable
	public static ITextEditor asTextEditor(@Nullable IEditorPart editorPart) {
		if (editorPart instanceof ITextEditor textEditor) {
			return textEditor;
		} else if (editorPart instanceof MultiPageEditorPart multiPageEditorPart
				&& multiPageEditorPart.getSelectedPage() instanceof ITextEditor textEditor) {
			return textEditor;
		}
		return null;
//		TODO consider returning Adapters.adapt(editorPart, ITextEditor.class) instead
	}

	@Nullable
	public static ITextViewer asTextViewer(@Nullable IEditorPart editorPart) {
		if (editorPart != null) {
			return editorPart.getAdapter(ITextViewer.class);
//			TODO consider returning Adapters.adapt(asTextEditor(editorPart), ITextViewer.class)
		}
		return null;
	}

	@Nullable
	public static ITextViewer getActiveTextViewer() {
		return asTextViewer(getActiveTextEditor());
	}

	@Nullable
	public static IWorkbenchWindow getActiveWindow() {
		return PlatformUI.getWorkbench().getActiveWorkbenchWindow();
	}

	/**
	 * @return the current display
	 */
	public static Display getDisplay() {
		if (PlatformUI.isWorkbenchRunning())
			return PlatformUI.getWorkbench().getDisplay();

		final var display = Display.getCurrent();
		if (display != null)
			return display;

		return Display.getDefault();
	}

	public static void runOnUIThread(Runnable runnable) {
		if (Display.getCurrent() == null) {
			getDisplay().asyncExec(runnable);
		} else {
			runnable.run();
		}
	}

	private UI() {
	}

}
