/*******************************************************************************
 * Copyright (c) 2018, 2022 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Max Bureck (Fraunhofer FOKUS) - integeration with CommandExecutor
 *******************************************************************************/
package org.eclipse.lsp4e.operations.codeactions;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServersRegistry;
import org.eclipse.lsp4e.LanguageServersRegistry.LanguageServerDefinition;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.command.CommandExecutor;
import org.eclipse.lsp4e.operations.diagnostics.LSPDiagnosticsToMarkers;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.IMarkerResolution;
import org.eclipse.ui.views.markers.WorkbenchMarkerResolution;

public class CodeActionMarkerResolution extends WorkbenchMarkerResolution implements IMarkerResolution {

	private CodeAction codeAction;

	public CodeActionMarkerResolution(CodeAction codeAction) {
		this.codeAction = codeAction;
	}

	@Override
	public String getDescription() {
		return codeAction.getTitle();
	}

	@Override
	public Image getImage() {
		return null;
	}

	@Override
	public String getLabel() {
		return codeAction.getTitle();
	}

	@Override
	public void run(IMarker marker) {
		String languageServerId = marker.getAttribute(LSPDiagnosticsToMarkers.LANGUAGE_SERVER_ID, null);
		if (codeAction.getEdit() == null) {
			LanguageServerDefinition definition = languageServerId != null ? LanguageServersRegistry.getInstance().getDefinition(languageServerId) : null;
			try {
				LanguageServerWrapper wrapper = definition != null ? LanguageServiceAccessor.getLSWrapper(marker.getResource().getProject(), definition) : null;
				if (wrapper != null && CodeActionCompletionProposal.isCodeActionResolveSupported(wrapper.getServerCapabilities())) {
					CodeAction resolvedCodeAction = wrapper.execute(ls -> ls.getTextDocumentService().resolveCodeAction(codeAction)).get(2, TimeUnit.SECONDS);
					if (resolvedCodeAction != null) {
						codeAction = resolvedCodeAction;
					}
				}
			} catch (IOException | TimeoutException | ExecutionException | InterruptedException ex) {
				LanguageServerPlugin.logError(ex);
			}
		}
		if (codeAction.getEdit() != null) {
			LSPEclipseUtils.applyWorkspaceEdit(codeAction.getEdit(), codeAction.getTitle());
		}
		if (codeAction.getCommand() != null) {
			IResource resource = marker.getResource();
			IDocument document = LSPEclipseUtils.getExistingDocument(resource);
			boolean temporaryLoadDocument = document == null;
			if (temporaryLoadDocument) {
				document = LSPEclipseUtils.getDocument(resource);
			}
			if (document != null) {
				CommandExecutor.executeCommand(codeAction.getCommand(), document, languageServerId);
				if (temporaryLoadDocument) {
					try {
						FileBuffers.getTextFileBufferManager().disconnect(resource.getFullPath(), LocationKind.IFILE, new NullProgressMonitor());
					} catch (CoreException e) {
						LanguageServerPlugin.logError(e);
					}
				}
			}
		}
	}

	@Override
	public IMarker[] findOtherMarkers(IMarker[] markers) {
		if (markers == null) {
			return new IMarker[0];
		}
		return Arrays.stream(markers).filter(marker -> {
			try {
				return codeAction.getDiagnostics()
						.contains(marker.getAttribute(LSPDiagnosticsToMarkers.LSP_DIAGNOSTIC));
			} catch (CoreException e) {
				LanguageServerPlugin.logError(e);
				return false;
			}
		}).toArray(IMarker[]::new);
	}

}
