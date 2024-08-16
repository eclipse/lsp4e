/*******************************************************************************
 * Copyright (c) 2019 Kichwa Coders Canada Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.debug.breakpoints;

import java.util.Collections;
import java.util.Set;

import org.eclipse.debug.ui.actions.IToggleBreakpointsTarget;
import org.eclipse.debug.ui.actions.IToggleBreakpointsTargetFactory;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.lsp4e.debug.DSPPlugin;
import org.eclipse.ui.IWorkbenchPart;

public class ToggleBreakpointsTargetFactory implements IToggleBreakpointsTargetFactory {

	private static final DSPBreakpointAdapter DSP_BREAKPOINT_ADAPTER = new DSPBreakpointAdapter();

	public static final String TOGGLE_BREAKPOINT_TARGET_ID = DSPPlugin.PLUGIN_ID + ".toggleBreakpointTarget"; //$NON-NLS-1$

	private static final Set<String> TOGGLE_TARGET_IDS = Collections.singleton(TOGGLE_BREAKPOINT_TARGET_ID);

	@Override
	public Set<String> getToggleTargets(IWorkbenchPart part, ISelection selection) {
		return TOGGLE_TARGET_IDS;
	}

	@Override
	public @Nullable String getDefaultToggleTarget(IWorkbenchPart part, ISelection selection) {
		return null;
	}

	@Override
	public @Nullable IToggleBreakpointsTarget createToggleTarget(String targetID) {
		if (TOGGLE_BREAKPOINT_TARGET_ID.equals(targetID)) {
			return DSP_BREAKPOINT_ADAPTER;
		}
		return null;
	}

	@Override
	public @Nullable String getToggleTargetName(String targetID) {
		if (TOGGLE_BREAKPOINT_TARGET_ID.equals(targetID)) {
			return "LSP4E Breakpoint";
		}
		return null;
	}

	@Override
	public @Nullable String getToggleTargetDescription(String targetID) {
		if (TOGGLE_BREAKPOINT_TARGET_ID.equals(targetID)) {
			return "LSP4E Breakpoint for Debug Adapter";
		}
		return null;

	}

}
