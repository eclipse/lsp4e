/*******************************************************************************
 * Copyright (c) 2019, 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Andrew Obuchowicz (Red Hat Inc.)
 *******************************************************************************/
package org.eclipse.lsp4e.operations.codeactions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.quickassist.IQuickAssistInvocationContext;
import org.eclipse.jface.text.quickassist.IQuickAssistProcessor;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.LanguageServers.LanguageServerDocumentExecutor;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.ui.internal.progress.ProgressInfoItem;

public class LSPCodeActionQuickAssistProcessor implements IQuickAssistProcessor {

	// Data necessary for caching proposals
	private Object lock = new Object();
	private IQuickAssistInvocationContext cachedContext;
	private List<ICompletionProposal> proposals = Collections.synchronizedList(new ArrayList<>());

	private static final ICompletionProposal COMPUTING = new ICompletionProposal() {

		@Override
		public void apply(IDocument document) {
			// Placeholder proposal so nothing to do here
		}

		@Override
		public Point getSelection(IDocument document) {
			return null;
		}

		@Override
		public String getAdditionalProposalInfo() {
			return null;
		}

		@Override
		public String getDisplayString() {
			return Messages.computing;
		}

		@Override
		public Image getImage() {
			return JFaceResources.getImage(ProgressInfoItem.class.getPackage().getName() + ".PROGRESS_DEFAULT"); //$NON-NLS-1$
		}

		@Override
		public IContextInformation getContextInformation() {
			return null;
		}

	};

	CompletionProposal[] NO_PROPOSALS = {};

	@Override
	public String getErrorMessage() {
		return "CodeActions not implemented on this Language Server"; //$NON-NLS-1$
	}

	@Override
	public boolean canFix(Annotation annotation) {
		return true;
	}

	@Override
	public boolean canAssist(IQuickAssistInvocationContext invocationContext) {
		IDocument document = invocationContext.getSourceViewer().getDocument();
		if (document == null) {
			return false;
		}
		LanguageServerDocumentExecutor executor = LanguageServers.forDocument(document).withFilter(LSPCodeActionMarkerResolution::providesCodeActions);
		if (!executor.anyMatching()) {
			return false;
		}

		CodeActionParams params = prepareCodeActionParams(document, invocationContext.getOffset(), invocationContext.getLength());

		try {
			CompletableFuture<List<Either<Command, CodeAction>>> anyActions = executor.collectAll(ls -> ls.getTextDocumentService().codeAction(params)).thenApply(s -> s.stream().flatMap(List::stream).collect(Collectors.toList()));
			if (anyActions.get(200, TimeUnit.MILLISECONDS).stream().filter(LSPCodeActionMarkerResolution::canPerform).collect(Collectors.toList()).isEmpty()) {
				return false;
			}
		} catch (InterruptedException | ExecutionException | TimeoutException e) {
			LanguageServerPlugin.logError(e);
			return false;
		}

		return true;
	}

	@Override
	public ICompletionProposal[] computeQuickAssistProposals(IQuickAssistInvocationContext invocationContext) {
		IDocument document = invocationContext.getSourceViewer().getDocument();

		if (document == null) {
			return NO_PROPOSALS;
		}
		LanguageServerDocumentExecutor executor = LanguageServers.forDocument(document).withFilter(LSPCodeActionMarkerResolution::providesCodeActions);
		if (!executor.anyMatching()) {
			return NO_PROPOSALS;
		}

		// If context has changed, i.e. neq quick assist invocation rather than old
		// proposals computed and calling this method artificially to show proposals in
		// the UI
		boolean proposalsRefreshInProgress = false;
		synchronized (lock) {
			if (cachedContext != invocationContext) {
				cachedContext = invocationContext;
				proposals.clear();
			} else {
				proposalsRefreshInProgress = true;
				proposals.remove(COMPUTING);
			}
		}

		// Get the codeActions
		CodeActionParams params = prepareCodeActionParams(document, invocationContext.getOffset(), invocationContext.getLength());
		List<CompletableFuture<Void>> futures = Collections.emptyList();
		try {
			// Prevent infinite re-entrance by only computing proposals if there aren't any
			if (!proposalsRefreshInProgress) {
				proposals.clear();
				// Start all the servers computing actions - each server will append any code actions to the ongoing list of proposals
				// as a side effect of this request
				futures = executor.computeAll((w, ls) -> ls.getTextDocumentService()
						.codeAction(params)
						.thenAccept(actions -> LanguageServers.streamSafely(actions)
								.filter(LSPCodeActionMarkerResolution::canPerform)
								.forEach(action -> processNewProposal(invocationContext, new CodeActionCompletionProposal(action, w)))));

				CompletableFuture<?> aggregateFutures = CompletableFuture
						.allOf(futures.toArray(new CompletableFuture[futures.size()]));

				// If the result completes quickly without blocking the UI, then return result directly
				aggregateFutures.get(200, TimeUnit.MILLISECONDS);
			}
		} catch (InterruptedException | ExecutionException e) {
			LanguageServerPlugin.logError(e);
		} catch (TimeoutException e) {
			// Server calls didn't complete in time;  those that did will have added their results to <code>this.proposals</code> and can be returned
			// as an intermediate result; as we're returning control to the UI, we need any stragglers to trigger a refresh when they arrive later on
			for (CompletableFuture<Void> future : futures) {
				// Refresh will effectively re-enter this method with the same invocationContext and already computed proposals simply to show the proposals in the UI
				future.whenComplete((r, t) -> this.refreshProposals(invocationContext));
			}
			proposals.add(COMPUTING);
		}
		return proposals.toArray(new ICompletionProposal[proposals.size()]);
	}

	private void processNewProposal(IQuickAssistInvocationContext invocationContext, ICompletionProposal p) {
		// non-ui thread. Context might have changed (quick assist at a different spot) by the time time proposals are computed
		synchronized(lock) {
			if (cachedContext == invocationContext) {
				proposals.add(p);
			}
		}
	}

	/**
	 * Reinvokes the quickAssist in order to refresh the list of completion
	 * proposals
	 *
	 * @param invocationContext
	 *            The invocation context
	 */
	private void refreshProposals(IQuickAssistInvocationContext invocationContext) {
		invocationContext.getSourceViewer().getTextWidget().getDisplay().asyncExec(() -> invocationContext
				.getSourceViewer().getTextOperationTarget().doOperation(ISourceViewer.QUICK_ASSIST));
	}

	private static CodeActionParams prepareCodeActionParams(final IDocument doc, int offset, int length) {
		final var context = new CodeActionContext(Collections.emptyList());
		final var params = new CodeActionParams();
		params.setTextDocument(LSPEclipseUtils.toTextDocumentIdentifier(doc));
		try {
			params.setRange(new Range(LSPEclipseUtils.toPosition(offset, doc), LSPEclipseUtils
					.toPosition(offset + (length > 0 ? length : 0), doc)));
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
		}
		params.setContext(context);
		return params;
	}

}
