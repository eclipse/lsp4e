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
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.ui.internal.progress.ProgressInfoItem;

public class LSPCodeActionQuickAssistProcessor implements IQuickAssistProcessor {

	private List<LSPDocumentInfo> infos;

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
		if (this.infos == null || this.infos.isEmpty() || this.infos.get(0).getDocument() != document) {
			infos = getLSPDocumentInfos(document);
			if (infos.isEmpty()) {
				return false;
			}
		}

		CodeActionParams params = prepareCodeActionParams(infos, invocationContext.getOffset(), invocationContext.getLength());
		List<Either<Command, CodeAction>> possibleProposals = Collections.synchronizedList(new ArrayList<>());
		List<CompletableFuture<Void>> futures = infos.stream()
				.map(info -> info.getInitializedLanguageClient()
						.thenComposeAsync(ls -> ls.getTextDocumentService().codeAction(params).thenAcceptAsync(
								actions -> actions.stream().filter(Objects::nonNull).forEach(possibleProposals::add))))
				.toList();

		CompletableFuture<?> aggregateFutures = CompletableFuture
				.allOf(futures.toArray(new CompletableFuture[futures.size()]));
		try {
			aggregateFutures.get(200, TimeUnit.MILLISECONDS);
			if (possibleProposals.isEmpty()) {
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
		if (this.infos == null || this.infos.isEmpty() || this.infos.get(0).getDocument() != document) {
			infos = getLSPDocumentInfos(document);
			if (infos.isEmpty()) {
				return NO_PROPOSALS;
			}
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
		CodeActionParams params = prepareCodeActionParams(infos, invocationContext.getOffset(), invocationContext.getLength());
		List<CompletableFuture<Void>> futures = Collections.emptyList();
		try {
			// Prevent infinite re-entrance by only computing proposals if there aren't any
			if (!proposalsRefreshInProgress) {
				proposals.clear();
				futures = infos.stream()
						.map(info -> info.getInitializedLanguageClient()
								.thenComposeAsync(ls -> ls.getTextDocumentService().codeAction(params)
										.thenAcceptAsync(actions -> actions.stream().filter(Objects::nonNull)
												.map(action -> new CodeActionCompletionProposal(action, info))
												.forEach(p -> processNewProposal(invocationContext, p)))))
								.toList();

				CompletableFuture<?> aggregateFutures = CompletableFuture
						.allOf(futures.toArray(new CompletableFuture[futures.size()]));
				aggregateFutures.get(200, TimeUnit.MILLISECONDS);
			}
		} catch (InterruptedException | ExecutionException e) {
			LanguageServerPlugin.logError(e);
		} catch (TimeoutException e) {
			for (CompletableFuture<Void> future : futures) {
				// Refresh will effectively re-enter this method with the same invocationContext and already computed proposals simply to show the proposals in the UI
				// Should be aware of this!
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

	private static List<LSPDocumentInfo> getLSPDocumentInfos(IDocument document) {
		if (document == null) {
			return Collections.emptyList();
		}
		return LanguageServiceAccessor.getLSPDocumentInfosFor(document,
				LSPCodeActionMarkerResolution::providesCodeActions);
	}

	private static CodeActionParams prepareCodeActionParams(List<LSPDocumentInfo> infos, int offset, int length) {
		CodeActionContext context = new CodeActionContext(Collections.emptyList());
		CodeActionParams params = new CodeActionParams();
		params.setTextDocument(new TextDocumentIdentifier(infos.get(0).getFileUri().toString()));
		try {
			params.setRange(new Range(LSPEclipseUtils.toPosition(offset, infos.get(0).getDocument()), LSPEclipseUtils
					.toPosition(offset + (length > 0 ? length : 0), infos.get(0).getDocument())));
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
		}
		params.setContext(context);
		return params;
	}

}
