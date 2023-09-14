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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.search.internal.ui.text.FileMatch;
import org.eclipse.search.internal.ui.text.FileSearchQuery;
import org.eclipse.search.internal.ui.text.FileSearchResult;
import org.eclipse.search.internal.ui.text.FileTreeContentProvider;
import org.eclipse.search.ui.SearchResultEvent;
import org.eclipse.search.ui.text.AbstractTextSearchResult;

public class FileAndURIMatchContentProvider implements ITreeContentProvider {

	private final FileTreeContentProvider delegate;
	private LSSearchResult searchResult;
	private FileSearchResult filteredFileSearchResult;

	FileAndURIMatchContentProvider(FileTreeContentProvider delegate) {
		this.delegate = delegate;
	}

	@Override
	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		if (newInput instanceof FileSearchResult initial && initial.getQuery() instanceof FileSearchQuery query) {
			this.filteredFileSearchResult = new FileSearchResult(query);
			Arrays.stream(initial.getElements()).flatMap(element -> Arrays.stream(initial.getMatches(element))).filter(FileMatch.class::isInstance).forEach(this.filteredFileSearchResult::addMatch);
			delegate.inputChanged(viewer, oldInput, this.filteredFileSearchResult);
		}
		if (newInput instanceof LSSearchResult searchResult) {
			this.searchResult = searchResult;
		}
	}

	@Override
	public Object[] getElements(Object inputElement) {
		List<Object> res = new ArrayList<>();
		res.addAll(Arrays.asList(delegate.getElements(inputElement == this.searchResult ? this.filteredFileSearchResult : inputElement)));
		if (inputElement instanceof AbstractTextSearchResult searchResult) {
			res.addAll(Arrays.stream(searchResult.getElements()).filter(URI.class::isInstance).toList());
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
	public Object getParent(Object element) {
		return delegate.getParent(element);
	}

	@Override
	public boolean hasChildren(Object element) {
		return delegate.hasChildren(element) || Arrays.asList(searchResult.getElements()).contains(element);
	}

}
