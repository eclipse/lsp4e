/**
 *  Copyright (c) 2017-2021 Angelo ZERR.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *  Angelo Zerr <angelo.zerr@gmail.com> - initial API and implementation
 *  Lucas Bullen (Red Hat Inc.) - [Bug 517428] Requests sent before initialization
 *  Jan Koehnlein (TypeFox) - give user feedback on failures and no-ops
 *  Pierre-Yves B. <pyvesdev@gmail.com> - Bug 525411 - [rename] input field should be filled with symbol to rename
 */
package org.eclipse.lsp4e.operations.rename;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.PrepareRenameParams;
import org.eclipse.lsp4j.PrepareRenameResult;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RenameOptions;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.services.LanguageServer;
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

	private final IDocument document;
	private final LanguageServer languageServer;
	private final int offset;

	private String newName;

	private WorkspaceEdit rename;
	private Either<Range, PrepareRenameResult> prepareRenameResult;

	public LSPRenameProcessor(@NonNull IDocument document, LanguageServer languageServer, int offset) {
		this.document = document;
		this.languageServer = languageServer;
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
		RefactoringStatus status = new RefactoringStatus();

		if (document == null) {
			return status;
		}
		try {
			CompletableFuture<List<LanguageServer>> serverList = LanguageServiceAccessor.getLanguageServers(document, LSPRenameProcessor::isPrepareRenameProvider) ;
			for (LanguageServer serverToTry : serverList.get(500, TimeUnit.MILLISECONDS)) {
				// check if prepareRename is supported by the active LSP
				if (languageServer.equals(serverToTry)) {
					TextDocumentIdentifier identifier = new TextDocumentIdentifier(LSPEclipseUtils.toUri(document).toString());
					PrepareRenameParams params = new PrepareRenameParams();
					params.setTextDocument(identifier);
					params.setPosition(LSPEclipseUtils.toPosition(offset, document));
					prepareRenameResult = languageServer.getTextDocumentService().prepareRename(params).get(1000, TimeUnit.MILLISECONDS);
					if (prepareRenameResult == null) {
						status.addFatalError(Messages.rename_invalidated);
					}
				}
			}
		} catch (Exception e) {
			handleError(e, status);
			return new RefactoringStatus();
		}
		return status;
	}

	public String getPlaceholder() {
		if (prepareRenameResult != null) {
			if (prepareRenameResult.isRight()) {
				return prepareRenameResult.getRight().getPlaceholder();
			} else {
				Range range = prepareRenameResult.getLeft();
				try {
					int startOffset = LSPEclipseUtils.toOffset(range.getStart(), document);
					int endOffset = LSPEclipseUtils.toOffset(range.getEnd(), document);
					return document.get(startOffset, endOffset - startOffset);
				} catch (BadLocationException e) {
					LanguageServerPlugin.logError(e);
				}
			}
		}
		return "newName"; //$NON-NLS-1$
	}

	public static boolean isPrepareRenameProvider(ServerCapabilities serverCapabilities) {
		if (serverCapabilities == null) {
			return false;
		}
		Either<Boolean, RenameOptions> renameProvider = serverCapabilities.getRenameProvider();
		if (renameProvider == null) {
			return false;
		}

		if (renameProvider.isRight()) {
			return renameProvider.getRight() != null && renameProvider.getRight().getPrepareProvider();
		}
		return false;
	}

	@Override
	public RefactoringStatus checkFinalConditions(IProgressMonitor pm, CheckConditionsContext context)
			throws CoreException, OperationCanceledException {
		RefactoringStatus status = new RefactoringStatus();
		try {
			RenameParams params = new RenameParams();
			params.setPosition(LSPEclipseUtils.toPosition(offset, document));
			TextDocumentIdentifier identifier = new TextDocumentIdentifier();
			identifier.setUri(LSPEclipseUtils.toUri(document).toString());
			params.setTextDocument(identifier);
			params.setNewName(newName);
			if (params.getNewName() != null) {
				// TODO: how to manage ltk with CompletableFuture? Is 1000 ms is enough?
				rename = languageServer.getTextDocumentService().rename(params).get(1000, TimeUnit.MILLISECONDS);
				if (!status.hasError() && (rename == null || rename.getChanges().isEmpty())) {
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
			status.addFatalError(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
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
