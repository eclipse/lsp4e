/*******************************************************************************
 * Copyright (c) 2016 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.symbols;

import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

public class LSPSymbolInWorkspaceHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IEditorPart part = HandlerUtil.getActiveEditor(event);
		IResource resource = null;
		if (part != null && part.getEditorInput() != null) {
			resource = part.getEditorInput().getAdapter(IResource.class);
		} else {
			IStructuredSelection selection = HandlerUtil.getCurrentStructuredSelection(event);
			if (selection.isEmpty() || !(selection.getFirstElement() instanceof IAdaptable)) {
				return null;
			}
			IAdaptable adaptable = (IAdaptable) selection.getFirstElement();
			resource = adaptable.getAdapter(IResource.class);
		}

		IProject project = resource.getProject();
		List<@NonNull LanguageServer> languageServers = LanguageServiceAccessor.getLanguageServers(project, capabilities -> LSPEclipseUtils.hasCapability(capabilities.getWorkspaceSymbolProvider()));
		if (languageServers.isEmpty()) {
			return null;
		}
		IWorkbenchSite site = HandlerUtil.getActiveSite(event);
		if (site == null) {
			return null;
		}
		LSPSymbolInWorkspaceDialog dialog = new LSPSymbolInWorkspaceDialog(site.getShell(), languageServers);
		if (dialog.open() != IDialogConstants.OK_ID) {
			return null;
		}
		SymbolInformation symbolInformation = (SymbolInformation) dialog.getFirstResult();
		Location location = symbolInformation.getLocation();

		LSPEclipseUtils.openInEditor(location, UI.getActivePage());
		return null;
	}

	@Override
	public boolean isEnabled() {
		IWorkbenchPart part = UI.getActivePart();
		if (part instanceof ITextEditor) {
			List<LSPDocumentInfo> infos = LanguageServiceAccessor.getLSPDocumentInfosFor(
					LSPEclipseUtils.getDocument((ITextEditor) part),
					capabilities -> LSPEclipseUtils.hasCapability(capabilities.getWorkspaceSymbolProvider()));
			return !infos.isEmpty();
		}
		return false;
	}

}
