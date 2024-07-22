/*******************************************************************************
 * Copyright (c) 2023 Avaloq Group AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Rubén Porras Campo (Avaloq Group AG) - Initial Implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.semanticTokens;

import java.util.function.Consumer;
import java.util.function.LongConsumer;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.Versioned;
import org.eclipse.lsp4e.internal.DocumentUtil;
import org.eclipse.lsp4e.internal.Pair;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;

/**
 * Specialization of <code>Versioned</code> for semanticTokens
 */
public class VersionedSemanticTokens extends Versioned<Pair<@Nullable SemanticTokens, @Nullable SemanticTokensLegend>>{

	public VersionedSemanticTokens(long version, Pair<@Nullable SemanticTokens, @Nullable SemanticTokensLegend> data,
			IDocument document) {
		super(document, version, data);
	}

	/**
	 * Apply the semantic tokens from the server, provided the document is unchanged since the request used
	 * to compute the edits
	 *
	 */
	public void apply(Consumer<Pair<@Nullable SemanticTokens, @Nullable SemanticTokensLegend>> first, LongConsumer second) {
		if (sourceDocumentVersion == DocumentUtil.getDocumentModificationStamp(document)) {
			first.accept(data);
			second.accept(sourceDocumentVersion);
		}
	}
}
