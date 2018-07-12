/**
 *  Copyright (c) 2017 Angelo ZERR.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *  Angelo Zerr <angelo.zerr@gmail.com> - initial API and implementation
 *  Lucas Bullen (Red Hat Inc.) - [Bug 517428] Requests sent before initialization
 *  Jan Koehnlein (TypeFox) - give user feedback on failures and no-ops
 */
package org.eclipse.lsp4e.operations.rename;

import java.util.concurrent.TimeUnit;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.RefactoringParticipant;
import org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor;
import org.eclipse.ltk.core.refactoring.participants.SharableParticipants;

/**
 * LTK {@link RefactoringProcessor} implementation to refactoring LSP symbols.
 *
 */
public class LSPRenameProcessor extends RefactoringProcessor {

	private static final String ID = "org.eclipse.lsp4e.operations.rename"; //$NON-NLS-1$

	private final LSPDocumentInfo info;
	private final int offset;

	private String newName;

	private WorkspaceEdit rename;

	public LSPRenameProcessor(LSPDocumentInfo info, int offset) {
		this.info = info;
		this.offset = offset;
	}

	@Override
	public Object[] getElements() {
		return null;
	}

	@Override
	public String getIdentifier() {
		return ID;
	}

	@Override
	public String getProcessorName() {
		return Messages.rename_processor_name;
	}

	@Override
	public boolean isApplicable() throws CoreException {
		return true;
	}

	@Override
	public RefactoringStatus checkInitialConditions(IProgressMonitor pm)
			throws CoreException, OperationCanceledException {
		return new RefactoringStatus();
	}

	@Override
	public RefactoringStatus checkFinalConditions(IProgressMonitor pm, CheckConditionsContext context)
			throws CoreException, OperationCanceledException {
		RefactoringStatus status = new RefactoringStatus();
		try {
			RenameParams params = new RenameParams();
			params.setPosition(LSPEclipseUtils.toPosition(offset, info.getDocument()));
			TextDocumentIdentifier identifier = new TextDocumentIdentifier();
			identifier.setUri(info.getFileUri().toString());
			params.setTextDocument(identifier);
			params.setNewName(newName);
			if (params.getNewName() != null) {
				// TODO: how to manage ltk with CompletableFuture? Is 1000 ms is enough?
				rename = info.getInitializedLanguageClient()
						.thenCompose(langaugeServer -> langaugeServer.getTextDocumentService().rename(params))
						.get(1000, TimeUnit.MILLISECONDS);
				if (!status.hasError() && rename.getChanges().isEmpty()) {
					status.addWarning(Messages.rename_empty_message);
				}
			}
		} catch (Exception e) {
			handleError(e, status);
		}
		return status;
	}

	private WorkspaceEdit handleError(Throwable e, RefactoringStatus status) {
		if (e.getCause() instanceof ResponseErrorException) {
			ResponseError responseError = ((ResponseErrorException) e.getCause()).getResponseError();
			String message = responseError.getMessage()
					+ ((responseError.getData() instanceof String) ? (": " + responseError.getData()) : ""); //$NON-NLS-1$ //$NON-NLS-2$
			status.addFatalError(message);
		} else {
			status.addFatalError(e.getMessage());
		}
		return null;
	}

	@Override
	public Change createChange(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		if (rename == null) {
			throw new CoreException(
					new Status(IStatus.ERROR, LanguageServerPlugin.PLUGIN_ID, Messages.rename_processor_required));
		}
		return LSPEclipseUtils.toCompositeChange(rename);
	}

	@Override
	public RefactoringParticipant[] loadParticipants(RefactoringStatus status, SharableParticipants sharedParticipants)
			throws CoreException {
		return null;
	}

	/**
	 * Set new name.
	 *
	 * @param newName
	 *            the new name.
	 */
	public void setNewName(String newName) {
		Assert.isNotNull(newName);
		this.newName = newName;
	}
}
