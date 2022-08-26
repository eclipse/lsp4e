/*******************************************************************************
 * Copyright (c) 2017 Kichwa Coders Ltd. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.debug.breakpoints;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.ILineBreakpoint;
import org.eclipse.debug.ui.actions.IToggleBreakpointsTarget;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.lsp4e.debug.DSPPlugin;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.part.MultiPageEditorPart;
import org.eclipse.ui.texteditor.ITextEditor;

public class DSPBreakpointAdapter implements IToggleBreakpointsTarget {
	@Override
	public void toggleLineBreakpoints(IWorkbenchPart part, ISelection selection) throws CoreException {
		ITextEditor textEditor = getEditor(part);
		if (textEditor != null) {
			IResource resource = textEditor.getEditorInput().getAdapter(IResource.class);
			if (resource != null) {
				ITextSelection textSelection = (ITextSelection) selection;
				int lineNumber = textSelection.getStartLine();
				IBreakpoint[] breakpoints = DebugPlugin.getDefault().getBreakpointManager()
						.getBreakpoints(DSPPlugin.ID_DSP_DEBUG_MODEL);
				for (int i = 0; i < breakpoints.length; i++) {
					IBreakpoint breakpoint = breakpoints[i];
					if (breakpoint instanceof ILineBreakpoint lineBreakpoint
							&& resource.equals(breakpoint.getMarker().getResource())
							&& lineBreakpoint.getLineNumber() == (lineNumber + 1)) {
						// remove
						breakpoint.delete();
						return;
					}
				}
				// create line breakpoint (doc line numbers start at 0)
				DSPLineBreakpoint lineBreakpoint = new DSPLineBreakpoint(resource, lineNumber + 1);
				DebugPlugin.getDefault().getBreakpointManager().addBreakpoint(lineBreakpoint);
			}
		}
	}

	@Override
	public boolean canToggleLineBreakpoints(IWorkbenchPart part, ISelection selection) {
		// TODO
		return true;
	}

	private ITextEditor getEditor(IWorkbenchPart part) {
		if (part instanceof ITextEditor textEditor) {
			return textEditor;
		}
		if (part instanceof MultiPageEditorPart multiPageEditorPart
				&& ((MultiPageEditorPart) part).getSelectedPage() instanceof ITextEditor textEditor) {
			return textEditor;
		}
		return null;
	}

	@Override
	public void toggleMethodBreakpoints(IWorkbenchPart part, ISelection selection) throws CoreException {
		// TODO
	}

	@Override
	public boolean canToggleMethodBreakpoints(IWorkbenchPart part, ISelection selection) {
		// TODO
		return false;
	}

	@Override
	public void toggleWatchpoints(IWorkbenchPart part, ISelection selection) throws CoreException {
		// TODO
	}

	@Override
	public boolean canToggleWatchpoints(IWorkbenchPart part, ISelection selection) {
		// TODO
		return false;
	}

}
