/*******************************************************************************
 * Copyright (c) 2021 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Victor Rubezhny (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.linkedediting;

import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentCommand;
import org.eclipse.jface.text.IAutoEditStrategy;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

public class LSPLinkedEditingAutoEditStrategy extends LSPLinkedEditingBase implements IAutoEditStrategy {

	private IDocument fDocument;
	private boolean fIsInstalled = false;

	@Override
	public void customizeDocumentCommand(IDocument document, DocumentCommand command) {
		if (!checkCommand(command)) {
			return;
		}

		if (!isOffsetInRanges(document, command.offset)) {
			try {
				collectLinkedEditingRanges(document, command.offset).get();
			} catch (InterruptedException | ExecutionException e) {
				LanguageServerPlugin.logError(e);
			}
		}

		if (fLinkedEditingRanges == null) {
			return;
		}

		Set<Range> sortedRanges = new TreeSet<>(RANGE_OFFSET_ORDER);
		sortedRanges.addAll(fLinkedEditingRanges.getRanges());

		int changeStart = Integer.MAX_VALUE;
		int changeEnd = Integer.MIN_VALUE;
		Range commandRange = null;
		int delta = 0;
		try {
			for (Range r : sortedRanges) {
				int start = LSPEclipseUtils.toOffset(r.getStart(), document);
				if (changeStart > start) {
					changeStart = start;
				}
				int end = LSPEclipseUtils.toOffset(r.getEnd(), document);
				if (changeEnd < end) {
					changeEnd = end;
				}

				if (start <= command.offset && end >= command.offset) {
					commandRange = r;
					delta = command.offset - start;
				}
			}
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
			return;
		}

		if (commandRange == null) {
			return;
		}

		StringBuilder text = new StringBuilder();
		int caretOffset = -1;
		try {
			int currentOffset = changeStart;
			for (Range r : sortedRanges) {
				int rangeStart = LSPEclipseUtils.toOffset(r.getStart(), document);
				int rangeEnd = LSPEclipseUtils.toOffset(r.getEnd(), document);
				if (currentOffset < rangeStart) {
					text.append(document.get(currentOffset, rangeStart - currentOffset));
				}

				int rangeChangeEnd = rangeStart + delta + command.length;
				String rangeTextBeforeCommand = document.get(rangeStart, delta);
				String rangeTextAfterCommand = rangeEnd > rangeChangeEnd ?
						document.get(rangeChangeEnd, rangeEnd - rangeChangeEnd) : ""; //$NON-NLS-1$

				text.append(rangeTextBeforeCommand).append(command.text);
				if (r == commandRange) {
					caretOffset = text.length();
				}
				text.append(rangeTextAfterCommand);
				currentOffset = rangeEnd > rangeChangeEnd ? rangeEnd : rangeChangeEnd;
			}
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
			return;
		}

		command.offset = changeStart;
		command.length = changeEnd - changeStart;
		command.text = text.toString();
		command.caretOffset = changeStart + caretOffset;
		command.shiftsCaret = false;
	}

	private boolean checkCommand(DocumentCommand command) {
		if (!fIsInstalled) {
			super.install();
			fIsInstalled = true;
		}
		return fEnabled && !command.text.chars().anyMatch(Character::isWhitespace);
	}

	private boolean isOffsetInRanges(IDocument document, int offset) {
		if (fDocument != document) {
			// The document is a different one
			fLinkedEditingRanges = null;
			fDocument = document;
			return false;
		}

		if (fLinkedEditingRanges != null) {
			try {
				for (Range r : fLinkedEditingRanges.getRanges()) {
					if (LSPEclipseUtils.toOffset(r.getStart(), document) <= offset &&
							LSPEclipseUtils.toOffset(r.getEnd(), document) >= offset) {
						return true;
					}
				}
			} catch (BadLocationException e) {
				LanguageServerPlugin.logError(e);
			}
		}
		return false;
	}

    /**
     * A Comparator that orders {@code Region} objects by offset
     */
    private static final Comparator<Range> RANGE_OFFSET_ORDER = new RangeOffsetComparator();
    private static class RangeOffsetComparator
            implements Comparator<Range> {
    	@Override
		public int compare(Range r1, Range r2) {
            Position p1 = r1.getStart();
            Position p2 = r2.getStart();

            if (p1.getLine() == p2.getLine()) {
            	return p1.getCharacter() - p2.getCharacter();
            }

            return p1.getLine() - p2.getLine();
        }
    }
}
