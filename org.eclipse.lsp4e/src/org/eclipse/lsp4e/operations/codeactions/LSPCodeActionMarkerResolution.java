/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.resource.JFaceResources;
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
import org.eclipse.ui.IMarkerResolution2;
import org.eclipse.ui.IMarkerResolutionGenerator2;
import org.eclipse.ui.internal.progress.ProgressInfoItem;

public class LSPCodeActionMarkerResolution implements IMarkerResolutionGenerator2 {

	private static final String LSP_REMEDIATION = "lspCodeActions"; //$NON-NLS-1$

	private static final IMarkerResolution2 COMPUTING = new IMarkerResolution2() {

		@Override
		public void run(IMarker marker) {
			// TODO Auto-generated method stub
			// join on Future?
		}

		@Override
		public String getLabel() {
			// TODO Auto-generated method stub
			return Messages.computing;
		}

		@Override
		public Image getImage() {
			Class<?> clazz = ProgressInfoItem.class;
			// load class so image is loaded
			return JFaceResources.getImage("org.eclipse.ui.internal.progress.PROGRESS_DEFAULT"); //$NON-NLS-1$
		}

		@Override
		public String getDescription() {
			return Messages.computing;
		}
	};

	@Override
	public IMarkerResolution[] getResolutions(IMarker marker) {
		Object att;
		try {
			checkMarkerResoultion(marker);
			att = marker.getAttribute(LSP_REMEDIATION);
		} catch (Exception e) {
			LanguageServerPlugin.logError(e);
			return new IMarkerResolution[0];
		}
		if (att == COMPUTING) {
			return new IMarkerResolution[] { COMPUTING };
		}
		List<? extends Command> commands = (List<? extends Command>)att;
		List<IMarkerResolution> res = new ArrayList<>(commands.size());
		for (Command command : commands) {
			res.add(new CodeActionMarkerResolution(command));
		}
		return res.toArray(new IMarkerResolution[res.size()]);
	}

	private void checkMarkerResoultion(IMarker marker) throws Exception {
		if (marker.getAttribute(LSP_REMEDIATION) != null) {
			return;
		} else if (marker.getResource().getType() == IResource.FILE) {
			LanguageServer lsp = LanguageServiceAccessor.getLanguageServer((IFile)marker.getResource(), (capabilities) -> Boolean.TRUE.equals(capabilities.getCodeActionProvider()));
			if (lsp != null) {
				marker.setAttribute(LSP_REMEDIATION, COMPUTING);
				Diagnostic diagnostic = (Diagnostic)marker.getAttribute(LSPDiagnosticsToMarkers.LSP_DIAGNOSTIC);
				CodeActionContext context = new CodeActionContext(Collections.singletonList(diagnostic));
				CodeActionParams params = new CodeActionParams();
				params.setContext(context);
				params.setTextDocument(new TextDocumentIdentifier(marker.getResource().getLocation().toFile().toURI().toString()));
				params.setRange(diagnostic.getRange());
				CompletableFuture<List<? extends Command>> codeAction = lsp.getTextDocumentService().codeAction(params);
				codeAction.thenAccept(actions -> {
					try {
						marker.setAttribute(LSP_REMEDIATION, actions);
					} catch (CoreException e) {
						LanguageServerPlugin.logError(e);
					}
				});
			}
		}
	}

	@Override
	public boolean hasResolutions(IMarker marker) {
		try {
			checkMarkerResoultion(marker);
			Object remediation = marker.getAttribute(LSP_REMEDIATION);
			return remediation == COMPUTING || (remediation instanceof Collection && !((Collection)remediation).isEmpty());
		} catch (Exception ex) {
			LanguageServerPlugin.logError(ex);
		}
		return false;
	}
}
