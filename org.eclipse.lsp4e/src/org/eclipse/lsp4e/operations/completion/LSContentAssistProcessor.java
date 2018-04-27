/*******************************************************************************
 * Copyright (c) 2016, 2017 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Lucas Bullen (Red Hat Inc.) - Bug 520700 - TextEditors within FormEditors are not supported *   Lucas Bullen (Red Hat Inc.) - Refactored for incomplete completion lists
 *								- Refactored for incomplete completion lists
 *******************************************************************************/
package org.eclipse.lsp4e.operations.completion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ContextInformation;
import org.eclipse.jface.text.contentassist.ContextInformationValidator;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SignatureInformation;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.xtext.xbase.lib.Pair;

public class LSContentAssistProcessor implements IContentAssistProcessor {

	private static final long TRIGGERS_TIMEOUT = 50;
	private static final long COMPLETION_TIMEOUT = 1000;
	private List<LSPDocumentInfo> infos;
	private char[] triggerChars;
	private char[] contextTriggerChars;
	private Pair<IDocument, Job> findInfoJob;
	private String errorMessage;
	private boolean isIncomplete = false;

	public LSContentAssistProcessor() {
	}

	private Comparator<LSCompletionProposal> proposalConparoator = (o1, o2) -> {
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
	};

	@Override
	public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int offset) {
		checkInfoAndJob(viewer.getDocument());
		if (infos == null) {
			try {
				this.findInfoJob.getValue().join(COMPLETION_TIMEOUT, new NullProgressMonitor());
			} catch (InterruptedException e) {
				LanguageServerPlugin.logError(e);
			}
		}
		if (infos == null || infos.isEmpty()) {
			return new ICompletionProposal[0];
		}

		// Check if any of the infos have the required capabilities
		final List<LSPDocumentInfo> applicableInfos = infos.stream().filter(info -> {
			final ServerCapabilities capabilities = info.getCapabilites();
			return capabilities != null && capabilities.getCompletionProvider() != null;
		}).collect(Collectors.toList());

		if (applicableInfos.isEmpty()) {
			return new ICompletionProposal[0];
		}

		List<ICompletionProposal> proposals = new ArrayList<>();
		try {
			CompletionParams param = LSPEclipseUtils.toCompletionParams(
					applicableInfos.get(0).getFileUri(),
					offset, viewer.getDocument());
			List<ICompletionProposal> lsProposals = Collections.synchronizedList(new ArrayList<>());
			// starts requests to various LS
			applicableInfos.parallelStream().forEach(info -> {
				try {
					Either<List<CompletionItem>, CompletionList> items = info.getInitializedLanguageClient().get()
							.getTextDocumentService().completion(param).get();
					lsProposals.addAll(toProposals(offset, items, info));
				} catch (InterruptedException | ExecutionException ex) {
					LanguageServerPlugin.logError(ex);
					// TODO: consider showing an error message as proposal?
					LSContentAssistProcessor.this.errorMessage = ex.getMessage();
				}
			});
			if (!isIncomplete) {
				List<LSCompletionProposal> CompletionProposal = (List<LSCompletionProposal>) (List<?>) lsProposals;
				CompletionProposal.sort(proposalConparoator);
				proposals.addAll(CompletionProposal);
			} else {
				proposals.addAll(lsProposals);
			}
		} catch (Exception ex) {
			LanguageServerPlugin.logError(ex);
			this.errorMessage = ex.getMessage();
			proposals.add(0, createErrorProposal(offset, ex));
		}
		return proposals.toArray(new ICompletionProposal[proposals.size()]);
	}

	private CompletionProposal createErrorProposal(int offset, Exception ex) {
		return new CompletionProposal("", offset, 0, offset, null, Messages.completionError, null, ex.getMessage()); //$NON-NLS-1$
	}

	private void checkInfoAndJob(@NonNull IDocument refDocument) {
		if (infos != null && !infos.isEmpty() && !refDocument.equals(infos.get(0).getDocument())) {
			if (findInfoJob != null) {
				findInfoJob.getValue().cancel();
				findInfoJob = null;
			}
			infos = null;
		}
		if (infos == null) {
			if (this.findInfoJob == null) {
				createInfoJob(refDocument);
			}
		}
	}

	private List<ICompletionProposal> toProposals(int offset,
			Either<List<CompletionItem>, CompletionList> completionList, LSPDocumentInfo info) {
		if (completionList == null) {
			return Collections.emptyList();
		}
		List<CompletionItem> items = Collections.emptyList();
		if (completionList.isLeft()) {
			items = completionList.getLeft();
		} else if (completionList.isRight()) {
			isIncomplete = completionList.getRight().isIncomplete();
			items = completionList.getRight().getItems();
		}
		List<ICompletionProposal> proposals = new ArrayList<>();
		for (CompletionItem item : items) {
			if (item != null) {
				if (isIncomplete) {
					ICompletionProposal proposal = new LSIncompleteCompletionProposal(item, offset, info);
					proposals.add(proposal);
				} else {
					ICompletionProposal proposal = new LSCompletionProposal(item, offset, info);
					if (((LSCompletionProposal) proposal).validate(info.getDocument(), offset, null)) {
						proposals.add(proposal);
					}
				}
			}
		}
		return proposals;
	}

	@Override
	public IContextInformation[] computeContextInformation(ITextViewer viewer, int offset) {
		checkInfoAndJob(viewer.getDocument());
		if (infos == null) {
			try {
				this.findInfoJob.getValue().join(COMPLETION_TIMEOUT, new NullProgressMonitor());
			} catch (InterruptedException e) {
				LanguageServerPlugin.logError(e);
			}
		}

		if (infos == null || infos.isEmpty()) {
			return new IContextInformation[0];
		}

		// Check if any of the infos have the required capabilities
		final List<LSPDocumentInfo> applicableInfos = infos.stream().filter(info -> {
			final ServerCapabilities capabilities = info.getCapabilites();
			return capabilities != null && capabilities.getSignatureHelpProvider() != null;
		}).collect(Collectors.toList());

		if (applicableInfos.isEmpty()) {
			return new IContextInformation[0];
		}

		List<IContextInformation> contextInformations = Collections.synchronizedList(new ArrayList<>());
		try {
			TextDocumentPositionParams param = LSPEclipseUtils.toTextDocumentPosistionParams(
					applicableInfos.get(0).getFileUri(), offset,
					viewer.getDocument());
			Stream<CompletableFuture<Void>> requests = applicableInfos.stream()
					.map(info -> info.getInitializedLanguageClient().thenCompose(languageServer -> languageServer
					.getTextDocumentService().signatureHelp(param).thenAccept(signatureHelp -> {
				for (SignatureInformation information : signatureHelp.getSignatures()) {
					StringBuilder signature = new StringBuilder(information.getLabel());
					String docString = LSPEclipseUtils.getDocString(information.getDocumentation());
					if (docString!=null && !docString.isEmpty()) {
						signature.append('\n').append(docString);
					}
					contextInformations.add(new ContextInformation(information.getLabel(), signature.toString()));
				}
			})));
			// wait for them to complete
			requests.forEach(future -> {
				try {
					future.get(); // TODO? timeout?
				} catch (ExecutionException | InterruptedException ex) {
					LanguageServerPlugin.logError(ex);
				}
			});
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
		}
		return contextInformations.toArray(new IContextInformation[0]);
	}

	@Override
	public char[] getCompletionProposalAutoActivationCharacters() {
		ITextEditor textEditor = LSPEclipseUtils.getActiveTextEditor();
		if(textEditor != null) {
			checkInfoAndJob(LSPEclipseUtils.getDocument(textEditor));
			try {
				this.findInfoJob.getValue().join(TRIGGERS_TIMEOUT, new NullProgressMonitor());
			} catch (InterruptedException | OperationCanceledException e) {
				LanguageServerPlugin.logError(e);
			}
			return triggerChars;
		}
		return triggerChars;
	}

	private Set<Character> collectCharacters(@Nullable List<String> triggerCharacters) {
		if (triggerCharacters == null || triggerCharacters.isEmpty()) {
			return Collections.emptySet();
		}
		Set<Character> chars = new HashSet<>();
		for (String s : triggerCharacters) {
			if (s.length() == 1) {
				chars.add(s.charAt(0));
			}
		}
		return chars;
	}

	private void createInfoJob(@NonNull final IDocument document) {
		Job job = new Job("[Completion] Find Language Servers") { //$NON-NLS-1$
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				infos = Collections.unmodifiableList(LanguageServiceAccessor.getLSPDocumentInfosFor(document,
						capabilities -> capabilities.getCompletionProvider() != null
								|| capabilities.getSignatureHelpProvider() != null));
				Set<Character> triggerChars = new HashSet<>();
				Set<Character> contextTriggerChars = new HashSet<>();
				if (infos != null) {
					infos.forEach(info -> {
						ServerCapabilities currentCapabilites = info.getCapabilites();
						if (currentCapabilites != null) {
							if (currentCapabilites.getCompletionProvider() != null) {
								triggerChars.addAll(collectCharacters(currentCapabilites.getCompletionProvider().getTriggerCharacters()));
							}
							if (currentCapabilites.getSignatureHelpProvider() != null) {
								contextTriggerChars.addAll(collectCharacters(currentCapabilites.getSignatureHelpProvider().getTriggerCharacters()));
							}
						}
					});
				}
				LSContentAssistProcessor.this.triggerChars = toArray(triggerChars);
				LSContentAssistProcessor.this.contextTriggerChars = toArray(contextTriggerChars);

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
		ITextEditor textEditor = LSPEclipseUtils.getActiveTextEditor();
		if(textEditor != null) {
			checkInfoAndJob(LSPEclipseUtils.getDocument(textEditor));
			try {
				this.findInfoJob.getValue().join(TRIGGERS_TIMEOUT, new NullProgressMonitor());
			} catch (InterruptedException | OperationCanceledException e) {
				LanguageServerPlugin.logError(e);
			}
		}
		return contextTriggerChars;
	}

	@Override
	public String getErrorMessage() {
		return this.errorMessage;
	}

	@Override
	public IContextInformationValidator getContextInformationValidator() {
		return new ContextInformationValidator(this);
	}

	private char[] toArray(Collection<Character> items) {
		char[] res = new char[items.size()];
		int i = 0;
		for (Character item : items) {
			res[i] = item.charValue();
			i++;
		}
		return res;
	}
}
