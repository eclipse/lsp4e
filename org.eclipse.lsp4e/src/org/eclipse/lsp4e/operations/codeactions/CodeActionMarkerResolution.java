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
 *******************************************************************************/
package org.eclipse.lsp4e.operations.codeactions;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServersRegistry;
import org.eclipse.lsp4e.LanguageServersRegistry.LanguageServerDefinition;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.operations.diagnostics.LSPDiagnosticsToMarkers;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.services.LanguageServer;
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

		if (marker.getResource().getType() == IResource.FILE) {
			String languageServerId = marker.getAttribute(LSPDiagnosticsToMarkers.LANGUAGE_SERVER_ID, null);
			if (languageServerId != null) {
				IFile file = (IFile) marker.getResource();
				LanguageServerDefinition definition = LanguageServersRegistry.getInstance()
						.getDefinition(languageServerId);
				if (file != null && definition != null) {
					try {
						CompletableFuture<LanguageServer> languageServerFuture = LanguageServiceAccessor
								.getInitializedLanguageServer(file, definition, serverCapabilities -> {
									ExecuteCommandOptions provider = serverCapabilities.getExecuteCommandProvider();
									return provider != null && provider.getCommands().contains(command.getCommand());
								});
						if (languageServerFuture != null) {
							languageServerFuture.thenAccept(server -> {
								ExecuteCommandParams params = new ExecuteCommandParams();
								params.setCommand(command.getCommand());
								params.setArguments(command.getArguments());
								server.getWorkspaceService().executeCommand(params);
							});
						}
					} catch (IOException e) {
						// log and let the code fall through for LSPEclipseUtils to handle
						LanguageServerPlugin.logError(e);
					}
				}
			}
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
