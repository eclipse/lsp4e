/*******************************************************************************
 * Copyright (c) 2017 Kichwa Coders Ltd. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.lsp4e.debug.breakpoints;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.LineBreakpoint;
import org.eclipse.lsp4e.debug.DSPPlugin;

public class DSPLineBreakpoint extends LineBreakpoint {

	public static final String ID = "org.eclipse.lsp4e.debug.breakpoints.markerType.lineBreakpoint";

	public DSPLineBreakpoint() {
	}

	public DSPLineBreakpoint(final IResource resource, final int lineNumber) throws CoreException {
		run(getMarkerRule(resource), monitor -> {
			IMarker marker = resource.createMarker(ID);
			setMarker(marker);
			marker.setAttribute(IBreakpoint.ENABLED, Boolean.TRUE);
			marker.setAttribute(IMarker.LINE_NUMBER, lineNumber);
			marker.setAttribute(IBreakpoint.ID, getModelIdentifier());
			marker.setAttribute(IMarker.MESSAGE, resource.getName() + " [line: " + lineNumber + "]");
		});
	}

	public DSPLineBreakpoint(final IResource resource, String fileName, final int lineNumber) throws CoreException {
		run(getMarkerRule(resource), monitor -> {
			IMarker marker = resource.createMarker(ID);
			setMarker(marker);
			marker.setAttribute(IBreakpoint.ENABLED, Boolean.TRUE);
			marker.setAttribute(IMarker.LINE_NUMBER, lineNumber);
			marker.setAttribute(IBreakpoint.ID, getModelIdentifier());
			marker.setAttribute(IMarker.MESSAGE, resource.getName() + " [line: " + lineNumber + "]");
		});
	}

	@Override
	public String getModelIdentifier() {
		return DSPPlugin.ID_DSP_DEBUG_MODEL;
	}
}
