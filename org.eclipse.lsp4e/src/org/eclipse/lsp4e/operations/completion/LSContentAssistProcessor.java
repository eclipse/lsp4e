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
package org.eclipse.lsp4e.operations.completion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.xtext.xbase.lib.Pair;

public class LSContentAssistProcessor implements IContentAssistProcessor {

	private static final long TRIGGERS_TIMEOUT = 50;
	private static final long COMPLETION_TIMEOUT = 1000;
	private LSPDocumentInfo info;
	private LSPDocumentInfo lastCheckedForAutoActiveCharactersInfo;
	private char[] triggerChars;
	private Pair<IDocument, Job> findInfoJob;
	private String errorMessage;

	public LSContentAssistProcessor() {
	}

	@Override
	public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int offset) {
		checkInfoAndJob(viewer.getDocument());
		if (info == null) {
			try {
				this.findInfoJob.getValue().join(COMPLETION_TIMEOUT, new NullProgressMonitor());
			} catch (InterruptedException e) {
				LanguageServerPlugin.logError(e);
			}
		}
		ICompletionProposal[] res = new ICompletionProposal[0];
		CompletableFuture<Either<List<CompletionItem>, CompletionList>> request = null;
		try {
			if (info != null) {
				TextDocumentPositionParams param = LSPEclipseUtils.toTextDocumentPosistionParams(info.getFileUri(), offset, info.getDocument());
				request = info.getLanguageClient().getTextDocumentService().completion(param);
				res = toProposals(offset, request.get());
			}
		} catch (Exception ex) {
			LanguageServerPlugin.logError(ex);
			if (request != null) {
				// TODO: consider showing an error message as proposal?
				res = toProposals(offset, request.getNow(Either.forLeft(Collections.emptyList())));
				this.errorMessage = ex.getMessage();
			}
		}
		return res;
	}

	private void checkInfoAndJob(@NonNull IDocument refDocument) {
		if (info != null && !refDocument.equals(info.getDocument())) {
			if (findInfoJob != null) {
				findInfoJob.getValue().cancel();
				findInfoJob = null;
			}
			info = null;
		}
		if (info == null) {
			if (this.findInfoJob == null) {
				createInfoJob(refDocument);
			}
		}
	}

	private ICompletionProposal[] toProposals(int offset, Either<List<CompletionItem>, CompletionList> completionList) {
		if (completionList == null) {
			return new ICompletionProposal[0];
		}
		List<CompletionItem> items = Collections.emptyList();
		if (completionList.isLeft()) {
			items = completionList.getLeft();
		} else if (completionList.isRight()) {
			items = completionList.getRight().getItems();
		}
		List<LSCompletionProposal> proposals = new ArrayList<>();
		for (CompletionItem item : items) {
			if (item != null) {
				LSCompletionProposal proposal = new LSCompletionProposal(item, offset, info);
				if (proposal.validate(info.getDocument(), offset, null)) {
					proposals.add(proposal);
				}
			}
		}
		proposals.sort((o1, o2) -> {
			// TODO: evaluate ICompletionProposalSorter

			// prefer the one that matches the most backward
			if (o1.getBestOffset() < o2.getBestOffset()) {
				return -1;
			} else if (o1.getBestOffset() > o2.getBestOffset()) {
				return +1;
			} else if (o1.getNumberOfModifsBeforeOffset() < o2.getNumberOfModifsBeforeOffset()) {
				return -1;
			} else if (o1.getNumberOfModifsBeforeOffset() > o2.getNumberOfModifsBeforeOffset()) {
				return +1;
			} else {
				String c1 = o1.getSortText();
				String c2 = o2.getSortText();
				if (c1 == null) {
					return -1;
				}
				return c1.compareToIgnoreCase(c2);
			}
		});
		return proposals.toArray(new ICompletionProposal[proposals.size()]);
	}

	@Override
	public IContextInformation[] computeContextInformation(ITextViewer viewer, int offset) {
		// TODO
		return new IContextInformation[0];
	}

	@Override
	public char[] getCompletionProposalAutoActivationCharacters() {
		checkInfoAndJob(LSPEclipseUtils.getDocument((ITextEditor) PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor()));
		if (info == null) {
			try {
				this.findInfoJob.getValue().join(TRIGGERS_TIMEOUT, new NullProgressMonitor());
			} catch (InterruptedException | OperationCanceledException e) {
				LanguageServerPlugin.logError(e);
			}
		}
		if (info != null && info != this.lastCheckedForAutoActiveCharactersInfo) {
			ServerCapabilities currentCapabilites = info.getCapabilites();
			if (currentCapabilites == null) {
				return null;
			}
			List<Character> chars = new ArrayList<>();
			List<String> triggerCharacters = currentCapabilites.getCompletionProvider().getTriggerCharacters();
			if (triggerCharacters == null) {
				return null;
			}
			for (String s : triggerCharacters) {
				if (s.length() == 1) {
					chars.add(s.charAt(0));
				}
			}
			triggerChars = new char[chars.size()];
			int i = 0;
			for (Character c : chars) {
				triggerChars[i] = c;
				i++;
			}
			this.lastCheckedForAutoActiveCharactersInfo = info;
		}
		return triggerChars;
	}

	private void createInfoJob(@NonNull final IDocument document) {
		Job job = new Job("[Completion] Find Language Server") { //$NON-NLS-1$
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				info = LanguageServiceAccessor.getLSPDocumentInfoFor(document, capabilities -> capabilities.getCompletionProvider() != null);
				return Status.OK_STATUS;
			}
		};
		job.setPriority(Job.INTERACTIVE);
		job.setSystem(true);
		job.setUser(false);
		job.schedule();
		this.findInfoJob = new Pair<IDocument, Job>(document, job);
	}

	@Override
	public char[] getContextInformationAutoActivationCharacters() {
		return null;
	}

	@Override
	public String getErrorMessage() {
		return this.errorMessage;
	}

	@Override
	public IContextInformationValidator getContextInformationValidator() {
		// TODO Auto-generated method stub
		return null;
	}

}
