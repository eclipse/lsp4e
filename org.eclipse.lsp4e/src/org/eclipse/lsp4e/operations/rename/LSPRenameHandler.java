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
package org.eclipse.lsp4e.operations.rename;

import java.util.concurrent.CompletableFuture;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.ui.texteditor.ITextEditor;

import com.google.common.base.Strings;

public class LSPRenameHandler extends AbstractHandler implements IHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IEditorPart part = HandlerUtil.getActiveEditor(event);
		if (part instanceof AbstractTextEditor) {
			LSPDocumentInfo info = LanguageServiceAccessor.getLSPDocumentInfoFor(
				LSPEclipseUtils.getDocument((ITextEditor) part),
				(capabilities) -> Boolean.TRUE.equals(capabilities.getRenameProvider()));
			if (info != null) {
				ISelection sel = ((AbstractTextEditor) part).getSelectionProvider().getSelection();
				if (sel instanceof TextSelection) {
					try {
						RenameParams params = new RenameParams();
						params.setPosition(LSPEclipseUtils.toPosition(((TextSelection) sel).getOffset(), info.getDocument()));
						TextDocumentIdentifier identifier = new TextDocumentIdentifier();
						identifier.setUri(info.getFileUri().toString());
						params.setTextDocument(identifier);
						params.setNewName(askNewName(part.getSite().getShell()));
						if (params.getNewName() != null) {
							CompletableFuture<WorkspaceEdit> rename = info.getLanguageClient().getTextDocumentService().rename(params);
							rename.thenAccept((WorkspaceEdit t) -> apply(t));
						}
					} catch (BadLocationException e) {
						LanguageServerPlugin.logError(e);
					}
				}
			}
		}
		return null;
	}

	private void apply(WorkspaceEdit workspaceEdit) {
		WorkspaceJob job = new WorkspaceJob(Messages.rename_job) {
			@Override
			public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
				LSPEclipseUtils.applyWorkspaceEdit(workspaceEdit);
				return Status.OK_STATUS;
			}
		};
		job.schedule();
	}

	@Override
	public boolean isEnabled() {
		IWorkbenchPart part = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActivePart();
		if (part instanceof AbstractTextEditor) {
			LSPDocumentInfo info = LanguageServiceAccessor.getLSPDocumentInfoFor(
				LSPEclipseUtils.getDocument((ITextEditor) part),
				(capabilities) ->
					Boolean.TRUE.equals(capabilities.getRenameProvider()));
			ISelection selection = ((AbstractTextEditor) part).getSelectionProvider().getSelection();
			return info != null && !selection.isEmpty() && selection instanceof ITextSelection;
		}
		return false;
	}

	private String askNewName(Shell parentShell) {
		InputDialog dialog = new InputDialog(parentShell,
				Messages.rename_title,
				Messages.rename_label,
				"newName", //$NON-NLS-1$
				s -> Strings.isNullOrEmpty(s) ? Messages.rename_invalid : null);
		dialog.setBlockOnOpen(true);
		if (dialog.open() == Dialog.OK) {
			return dialog.getValue();
		}
		return null;
	}

}
