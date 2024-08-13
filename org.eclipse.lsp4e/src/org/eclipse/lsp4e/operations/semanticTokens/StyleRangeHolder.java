/*******************************************************************************
 * Copyright (c) 2022 Avaloq Group AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 *******************************************************************************/
package org.eclipse.lsp4e.operations.semanticTokens;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextListener;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextEvent;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.swt.custom.StyleRange;

/**
 * The Class SemanticTokensDataStreamProcessor holds a list of StyleRanges.
 * <p>
 * To avoid flickering, we also implement {@link ITextListener} to adapt (the
 * only adaptation currently supported shifting ranges) recorded semantic
 * highlights When the user writes a single or multi-line comments shifting is
 * not enough. That could be improved if we can access
 * org.eclipse.tm4e.languageconfiguration.ILanguageConfiguration.getComments()
 * (still unclear on how to do that).
 */
public class StyleRangeHolder implements ITextListener {
	private final List<StyleRange> previousRanges;

	public StyleRangeHolder() {
		previousRanges = new ArrayList<>();
	}

	/**
	 * save the styles.
	 *
	 * @param styleRanges
	 */
	public void saveStyles(final List<StyleRange> styleRanges) {
		synchronized (previousRanges) {
			previousRanges.clear();
			previousRanges.addAll(styleRanges);
			previousRanges.sort(Comparator.comparing(s -> s.start));
		}
	}

	/**
	 * return a copy of the saved styles that overlap the given region.
	 *
	 * @param region
	 */
	public StyleRange[] overlappingRanges(final IRegion region) {
		synchronized (previousRanges) {
			// we need to create new styles because the text presentation might change a
			// style when applied to the presentation
			// and we want the ones saved from the reconciling as immutable
			return previousRanges.stream()//
					.filter(r -> TextUtilities.overlaps(region, new Region(r.start, r.length)))//
					.map(this::clone).toArray(StyleRange[]::new);
		}
	}

	private StyleRange clone(final StyleRange styleRange) {
		final var clonedStyleRange = new StyleRange(styleRange.start, styleRange.length, styleRange.foreground,
				styleRange.background, styleRange.fontStyle);
		clonedStyleRange.strikeout = styleRange.strikeout;
		return clonedStyleRange;
	}

	private boolean isContained(final int offset, final IRegion region) {
		return offset >= region.getOffset() && offset < (region.getOffset() + region.getLength());
	}

	@Override
	public void textChanged(final TextEvent event) {
		if (event.getDocumentEvent() != null) { // if null, it is an internal event, not a changed text
			String replacedText = event.getReplacedText();
			String text = event.getText();
			int delta = (text != null ? text.length() : 0) - (replacedText != null ? replacedText.length() : 0);
			synchronized (previousRanges) {
				previousRanges.removeIf(r -> isContained(event.getOffset(), new Region(r.start, r.length)));
				previousRanges.stream().filter(r -> r.start >= event.getOffset()).forEach(r -> r.start += delta);
			}
		}
	}

}
