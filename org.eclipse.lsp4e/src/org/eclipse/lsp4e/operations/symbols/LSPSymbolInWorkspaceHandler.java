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

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.text.contentassist.BoldStylerProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.LanguageServers.LanguageServerProjectExecutor;
import org.eclipse.lsp4e.internal.LSPDocumentAbstractHandler;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.handlers.HandlerUtil;

public class LSPSymbolInWorkspaceHandler extends LSPDocumentAbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IEditorPart part = HandlerUtil.getActiveEditor(event);
		IResource resource = null;
		if (part != null && part.getEditorInput() != null) {
			resource = part.getEditorInput().getAdapter(IResource.class);
		} else {
			IStructuredSelection selection = HandlerUtil.getCurrentStructuredSelection(event);
			if (selection.getFirstElement() instanceof IAdaptable adaptable) {
				resource = adaptable.getAdapter(IResource.class);
			}
		}
		if (resource == null) {
			return null;
		}

		IProject project = resource.getProject();
		final LanguageServerProjectExecutor executor = LanguageServers.forProject(project).withCapability(ServerCapabilities::getWorkspaceSymbolProvider);
		if (!executor.anyMatching()) {
			return null;
		}

		IWorkbenchSite site = HandlerUtil.getActiveSite(event);
		if (site == null) {
			return null;
		}
		var styleProvider = new BoldStylerProvider(site.getShell().getFont());
		final var dialog = new LSPSymbolInWorkspaceDialog(site.getShell(), project, styleProvider);
		int code = dialog.open();
		styleProvider.dispose();
		if (code != IDialogConstants.OK_ID) {
			return null;
		}
		final var symbolInformation = ((WorkspaceSymbol) dialog.getFirstResult()).getLocation();
		if (symbolInformation.isLeft()) {
			LSPEclipseUtils.openInEditor(symbolInformation.getLeft());
		} else if (symbolInformation.isRight()) {
			LSPEclipseUtils.open(symbolInformation.getRight().getUri(), null);
		}

		return null;
	}

	@Override
	public void setEnabled(Object evaluationContext) {
		setEnabled(ServerCapabilities::getWorkspaceSymbolProvider, x -> true);
	}

}
