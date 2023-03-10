/*******************************************************************************
 * Copyright (c) 2023 Avaloq Evolution AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Ruben Porras Campo (Avaloq Evolution AG) - initial implementation
 *
 *******************************************************************************/
package org.eclipse.lsp4e;

import java.util.function.BiFunction;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.internal.DocumentUtil;

/**
 * A builder to create instances of Versioned<T>.
 *
 * @param <T> the source type
 * @param <VT> the corresponding Versioned<T>
 */
public class VersionedBuilder<T, VT> {
	private final Versioned<IDocument> document;
	private final BiFunction<Versioned<IDocument>, T, VT> builder;

	public VersionedBuilder(@Nullable IDocument document, @NonNull BiFunction<Versioned<IDocument>, T, VT> builder) {
		this.document = Versioned.toVersioned(DocumentUtil.getDocumentModificationStamp(document), document);
		this.builder = builder;
	}

	public VT build(T request) {
		return builder.apply(document, request);
	}
}
