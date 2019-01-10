/*******************************************************************************
 * Copyright (c) 2016, 2019 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Lucas Bullen (Red Hat Inc.) - initial implementation
 *   Michał Niewrzał (Rogue Wave Software Inc.)
 *   Lucas Bullen (Red Hat Inc.) - Refactored for incomplete completion lists
 *                               - [Bug 517428] Requests sent before initialization
 *******************************************************************************/
package org.eclipse.lsp4e.operations.completion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.internal.text.html.BrowserInformationControl;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.AbstractReusableInformationControlCreator;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DefaultInformationControl;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.contentassist.BoldStylerProvider;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
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
import org.eclipse.jface.viewers.StyledString.Styler;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.lsp4e.operations.hover.LSBasedHover;
import org.eclipse.lsp4e.ui.LSPImages;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.TextStyle;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.link.EditorLinkedModeUI;

import com.google.common.collect.ImmutableList;

@SuppressWarnings("restriction")
public class LSIncompleteCompletionProposal
		implements ICompletionProposal, ICompletionProposalExtension3, ICompletionProposalExtension4,
		ICompletionProposalExtension5, ICompletionProposalExtension6, ICompletionProposalExtension7,
		IContextInformation {

	// Those variables should be defined in LSP4J and reused here whenever done there
	// See https://github.com/eclipse/lsp4j/issues/149
	/** The currently selected text or the empty string */
	private static final String TM_SELECTED_TEXT = "TM_SELECTED_TEXT"; //$NON-NLS-1$
	/** The contents of the current line */
	private static final String TM_CURRENT_LINE = "TM_CURRENT_LINE"; //$NON-NLS-1$
	/** The contents of the word under cursor or the empty string */
	private static final String TM_CURRENT_WORD = "TM_CURRENT_WORD"; //$NON-NLS-1$
	/** The zero-index based line number */
	private static final String TM_LINE_INDEX = "TM_LINE_INDEX"; //$NON-NLS-1$
	/** The one-index based line number */
	private static final String TM_LINE_NUMBER = "TM_LINE_NUMBER"; //$NON-NLS-1$
	/** The filename of the current document */
	private static final String TM_FILENAME = "TM_FILENAME"; //$NON-NLS-1$
	/** The filename of the current document without its extensions */
	private static final String TM_FILENAME_BASE = "TM_FILENAME_BASE"; //$NON-NLS-1$
	/** The directory of the current document */
	private static final String TM_DIRECTORY = "TM_DIRECTORY"; //$NON-NLS-1$
	/** The full file path of the current document */
	private static final String TM_FILEPATH = "TM_FILEPATH"; //$NON-NLS-1$

	private static final Styler DEPRECATE = new Styler() {
		@Override
		public void applyStyles(TextStyle textStyle) {
			textStyle.strikeout = true;
			textStyle.foreground = PlatformUI.getWorkbench().getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY);
		};
	};

	protected CompletionItem item;
	private int initialOffset = -1;
	protected int bestOffset = -1;
	protected int currentOffset = -1;
	protected ITextViewer viewer;
	private IRegion selection;
	private LinkedPosition firstPosition;
	private LSPDocumentInfo info;
	private Integer rankCategory;
	private Integer rankScore;
	private String documentFilter;
	private String documentFilterAddition = ""; //$NON-NLS-1$

	public LSIncompleteCompletionProposal(@NonNull CompletionItem item, int offset, LSPDocumentInfo info) {
		this.item = item;
		this.info = info;
		this.initialOffset = offset;
		this.currentOffset = offset;
		this.bestOffset = getPrefixCompletionStart(info.getDocument(), offset);
	}

	/**
	 * See {@link CompletionProposalTools.getFilterFromDocument} for filter
	 * generation logic
	 *
	 * @return The document filter for the given offset
	 */
	public String getDocumentFilter(int offset) throws BadLocationException {
		if (documentFilter != null) {
			if (offset != currentOffset) {
				documentFilterAddition = info.getDocument().get(initialOffset, offset - initialOffset);
				rankScore = null;
				rankCategory = null;
				currentOffset = offset;
			}
			return documentFilter + documentFilterAddition;
		}
		currentOffset = offset;
		return getDocumentFilter();
	}

	/**
	 * See {@link CompletionProposalTools.getFilterFromDocument} for filter
	 * generation logic
	 *
	 * @return The document filter for the last given offset
	 */
	public String getDocumentFilter() throws BadLocationException {
		if (documentFilter != null) {
			return documentFilter + documentFilterAddition;
		}
		documentFilter = CompletionProposalTools.getFilterFromDocument(info.getDocument(), currentOffset,
				getFilterString(), bestOffset);
		documentFilterAddition = ""; //$NON-NLS-1$
		return documentFilter;
	}

	/**
	 * See {@link CompletionProposalTools.getScoreOfFilterMatch} for ranking logic
	 *
	 * @return The rank of the match between the document's filter and this
	 *         completion's filter.
	 */
	public int getRankScore() {
		if (rankScore != null)
			return rankScore;
		try {
			rankScore = CompletionProposalTools.getScoreOfFilterMatch(getDocumentFilter(),
					getFilterString());
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
			rankScore = -1;
		}
		return rankScore;
	}

	/**
	 * See {@link CompletionProposalTools.getCategoryOfFilterMatch} for category
	 * logic
	 *
	 * @return The category of the match between the document's filter and this
	 *         completion's filter.
	 */
	public int getRankCategory() {
		if (rankCategory != null) {
			return rankCategory;
		}
		try {
			rankCategory = CompletionProposalTools.getCategoryOfFilterMatch(getDocumentFilter(),
					getFilterString());
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
			rankCategory = 5;
		}
		return rankCategory;
	}

	public int getBestOffset() {
		return this.bestOffset;
	}

	public void updateOffset(int offset) {
		this.bestOffset = getPrefixCompletionStart(info.getDocument(), offset);
	}

	public CompletionItem getItem() {
		return this.item;
	}

	private boolean isDeprecated() {
		return item.getDeprecated() != null && item.getDeprecated().booleanValue();
	}

	@Override
	public StyledString getStyledDisplayString(IDocument document, int offset, BoldStylerProvider boldStylerProvider) {
		String rawString = getDisplayString();
		StyledString res = isDeprecated()
				? new StyledString(rawString, DEPRECATE)
				: new StyledString(rawString);
		if (offset > this.bestOffset) {
			try {
				String subString = getDocumentFilter(offset).toLowerCase();
				int lastIndex = 0;
				String lowerRawString = rawString.toLowerCase();
				for (Character c : subString.toCharArray()) {
					int index = lowerRawString.indexOf(c, lastIndex);
					if (index < 0) {
						return res;
					} else {
						res.setStyle(index, 1, new Styler() {

							@Override
							public void applyStyles(TextStyle textStyle) {
								if (isDeprecated()) {
									DEPRECATE.applyStyles(textStyle);
								}
								boldStylerProvider.getBoldStyler().applyStyles(textStyle);
							}

						});
						lastIndex = index + 1;
					}
				}
			} catch (BadLocationException e) {
				LanguageServerPlugin.logError(e);
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
		if (item.getDeprecated() == Boolean.TRUE) {
			return new StyledString(getDisplayString(), DEPRECATE);
		}
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
			protected IInformationControl doCreateInformationControl(Shell parent) {
				if (BrowserInformationControl.isAvailable(parent)) {
					return new BrowserInformationControl(parent, JFaceResources.DEFAULT_FONT,
							false) {
						@Override
						public IInformationControlCreator getInformationPresenterControlCreator() {
							return new IInformationControlCreator() {
								@Override
								public IInformationControl createInformationControl(Shell parent) {
									BrowserInformationControl res = new BrowserInformationControl(parent,
											JFaceResources.DEFAULT_FONT, true);
									return res;
								}
							};
						}

					};
				} else {
					return new DefaultInformationControl(parent);
				}
			}
		};
	}

	@Override
	public Object getAdditionalProposalInfo(IProgressMonitor monitor) {
		ServerCapabilities capabilities = info.getCapabilites();
		if (capabilities != null) {
			CompletionOptions options = capabilities.getCompletionProvider();
			if (options != null && Boolean.TRUE.equals(options.getResolveProvider())) {
				try {
					updateCompletionItem(info.getInitializedLanguageClient()
							.thenCompose(ls -> ls.getTextDocumentService().resolveCompletionItem(item))
							.get(500, TimeUnit.MILLISECONDS));
				} catch (InterruptedException | ExecutionException | TimeoutException e) {
					LanguageServerPlugin.logError(e);
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
			String htmlDocString = LSPEclipseUtils.getHtmlDocString(this.item.getDocumentation());
			if (htmlDocString != null) {
				res.append(htmlDocString);
			}
		}

		return LSBasedHover.styleHtml(res.toString());
	}

	private void updateCompletionItem(CompletionItem resolvedItem) {
		if (resolvedItem == null) {
			return;
		}
		if (resolvedItem.getLabel() != null) {
			item.setLabel(resolvedItem.getLabel());
		}
		if (resolvedItem.getKind() != null) {
			item.setKind(resolvedItem.getKind());
		}
		if (resolvedItem.getDetail() != null) {
			item.setDetail(resolvedItem.getDetail());
		}
		if (resolvedItem.getDocumentation() != null) {
			item.setDocumentation(resolvedItem.getDocumentation());
		}
		if (resolvedItem.getInsertText() != null) {
			item.setInsertText(resolvedItem.getInsertText());
		}
		if (resolvedItem.getInsertTextFormat() != null) {
			item.setInsertTextFormat(resolvedItem.getInsertTextFormat());
		}
		if (resolvedItem.getTextEdit() != null) {
			item.setTextEdit(resolvedItem.getTextEdit());
		}
		if (resolvedItem.getAdditionalTextEdits() != null) {
			item.setAdditionalTextEdits(resolvedItem.getAdditionalTextEdits());
		}
	}

	@Override
	public CharSequence getPrefixCompletionText(IDocument document, int completionOffset) {
		return item.getInsertText().substring(completionOffset - this.bestOffset);
	}

	@Override
	public int getPrefixCompletionStart(IDocument document, int completionOffset) {
		if (this.item.getTextEdit() != null) {
			try {
				return LSPEclipseUtils.toOffset(this.item.getTextEdit().getRange().getStart(), document);
			} catch (BadLocationException e) {
				LanguageServerPlugin.logError(e);
			}
		}
		String insertText = getInsertText();
		try {
			String subDoc = document.get(
					Math.max(0, completionOffset - insertText.length()),
					Math.min(insertText.length(), completionOffset));
			for (int i = 0; i < insertText.length() && i < completionOffset; i++) {
				String tentativeCommonString = subDoc.substring(i);
				if (insertText.startsWith(tentativeCommonString)) {
					return completionOffset - tentativeCommonString.length();
				}
			}
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
		}
		return completionOffset;
	}

	@Override
	public void apply(IDocument document) {
		apply(document, Character.MIN_VALUE, 0, this.bestOffset);
	}

	protected void apply(IDocument document, char trigger, int stateMask, int offset) {
		String insertText = null;
		TextEdit textEdit = item.getTextEdit();
		try {
			if (textEdit == null) {
				insertText = getInsertText();
				Position start = LSPEclipseUtils.toPosition(this.bestOffset, document);
				Position end = LSPEclipseUtils.toPosition(offset, document); // need 2 distinct objects
				textEdit = new TextEdit(new Range(start, end), insertText);
			} else if (offset > this.initialOffset) {
				// characters were added after completion was activated
				int shift = offset - this.initialOffset;
				textEdit.getRange().getEnd().setCharacter(textEdit.getRange().getEnd().getCharacter() + shift);
			}
			{ // workaround https://github.com/Microsoft/vscode/issues/17036
				Position start = textEdit.getRange().getStart();
				Position end = textEdit.getRange().getEnd();
				if (start.getLine() > end.getLine() || (start.getLine() == end.getLine() && start.getCharacter() > end.getCharacter())) {
					textEdit.getRange().setEnd(start);
					textEdit.getRange().setStart(end);
				}
			}
			{ // allow completion items to be wrong with a too wide range
				Position documentEnd = LSPEclipseUtils.toPosition(document.getLength(), document);
				Position textEditEnd = textEdit.getRange().getEnd();
				if (documentEnd.getLine() < textEditEnd.getLine()
					|| (documentEnd.getLine() == textEditEnd.getLine() && documentEnd.getCharacter() < textEditEnd.getCharacter())) {
					textEdit.getRange().setEnd(documentEnd);
				}
			}

			if (insertText != null) {
				// try to reuse existing characters after completion location
				int shift = offset - this.bestOffset;
				int commonSize = 0;
				while (commonSize < insertText.length() - shift
					&& document.getLength() > offset + commonSize
					&& document.getChar(this.bestOffset + shift + commonSize) == insertText.charAt(commonSize + shift)) {
					commonSize++;
				}
				textEdit.getRange().getEnd().setCharacter(textEdit.getRange().getEnd().getCharacter() + commonSize);
			}
			insertText = textEdit.getNewText();
			LinkedHashMap<String, List<LinkedPosition>> regions = new LinkedHashMap<>();
			int insertionOffset = LSPEclipseUtils.toOffset(textEdit.getRange().getStart(), document);
			insertionOffset = computeNewOffset(item.getAdditionalTextEdits(), insertionOffset, document);
			if (item.getInsertTextFormat() == InsertTextFormat.Snippet) {
				int currentOffset = 0;
				while ((currentOffset = insertText.indexOf('$', currentOffset)) != -1) {
					StringBuilder keyBuilder = new StringBuilder();
					String defaultValue = ""; //$NON-NLS-1$
					int length = 1;
					while (currentOffset + length < insertText.length() && Character.isDigit(insertText.charAt(currentOffset + length))) {
						keyBuilder.append(insertText.charAt(currentOffset + length));
						length++;
					}
					if (length == 1 && insertText.length() >= 2 && insertText.charAt(currentOffset + 1) == '{') {
						length++;
						while (currentOffset + length < insertText.length() && Character.isDigit(insertText.charAt(currentOffset + length))) {
							keyBuilder.append(insertText.charAt(currentOffset + length));
							length++;
						}
						if (currentOffset + length < insertText.length() && insertText.charAt(currentOffset + length) == ':') {
							length++;
						}
						while (currentOffset + length < insertText.length() && insertText.charAt(currentOffset + length) != '}') {
							defaultValue += insertText.charAt(currentOffset + length);
							length++;
						}
						if (defaultValue.startsWith("$")) { //$NON-NLS-1$
							String varValue = getVariableValue(defaultValue.substring(1));
							if (varValue != null) {
								defaultValue = varValue;
							}
						}
						if (currentOffset + length < insertText.length() && insertText.charAt(currentOffset + length) == '}') {
							length++;
						}
					}
					if (keyBuilder.length() > 0) {
						String key = keyBuilder.toString();
						if (!regions.containsKey(key)) {
							regions.put(key, new ArrayList<>());
						}
						insertText = insertText.substring(0, currentOffset) + defaultValue + insertText.substring(currentOffset + length);
						LinkedPosition position = new LinkedPosition(document, insertionOffset + currentOffset, defaultValue.length());
						if (firstPosition == null) {
							firstPosition = position;
						}
						regions.get(key).add(position);
						currentOffset += defaultValue.length();
					} else {
						currentOffset++;
					}
				}
			}
			textEdit.setNewText(insertText); // insertText now has placeholder removed
			List<TextEdit> additionalEdits = item.getAdditionalTextEdits();
			if (additionalEdits != null && !additionalEdits.isEmpty()) {
				ImmutableList.Builder<TextEdit> allEdits = ImmutableList.builder();
				allEdits.add(textEdit);
				allEdits.addAll(additionalEdits);
				LSPEclipseUtils.applyEdits(document, allEdits.build());
			} else {
				LSPEclipseUtils.applyEdit(textEdit, document);
			}

			if (viewer != null && !regions.isEmpty()) {
				LinkedModeModel model = new LinkedModeModel();
				for (List<LinkedPosition> positions: regions.values()) {
					LinkedPositionGroup group = new LinkedPositionGroup();
					for (LinkedPosition position : positions) {
						group.addPosition(position);
					}
					model.addGroup(group);
				}
				model.forceInstall();

				LinkedModeUI ui = new EditorLinkedModeUI(model, viewer);
				// ui.setSimpleMode(true);
				// ui.setExitPolicy(new ExitPolicy(closingCharacter, document));
				// ui.setExitPosition(getTextViewer(), exit, 0, Integer.MAX_VALUE);
				ui.setCyclingMode(LinkedModeUI.CYCLE_NEVER);
				ui.enter();
			} else {
				selection = new Region(insertionOffset + textEdit.getNewText().length(), 0);
			}
		} catch (BadLocationException ex) {
			LanguageServerPlugin.logError(ex);
		}
	}

	private int computeNewOffset(List<TextEdit> additionalTextEdits, int insertionOffset, IDocument doc) {
		if (additionalTextEdits != null && !additionalTextEdits.isEmpty()) {
			int adjustment = 0;
			for (TextEdit edit : additionalTextEdits) {
				try {
					Range rng = edit.getRange();
					int start = LSPEclipseUtils.toOffset(rng.getStart(), doc);
					if (start <= insertionOffset) {
						int end = LSPEclipseUtils.toOffset(rng.getEnd(), doc);
						int orgLen = end - start;
						int newLeng = edit.getNewText().length();
						int editChange = newLeng - orgLen;
						adjustment += editChange;
					}
				} catch (BadLocationException e) {
					LanguageServerPlugin.logError(e);
				}
			}
			return insertionOffset + adjustment;
		}
		return insertionOffset;
	}

	private String getVariableValue(String variableName) {
		switch (variableName) {
		case TM_FILENAME_BASE:
			IPath pathBase = Path.fromPortableString(info.getFileUri().getPath()).removeFileExtension();
			String fileName = pathBase.lastSegment();
			return fileName != null ? fileName : ""; //$NON-NLS-1$
		case TM_FILENAME:
			return Path.fromPortableString(info.getFileUri().getPath()).lastSegment();
		case TM_FILEPATH:
			IResource resource = LSPEclipseUtils.findResourceFor(info.getFileUri().toString());
			if (resource != null)
				return resource.getLocation().toString();
			return ""; //$NON-NLS-1$
		case TM_DIRECTORY:
			IResource dirResource = LSPEclipseUtils.findResourceFor(info.getFileUri().toString());
			if (dirResource != null && dirResource.getParent() != null)
				return dirResource.getParent().getLocation().toString();
			return ""; //$NON-NLS-1$
		case TM_LINE_INDEX:
			int lineIndex = item.getTextEdit().getRange().getStart().getLine();
			return Integer.toString(lineIndex);
		case TM_LINE_NUMBER:
			int lineNumber = item.getTextEdit().getRange().getStart().getLine();
			return Integer.toString(lineNumber + 1);
		case TM_CURRENT_LINE:
			int currentLineIndex = item.getTextEdit().getRange().getStart().getLine();
			try {
				IRegion lineInformation = info.getDocument().getLineInformation(currentLineIndex);
				String line = info.getDocument().get(lineInformation.getOffset(), lineInformation.getLength());
				return line;
			} catch (BadLocationException e) {
				LanguageServerPlugin.logWarning(e.getMessage(), e);
				return ""; //$NON-NLS-1$
			}
		case TM_SELECTED_TEXT:
			Range selectedRange = item.getTextEdit().getRange();
			try {
				int startOffset = LSPEclipseUtils.toOffset(selectedRange.getStart(), info.getDocument());
				int endOffset = LSPEclipseUtils.toOffset(selectedRange.getEnd(), info.getDocument());
				String selectedText = info.getDocument().get(startOffset, endOffset - startOffset);
				return selectedText;
			} catch (BadLocationException e) {
				LanguageServerPlugin.logWarning(e.getMessage(), e);
				return ""; //$NON-NLS-1$
			}
		case TM_CURRENT_WORD:
			return ""; //$NON-NLS-1$
		default:
			return null;
		}
	}

	protected String getInsertText() {
		String insertText = this.item.getInsertText();
		if (this.item.getTextEdit() != null) {
			insertText = this.item.getTextEdit().getNewText();
		}
		if (insertText == null) {
			insertText = this.item.getLabel();
		}
		return insertText;
	}

	@Override
	public Point getSelection(IDocument document) {
		if (this.firstPosition != null) {
			return new Point(this.firstPosition.getOffset(), this.firstPosition.getLength());
		}
		if (selection == null) {
			return null;
		}
		return new Point(selection.getOffset(), selection.getLength());
	}

	@Override
	public String getAdditionalProposalInfo() {
		return this.item.getDetail();
	}

	@Override
	public Image getImage() {
		return LSPImages.imageFromCompletionItem(this.item);
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

	public String getSortText() {
		if (item.getSortText() != null && !item.getSortText().isEmpty()) {
			return item.getSortText();
		}
		return item.getLabel();
	}

	public String getFilterString() {
		if (item.getFilterText() != null && !item.getFilterText().isEmpty()) {
			return item.getFilterText();
		}
		return item.getLabel();
	}
}
