package org.eclipse.lsp4e;

import java.util.function.BiFunction;

import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.internal.DocumentUtil;
import org.eclipse.lsp4e.internal.Pair;

public class VersionedBuilder<T, VT> {
	private final IDocument document;
	private final long modificationStamp;
	private final BiFunction<Pair<IDocument, Long>, T, VT> builder;

	public VersionedBuilder(IDocument document, BiFunction<Pair<IDocument, Long>, T, VT> builder) {
		this.document = document;
		this.modificationStamp = DocumentUtil.getDocumentModificationStamp(document);
		this.builder = builder;
	}

	public VT build(T request) {
		return builder.apply(Pair.of(document, modificationStamp), request);
	}
}
