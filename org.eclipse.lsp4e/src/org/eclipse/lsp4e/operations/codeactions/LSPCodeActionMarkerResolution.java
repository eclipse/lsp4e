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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.operations.diagnostics.LSPDiagnosticsToMarkers;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.IMarkerResolution;
import org.eclipse.ui.IMarkerResolutionGenerator2;
import org.eclipse.ui.views.markers.WorkbenchMarkerResolution;

public class LSPCodeActionMarkerResolution extends WorkbenchMarkerResolution implements IMarkerResolutionGenerator2 {

	private static final String LSP_REMEDIATION = "lspCodeActions"; //$NON-NLS-1$

	@Override
	public IMarkerResolution[] getResolutions(IMarker marker) {
		Object att;
		try {
			att = marker.getAttribute(LSP_REMEDIATION);
		} catch (CoreException e) {
			e.printStackTrace();
			return null;
		}
		if (att == null) {
			return null;
		}
		List<? extends Command> commands = (List<? extends Command>)att;
		List<IMarkerResolution> res = new ArrayList<>(commands.size());
		for (Command command : commands) {
			res.add(new CodeActionMarkerResolution(command));
		}
		return res.toArray(new IMarkerResolution[res.size()]);
	}

	@Override
	public String getDescription() {
		return Messages.codeActions_description;
	}

	@Override
	public Image getImage() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getLabel() {
		return Messages.codeActions_label;
	}

	@Override
	public void run(IMarker marker) {
		// TODO Auto-generated method stub
	}

	@Override
	public boolean hasResolutions(IMarker marker) {
		List<? extends Command> resolutions = null;
		try {
			if (marker.getAttribute(LSP_REMEDIATION) != null) {
				resolutions = (List<? extends Command>)marker.getAttribute(LSP_REMEDIATION);
			} else if (marker.getResource().getType() == IResource.FILE) {
				LanguageServer lsp = LanguageServiceAccessor.getLanguageServer((IFile)marker.getResource(), (capabilities) -> Boolean.TRUE.equals(capabilities.getCodeActionProvider()));
				if (lsp != null) {
					Diagnostic diagnostic = (Diagnostic)marker.getAttribute(LSPDiagnosticsToMarkers.LSP_DIAGNOSTIC);
					CodeActionContext context = new CodeActionContext(Collections.singletonList(diagnostic));
					CodeActionParams params = new CodeActionParams();
					params.setContext(context);
					params.setTextDocument(new TextDocumentIdentifier(marker.getResource().getLocation().toFile().toURI().toString()));
					params.setRange(diagnostic.getRange());
					CompletableFuture<List<? extends Command>> codeAction = lsp.getTextDocumentService().codeAction(params);
					resolutions = codeAction.get();
					marker.setAttribute(LSP_REMEDIATION, resolutions);
				}
			}
		} catch (Exception ex) {
			LanguageServerPlugin.logError(ex);
		}
		return resolutions != null && !resolutions.isEmpty();
	}

	@Override
	public IMarker[] findOtherMarkers(IMarker[] markers) {
		return null;
	}
}
