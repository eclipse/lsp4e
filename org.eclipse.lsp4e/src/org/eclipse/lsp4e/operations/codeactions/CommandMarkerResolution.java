/*******************************************************************************
 * Copyright (c) 2016, 2017 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Max Bureck (Fraunhofer FOKUS) - Moved command execution to CommandExecutor
 *******************************************************************************/
package org.eclipse.lsp4e.operations.codeactions;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServersRegistry;
import org.eclipse.lsp4e.LanguageServersRegistry.LanguageServerDefinition;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.command.CommandExecutor;
import org.eclipse.lsp4e.operations.diagnostics.LSPDiagnosticsToMarkers;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.IMarkerResolution;
import org.eclipse.ui.views.markers.WorkbenchMarkerResolution;

public class CommandMarkerResolution extends WorkbenchMarkerResolution implements IMarkerResolution {

	private static final IMarker[] NO_MARKERS = new IMarker[0];

	private final Command command;

	public CommandMarkerResolution(Command command) {
		this.command = command;
	}

	@Override
	public String getLabel() {
		return this.command.getTitle();
	}

	@Override
	public void run(IMarker marker) {
		IResource resource = marker.getResource();
		if (resource == null) {
			return;
		}
		String languageServerId = marker.getAttribute(LSPDiagnosticsToMarkers.LANGUAGE_SERVER_ID, null);
		LanguageServerDefinition definition = languageServerId != null
				? LanguageServersRegistry.getInstance().getDefinition(languageServerId)
				: null;

		if (definition == null) {
			return;
		}

		LanguageServerWrapper wrapper = LanguageServiceAccessor.getLSWrapper(resource.getProject(), definition);
		ServerCapabilities cap = wrapper.getServerCapabilities();
		ExecuteCommandOptions provider = cap == null ? null : cap.getExecuteCommandProvider();
		if (provider != null && provider.getCommands().contains(command.getCommand())) {
			wrapper.execute(ls -> ls.getWorkspaceService()
					.executeCommand(new ExecuteCommandParams(command.getCommand(), command.getArguments())));
		} else {
			CommandExecutor.executeCommandClientSide(command, resource);
		}
	}

	@Override
	public String getDescription() {
		return command.getTitle();
	}

	@Override
	public @Nullable Image getImage() {
		return null;
	}

	@Override
	public IMarker[] findOtherMarkers(IMarker[] markers) {
		return NO_MARKERS;
	}

}
