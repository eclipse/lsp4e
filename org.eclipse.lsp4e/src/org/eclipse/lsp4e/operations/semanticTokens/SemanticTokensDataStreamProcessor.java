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
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.lsp4e.internal.StyleUtil;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;

/**
 * The Class SemanticTokensDataStreamProcessor translates a stream of integers
 * as defined by the LSP SemanticTokenRequests into a list of StyleRanges.
 */
public class SemanticTokensDataStreamProcessor {

	private final Function<Position, Integer> offsetMapper;
	private final Function<String, IToken> tokenTypeMapper;

	/**
	 * Creates a new instance of {@link SemanticTokensDataStreamProcessor}.
	 *
	 * @param tokenTypeMapper
	 * @param offsetMapper
	 */
	public SemanticTokensDataStreamProcessor(@NonNull final Function<String, IToken> tokenTypeMapper,
			@NonNull final Function<Position, Integer> offsetMapper) {
		this.tokenTypeMapper = tokenTypeMapper;
		this.offsetMapper = offsetMapper;
	}

	/**
	 * Get the StyleRanges for the given data stream and tokens legend.
	 *
	 * @param dataStream
	 * @param semanticTokensLegend
	 * @return
	 */
	public @NonNull List<StyleRange> getStyleRanges(@NonNull final List<Integer> dataStream,
			@NonNull final SemanticTokensLegend semanticTokensLegend) {
		final var styleRanges = new ArrayList<StyleRange>(dataStream.size() / 5);

		int idx = 0;
		int prevLine = 0;
		int line = 0;
		int offset = 0;
		int length = 0;
		String tokenType = null;
		for (Integer data : dataStream) {
			switch (idx % 5) {
			case 0: // line
				line += data;
				break;
			case 1: // offset
				if (line == prevLine) {
					offset += data;
				} else {
					offset = offsetMapper.apply(new Position(line, data));
				}
				break;
			case 2: // length
				length = data;
				break;
			case 3: // token type
				tokenType = tokenType(data, semanticTokensLegend.getTokenTypes());
				break;
			case 4: // token modifier
				prevLine = line;
				List<String> tokenModifiers = tokenModifiers(data, semanticTokensLegend.getTokenModifiers());
				StyleRange styleRange = getStyleRange(offset, length, textAttribute(tokenType));
				if (tokenModifiers.stream().anyMatch(x -> x.equals(SemanticTokenModifiers.Deprecated))) {
					StyleUtil.DEPRECATE.applyStyles(styleRange);
				}
				styleRanges.add(styleRange);
				break;
			}
			idx++;
		}
		return styleRanges;
	}

	private String tokenType(final Integer data, final List<String> legend) {
		try {
			return legend.get(data - 1);
		} catch (IndexOutOfBoundsException e) {
			return null; // no match
		}
	}

	private List<String> tokenModifiers(final Integer data, final List<String> legend) {
		if (data.intValue() == 0) {
			return Collections.emptyList();
		}
		final var bitSet = BitSet.valueOf(new long[] { data });
		final var tokenModifiers = new ArrayList<String>();
		for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
			try {
				tokenModifiers.add(legend.get(i));
			} catch (IndexOutOfBoundsException e) {
				// no match
			}
		}

		return tokenModifiers;
	}

	private TextAttribute textAttribute(final String tokenType) {
		if (tokenType != null) {
			IToken token = tokenTypeMapper.apply(tokenType);
			if (token != null) {
				Object data = token.getData();
				if (data instanceof final TextAttribute textAttribute) {
					return textAttribute;
				}
			}
		}
		return null;
	}

	/**
	 * Gets a style range for the given inputs.
	 *
	 * @param offset
	 *            the offset of the range to be styled
	 * @param length
	 *            the length of the range to be styled
	 * @param attr
	 *            the attribute describing the style of the range to be styled
	 */
	private StyleRange getStyleRange(final int offset, final int length, final TextAttribute attr) {
		final StyleRange styleRange;
		if (attr != null) {
			final int style = attr.getStyle();
			final int fontStyle = style & (SWT.ITALIC | SWT.BOLD | SWT.NORMAL);
			styleRange = new StyleRange(offset, length, attr.getForeground(), attr.getBackground(), fontStyle);
			styleRange.strikeout = (style & TextAttribute.STRIKETHROUGH) != 0;
			styleRange.underline = (style & TextAttribute.UNDERLINE) != 0;
			styleRange.font = attr.getFont();
			return styleRange;
		} else {
			styleRange = new StyleRange();
			styleRange.start = offset;
			styleRange.length = length;
		}
		return styleRange;
	}
}