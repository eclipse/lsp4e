/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.operations.references;

import java.net.URI;
import java.util.Arrays;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.lsp4e.internal.ArrayUtil;
import org.eclipse.search.internal.ui.text.FileMatch;
import org.eclipse.search.internal.ui.text.FileSearchQuery;
import org.eclipse.search.internal.ui.text.FileSearchResult;
import org.eclipse.search.internal.ui.text.FileTreeContentProvider;
import org.eclipse.search.ui.text.AbstractTextSearchResult;

public class FileAndURIMatchContentProvider implements ITreeContentProvider {

	private final FileTreeContentProvider delegate;
	private @Nullable LSSearchResult searchResult;
	private @Nullable FileSearchResult filteredFileSearchResult;

	FileAndURIMatchContentProvider(FileTreeContentProvider delegate) {
		this.delegate = delegate;
	}

	@Override
	public void inputChanged(Viewer viewer, @Nullable Object oldInput, @Nullable Object newInput) {
		if (newInput instanceof FileSearchResult initial && initial.getQuery() instanceof FileSearchQuery query) {
			final var filteredFileSearchResult = this.filteredFileSearchResult = new FileSearchResult(query);
			Arrays.stream(initial.getElements()) //
				.flatMap(element -> Arrays.stream(initial.getMatches(element))) //
				.filter(FileMatch.class::isInstance) //
				.forEach(filteredFileSearchResult::addMatch);
			delegate.inputChanged(viewer, oldInput, filteredFileSearchResult);
		}
		if (newInput instanceof LSSearchResult searchResult) {
			this.searchResult = searchResult;
		}
	}

	@Override
	public Object[] getElements(@Nullable Object inputElement) {
		final var res = ArrayUtil.asArrayList(delegate.getElements(inputElement == this.searchResult ? this.filteredFileSearchResult : inputElement));
		if (inputElement instanceof AbstractTextSearchResult searchResult) {
			Arrays.stream(searchResult.getElements()).filter(URI.class::isInstance).forEach(res::add);
		}
		return res.toArray();
	}

	@Override
	public Object[] getChildren(Object parentElement) {
		if (parentElement instanceof URI && searchResult != null) {
			return searchResult.getMatches(parentElement);
		}
		return delegate.getChildren(parentElement);
	}

	@Override
	public @Nullable Object getParent(Object element) {
		return delegate.getParent(element);
	}

	@Override
	public boolean hasChildren(Object element) {
		return delegate.hasChildren(element) || (searchResult != null && ArrayUtil.contains(searchResult.getElements(), element));
	}

}
