/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Mickael Istria (Red Hat Inc.) - initial implementation
 *   Michał Niewrzał (Rogue Wave Software Inc.)
 *******************************************************************************/
package org.eclipse.languageserver.operations.completion;

import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.text.AbstractReusableInformationControlCreator;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DefaultInformationControl;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.BoldStylerProvider;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension2;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension3;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension4;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension5;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension6;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension7;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.link.LinkedModeModel;
import org.eclipse.jface.text.link.LinkedModeUI;
import org.eclipse.jface.text.link.LinkedPosition;
import org.eclipse.jface.text.link.LinkedPositionGroup;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.languageserver.LSPEclipseUtils;
import org.eclipse.languageserver.LSPImages;
import org.eclipse.languageserver.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.texteditor.link.EditorLinkedModeUI;

public class LSCompletionProposal implements ICompletionProposal, ICompletionProposalExtension,
		ICompletionProposalExtension2, ICompletionProposalExtension3, ICompletionProposalExtension4,
		ICompletionProposalExtension5, ICompletionProposalExtension6, ICompletionProposalExtension7, IContextInformation {

	private static final String EDIT_AREA_OPEN_PATTERN = "{{"; //$NON-NLS-1$
	private static final String EDIT_AREA_CLOSE_PATTERN = "}}"; //$NON-NLS-1$
	private CompletionItem item;
	private int initialOffset;
	private int selectionOffset;
	private ITextViewer viewer;
	private LinkedPosition firstPosition;
	private LSPDocumentInfo info;

	public LSCompletionProposal(CompletionItem item, int offset, LSPDocumentInfo info) {
		this.item = item;
		this.initialOffset = offset;
		this.info = info;
	}

	@Override
	public StyledString getStyledDisplayString(IDocument document, int offset, BoldStylerProvider boldStylerProvider) {
		String rawString = getDisplayString();
		StyledString res = new StyledString(rawString);
		if (offset != this.initialOffset) {
			try {
				String subString = document.get(this.initialOffset, offset - this.initialOffset);
				int lastIndex = 0;
				for (Character c : subString.toCharArray()) {
					int index = rawString.indexOf(c, lastIndex);
					if (index < 0) {
						return res;
					} else {
						res.setStyle(index, 1, boldStylerProvider.getBoldStyler());
						lastIndex = index + 1;
					}
				}
			} catch (BadLocationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}	
		}
		return res; 
	}

	@Override
	public String getDisplayString() {
		return this.item.getLabel();
	}
	
	@Override
	public StyledString getStyledDisplayString() {
		return new StyledString(getDisplayString()); 
	}

	@Override
	public boolean isAutoInsertable() {
		// TODO consider what's best
		return false;
	}

	@Override
	public IInformationControlCreator getInformationControlCreator() {
		 return new AbstractReusableInformationControlCreator() {
				@Override
				public IInformationControl doCreateInformationControl(Shell shell) {
					return new DefaultInformationControl(shell, true);
				}
			};
	}
	
	@Override
	public Object getAdditionalProposalInfo(IProgressMonitor monitor) {
		ServerCapabilities capabilities = info.getCapabilites();
		if (capabilities != null) {
			CompletionOptions options = capabilities.getCompletionProvider();
			if (options != null && options.getResolveProvider()) {
				// TODO why a copy?
				CompletionItem i = new CompletionItem();
				i.setLabel(item.getLabel());
				i.setKind(item.getKind());
				i.setData(item.getData());
				i.setTextEdit(i.getTextEdit());
				CompletableFuture<CompletionItem> resolvedItem = info.getLanguageClient().getTextDocumentService()
				        .resolveCompletionItem(i);
				try {
					this.item = resolvedItem.get(500, TimeUnit.MILLISECONDS);
				} catch (InterruptedException | ExecutionException | TimeoutException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

		StringBuilder res = new StringBuilder();
		if (this.item.getDetail() != null) {
			res.append("<p>" + this.item.getDetail() + "</p>"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		if (res.length() > 0) {
			res.append("<br/>"); //$NON-NLS-1$
		}
		if (this.item.getDocumentation() != null) {
			res.append("<p>" + this.item.getDocumentation() + "</p>"); //$NON-NLS-1$ //$NON-NLS-2$
		}

		return res.toString();
	}

	@Override
	public boolean isValidFor(IDocument document, int offset) {
		return validate(document, offset, null);
	}
	
	@Override
	public CharSequence getPrefixCompletionText(IDocument document, int completionOffset) {
		return item.getInsertText().substring(completionOffset -this.initialOffset);
	}

	@Override
	public int getPrefixCompletionStart(IDocument document, int completionOffset) {
		return completionOffset;
	}

	@Override
	public void selected(ITextViewer viewer, boolean smartToggle) {
		this.viewer = viewer;
	}

	@Override
	public void unselected(ITextViewer viewer) {
	}

	@Override
	public boolean validate(IDocument document, int offset, DocumentEvent event) {
		if (offset != this.initialOffset) {
			try {
				String subString = document.get(this.initialOffset, offset - this.initialOffset);
				String insert = getInsertText();
				int lastIndex = 0;
				for (Character c : subString.toCharArray()) {
					int index = insert.indexOf(c, lastIndex);
					if (index < 0) {
						return false;
					} else {
						lastIndex = index + 1;
					}
				}
			} catch (BadLocationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return true;
	}
	
	@Override
	public void apply(IDocument document) {
		String insertText = null;
		int insertionOffset = this.initialOffset;
		if (item.getTextEdit() != null) {
			try {
				insertText = item.getTextEdit().getNewText();
				insertionOffset = LSPEclipseUtils.toOffset(item.getTextEdit().getRange().getStart(), document);
				LSPEclipseUtils.applyEdit(item.getTextEdit(), document);
			} catch (BadLocationException e) {
				e.printStackTrace();
			}
		} else { //compute a best edit by reusing prefixes and suffixes
			insertText = getInsertText();
			this.selectionOffset = insertText.length();
			
			// Look for letters that are available before completion offset
			try {
				int backOffset = 0;
				int size = Math.min(this.initialOffset, insertText.length());
				while (backOffset == 0 && size != 0) {
					if (document.get(this.initialOffset - size, size).equals(insertText.substring(0, size))) {
						backOffset = size;
					}
					size--;
				}
				if (backOffset != 0) {
					insertText = insertText.substring(backOffset);
					this.selectionOffset -= backOffset;
				}
			} catch (BadLocationException ex) {
				ex.printStackTrace();
			}
			
			// Looks for letters that were added after completion was triggered
			int aheadOffset = 0;
			try {
				while (aheadOffset < document.getLength() && aheadOffset < insertText.length() && document.getChar(this.initialOffset + aheadOffset) == insertText.charAt(aheadOffset)) {
					aheadOffset++;
				}
				insertText = insertText.substring(aheadOffset);
			} catch (BadLocationException x) {
				x.printStackTrace();
			}
			
			try {
				document.replace(this.initialOffset + aheadOffset, 0, insertText);
			} catch (BadLocationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return;
			}
		}

		if (viewer != null) {
			LinkedHashMap<String, LinkedPositionGroup> groups = new LinkedHashMap<>();
			int currentOffset = insertText.indexOf(EDIT_AREA_OPEN_PATTERN);
			try {
				while (currentOffset != -1) {
					int closeOffset = insertText.indexOf(EDIT_AREA_CLOSE_PATTERN, currentOffset + EDIT_AREA_OPEN_PATTERN.length());
					if (closeOffset != -1) {
						String key = insertText.substring(currentOffset, closeOffset + EDIT_AREA_CLOSE_PATTERN.length());
						if (!groups.containsKey(key)) {
							groups.put(key, new LinkedPositionGroup());
						}
						LinkedPosition position = new LinkedPosition(document, insertionOffset + currentOffset, key.length());
						if (firstPosition == null) {
							firstPosition = position;
						}
						groups.get(key).addPosition(firstPosition);
						currentOffset = closeOffset + EDIT_AREA_CLOSE_PATTERN.length();
					} else {
						// TODO log
						currentOffset += EDIT_AREA_OPEN_PATTERN.length();
					}
					currentOffset = insertText.indexOf(EDIT_AREA_OPEN_PATTERN, currentOffset);
				}
				if (!groups.isEmpty()) {
					LinkedModeModel model= new LinkedModeModel();
					for (LinkedPositionGroup group : groups.values()) {
						model.addGroup(group);
					}
					model.forceInstall();

					LinkedModeUI ui= new EditorLinkedModeUI(model, viewer);
					//ui.setSimpleMode(true);
					//ui.setExitPolicy(new ExitPolicy(closingCharacter, document));
					//ui.setExitPosition(getTextViewer(), exit, 0, Integer.MAX_VALUE);
					ui.setCyclingMode(LinkedModeUI.CYCLE_NEVER);
					ui.enter();
				}
			} catch (BadLocationException ex) {
				// TODO log
				ex.printStackTrace();
			}
		}
	}

	private String getInsertText() {
		String insertText = this.item.getInsertText();
		if (insertText == null) {
			insertText = this.item.getSortText();
		}
		return insertText;
	}


	@Override
	public void apply(ITextViewer viewer, char trigger, int stateMask, int offset) {
		// TODO Auto-generated method stub
		apply(viewer.getDocument());
	}
	
	@Override
	public void apply(IDocument document, char trigger, int offset) {
		// TODO Auto-generated method stub
		apply(document);
	}

	@Override
	public char[] getTriggerCharacters() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getContextInformationPosition() {
		return SWT.RIGHT;
	}

	@Override
	public Point getSelection(IDocument document) {
		if (this.firstPosition != null) {
			return new Point(this.firstPosition.getOffset(), this.firstPosition.getLength());
		}
		return new Point(this.initialOffset + this.selectionOffset, 0);
	}

	@Override
	public String getAdditionalProposalInfo() {
		return this.item.getDetail();
	}

	@Override
	public Image getImage() {
		return LSPImages.imageFromCompletionKind(this.item.getKind());
	}

	@Override
	public IContextInformation getContextInformation() {
		return this;
	}

	@Override
	public String getContextDisplayString() {
		return getAdditionalProposalInfo();
	}

	@Override
	public String getInformationDisplayString() {
		return getAdditionalProposalInfo();
	}

}
