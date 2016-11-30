/*******************************************************************************
 * Copyright (c) 2016 Rogue Wave Software Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.languageserver.operations.symbols;

import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.languageserver.LSPEclipseUtils;
import org.eclipse.languageserver.LanguageServiceAccessor;
import org.eclipse.languageserver.LanguageServiceAccessor.LSPServerInfo;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;

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

		if (resource == null) {
			return null;
		}
		IProject project = resource.getProject();
		List<LSPServerInfo> infos = LanguageServiceAccessor.getLSPServerInfos(project,
				capabilities -> Boolean.TRUE.equals(capabilities.getWorkspaceSymbolProvider()));
		if (infos.isEmpty()) {
			return null;
		}
		final Shell shell = HandlerUtil.getActiveShell(event);
		LSPSymbolInWorkspaceDialog dialog = new LSPSymbolInWorkspaceDialog(shell, infos);
		if (dialog.open() != IDialogConstants.OK_ID) {
			return null;
		}
		SymbolInformation symbolInformation = (SymbolInformation) dialog.getFirstResult();
		Location location = symbolInformation.getLocation();

		IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		LSPEclipseUtils.openInEditor(location, page);
		return null;
	}

}
