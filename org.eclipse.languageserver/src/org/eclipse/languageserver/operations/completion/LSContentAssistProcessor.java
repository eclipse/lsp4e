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
package org.eclipse.languageserver.operations.completion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;
import org.eclipse.languageserver.LSPEclipseUtils;
import org.eclipse.languageserver.LanguageServiceAccessor;
import org.eclipse.languageserver.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentPositionParams;

public class LSContentAssistProcessor implements IContentAssistProcessor {

	private LSPDocumentInfo info;
	private LSPDocumentInfo lastCheckedForAutoActiveCharactersInfo;
	private char[] triggerChars;

	public LSContentAssistProcessor() {
	}

	@Override
	public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int offset) {
		ICompletionProposal[] res = new ICompletionProposal[0];
		info = LanguageServiceAccessor.getLSPDocumentInfoFor(viewer, capabilities -> capabilities.getCompletionProvider() != null);
		CompletableFuture<CompletionList> request = null;
		try {
			if (info != null) {
				TextDocumentPositionParams param = LSPEclipseUtils.toTextDocumentPosistionParams(info.getFileUri(), offset, info.getDocument());
				request = info.getLanguageClient().getTextDocumentService().completion(param);
				CompletionList completionList = request.get(5, TimeUnit.SECONDS);
				res = toProposals(offset, completionList);
			}
		} catch (Exception ex) {
			ex.printStackTrace(); //TODO
			if (request != null) {
				res = toProposals(offset, request.getNow(null));
			}
		}
		return res;
	}

	private ICompletionProposal[] toProposals(int offset, CompletionList completionList) {
		if (completionList == null) {
			return new ICompletionProposal[0];
		}

		Collections.sort(completionList.getItems(), new Comparator<CompletionItem>() {
			@Override
			public int compare(CompletionItem o1, CompletionItem o2) {
				String c1 = getComparableLabel(o1);
				String c2 = getComparableLabel(o2);
				if (c1 == null) {
					return -1;
				}
				return c1.compareToIgnoreCase(c2);
			}
		});
		List<ICompletionProposal> proposals = new ArrayList<>();
		for (@NonNull CompletionItem item : completionList.getItems()) {
			if (item.getLabel() != null && !item.getLabel().isEmpty()) {
				proposals.add(new LSCompletionProposal(item, offset, info));
			}
		}
		return proposals.toArray(new ICompletionProposal[proposals.size()]);
	}

	private String getComparableLabel(CompletionItem item) {
		if (item.getSortText() != null && !item.getSortText().isEmpty()) {
			return item.getSortText();
		}
		return item.getLabel();
	}

	@Override
	public IContextInformation[] computeContextInformation(ITextViewer viewer, int offset) {
		// TODO
		return new IContextInformation[0];
	}

	@Override
	public char[] getCompletionProposalAutoActivationCharacters() {
		if (info != this.lastCheckedForAutoActiveCharactersInfo) {
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

	@Override
	public char[] getContextInformationAutoActivationCharacters() {
		return null;
	}

	@Override
	public String getErrorMessage() {
		return "Error"; //$NON-NLS-1$
	}

	@Override
	public IContextInformationValidator getContextInformationValidator() {
		// TODO Auto-generated method stub
		return null;
	}

}
