/**
 *  Copyright (c) 2017-2023 Angelo ZERR.
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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.internal.Pair;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior;
import org.eclipse.lsp4j.PrepareRenameParams;
import org.eclipse.lsp4j.PrepareRenameResult;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RenameOptions;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
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

	private final @NonNull IDocument document;
	private final int offset;

	private LanguageServerWrapper refactoringServer;

	private String newName;

	private WorkspaceEdit rename;
	private Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior> prepareRenameResult;

	public LSPRenameProcessor(@NonNull IDocument document, int offset) {
		this.document = document;
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
		final var status = new RefactoringStatus();

		try {
			final var identifier = LSPEclipseUtils.toTextDocumentIdentifier(document);
			final var params = new PrepareRenameParams();
			params.setTextDocument(identifier);
			params.setPosition(LSPEclipseUtils.toPosition(offset, document));

			@SuppressWarnings("null")
			List<Pair<LanguageServerWrapper, Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>>> list = LanguageServers
					.forDocument(document).withFilter(LSPRenameProcessor::isPrepareRenameProvider)
					.collectAll((w, ls) -> ls.getTextDocumentService().prepareRename(params)
							.thenApply(result -> new Pair<>(w, result)))
					.get(1000, TimeUnit.MILLISECONDS);

			Optional<Pair<LanguageServerWrapper, Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>>> tmp = list
					.stream().filter(Objects::nonNull).filter(t -> t.getSecond() != null).findFirst();

			if (tmp.isEmpty()) {
				status.addFatalError(Messages.rename_invalidated);
			} else {
				tmp.ifPresent(p -> {
					refactoringServer = p.getFirst();
					prepareRenameResult = p.getSecond();
				});
			}
		} catch (TimeoutException e) {
			LanguageServerPlugin.logWarning("Could not prepare rename due to timeout after 1 seconds in `textDocument/prepareRename`. 'newName' will be used", e); //$NON-NLS-1$
		} catch (Exception e) {
			status.addFatalError(getErrorMessage(e));
		}
		return status;
	}

	public String getPlaceholder() {
		@Nullable String placeholder = null;
		if (prepareRenameResult != null) {
			placeholder = prepareRenameResult.map(range -> {
				try {
					int startOffset = LSPEclipseUtils.toOffset(range.getStart(), document);
					int endOffset = LSPEclipseUtils.toOffset(range.getEnd(), document);
					return document.get(startOffset, endOffset - startOffset);
				} catch (BadLocationException e) {
					LanguageServerPlugin.logError(e);
					return null;
				}
			}, PrepareRenameResult::getPlaceholder,
			options -> null);
		}
		return placeholder != null && !placeholder.isBlank() ? placeholder :"newName"; //$NON-NLS-1$
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
		final var status = new RefactoringStatus();
		try {
			final var params = new RenameParams();
			params.setPosition(LSPEclipseUtils.toPosition(offset, document));
			final var identifier = LSPEclipseUtils.toTextDocumentIdentifier(document);
			identifier.setUri(LSPEclipseUtils.toUri(document).toString());
			params.setTextDocument(identifier);
			params.setNewName(newName);
			if (params.getNewName() != null && refactoringServer != null) {
				// TODO: how to manage ltk with CompletableFuture? Is 1000 ms is enough?
				if (refactoringServer != null) {
					rename = refactoringServer.execute(ls -> ls.getTextDocumentService().rename(params)).get(1000, TimeUnit.MILLISECONDS);
				} else {
					// Prepare timed out so we don't have a preferred server, so just try all the servers again
					rename = LanguageServers.forDocument(document).withCapability(ServerCapabilities::getRenameProvider)
							.computeFirst(ls -> ls.getTextDocumentService().rename(params)).get(1000, TimeUnit.MILLISECONDS).orElse(null);
				}
				if (!status.hasError() && (rename == null
						|| (rename.getChanges().isEmpty() && rename.getDocumentChanges().isEmpty()))) {
					status.addWarning(Messages.rename_empty_message);
				}
			}
		} catch (Exception e) {
			status.addFatalError(getErrorMessage(e));
		}
		return status;
	}

	private String getErrorMessage(Throwable e) {
		if (e.getCause() instanceof ResponseErrorException responseErrorException) {
			ResponseError responseError = responseErrorException.getResponseError();
			return responseError.getMessage()
					+ ((responseError.getData() instanceof String data) ? (": " + data) : ""); //$NON-NLS-1$ //$NON-NLS-2$
		} else {
			return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
		}
	}

	@Override
	public Change createChange(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		if (rename == null) {
			throw new CoreException(
					new Status(IStatus.ERROR, LanguageServerPlugin.PLUGIN_ID, Messages.rename_processor_required));
		}
		return LSPEclipseUtils.toCompositeChange(rename, Messages.rename_title);
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
