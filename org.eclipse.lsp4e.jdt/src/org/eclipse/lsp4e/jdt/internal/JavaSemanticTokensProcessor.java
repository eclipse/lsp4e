/*******************************************************************************
 * Copyright (c) 2024 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.lsp4e.jdt.internal;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.ui.text.java.ISemanticTokensProvider;
import org.eclipse.lsp4e.operations.semanticTokens.SemanticTokensDataStreamProcessor;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SemanticTokensLegend;

class JavaSemanticTokensProcessor {
	
	private final Function<Position, Integer> offsetMapper;
	private final Function<String, ISemanticTokensProvider.TokenType> tokenTypeMapper;

	/**
	 * Creates a new instance of {@link SemanticTokensDataStreamProcessor}.
	 *
	 * @param tokenTypeMapper
	 * @param offsetMapper
	 */
	@SuppressWarnings("restriction")
	public JavaSemanticTokensProcessor(@NonNull final Function<String, ISemanticTokensProvider.TokenType> tokenTypeMapper,
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
	public @NonNull List<ISemanticTokensProvider.SemanticToken> getSemanticTokens(@NonNull final List<Integer> dataStream,
			@NonNull final SemanticTokensLegend semanticTokensLegend) {
		final var tokens = new ArrayList<ISemanticTokensProvider.SemanticToken>(dataStream.size() / 5);

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
				tokens.add(new ISemanticTokensProvider.SemanticToken(offset, length, tokenTypeMapper.apply(tokenType)));
				break;
			}
			idx++;
		}
		return tokens;
	}

	private String tokenType(final Integer data, final List<String> legend) {
		try {
			return legend.get(data);
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

}
