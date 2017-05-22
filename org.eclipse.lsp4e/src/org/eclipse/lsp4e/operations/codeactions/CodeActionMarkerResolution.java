/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.codeactions;

import org.eclipse.core.resources.IMarker;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.IMarkerResolution;
import org.eclipse.ui.views.markers.WorkbenchMarkerResolution;

public class CodeActionMarkerResolution extends WorkbenchMarkerResolution implements IMarkerResolution {

	private @NonNull Command command;

	public CodeActionMarkerResolution(@NonNull Command command) {
		this.command = command;
	}

	@Override
	public String getLabel() {
		return this.command.getTitle();
	}

	@Override
	public void run(IMarker marker) {
		// This is a *client-side* command, no need to go through workspace/executeCommand operation
		// TODO? Consider binding LS commands to Eclipse commands and handlers???
		if (command.getArguments() == null) {
			return;
		}
		WorkspaceEdit edit = LSPEclipseUtils.createWorkspaceEdit(command.getArguments(), marker.getResource());
		LSPEclipseUtils.applyWorkspaceEdit(edit);
	}

	@Override
	public String getDescription() {
		return command.getTitle();
	}

	@Override
	public Image getImage() {
		return null;
	}

	@Override
	public IMarker[] findOtherMarkers(IMarker[] markers) {
		// TODO Auto-generated method stub
		return new IMarker[0];
	}

}
