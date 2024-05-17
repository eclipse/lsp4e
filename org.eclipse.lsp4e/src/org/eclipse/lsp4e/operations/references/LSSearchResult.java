/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Mickael Istria (Red Hat Inc.) - initial implementation
 *   Michał Niewrzał (Rogue Wave Software Inc.)
 *   Angelo Zerr <angelo.zerr@gmail.com> - fix Bug 526255
 *******************************************************************************/
package org.eclipse.lsp4e.operations.references;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.resources.IFile;
import org.eclipse.search.internal.ui.text.FileSearchResult;
import org.eclipse.search.ui.ISearchResult;
import org.eclipse.search.ui.text.AbstractTextSearchResult;
import org.eclipse.search.ui.text.IEditorMatchAdapter;
import org.eclipse.search.ui.text.IFileMatchAdapter;
import org.eclipse.search.ui.text.Match;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IURIEditorInput;

/**
 * {@link ISearchResult} implementation for LSP; add support for URI only match (no resource)
 */
public class LSSearchResult extends FileSearchResult implements IEditorMatchAdapter, IFileMatchAdapter {

	public LSSearchResult(LSSearchQuery query) {
		super(query);
	}

	private static final Match[] EMPTY_ARR= new Match[0];
	private final Set<Object> nonFileElements = ConcurrentHashMap.newKeySet();

	@Override
	public IFile getFile(Object element) {
		return element instanceof IFile file ? file : null;
	}

	@Override
	public void addMatch(Match match) {
		super.addMatch(match);
		this.nonFileElements.add(match.getElement());
	}

	@Override
	public boolean isShownInEditor(Match match, IEditorPart editor) {
		IEditorInput ei= editor.getEditorInput();
		return (ei instanceof IFileEditorInput fi && Objects.equals(match.getElement(), fi.getFile())) ||
			(ei instanceof IURIEditorInput uriInput && Objects.equals(match.getElement(), uriInput.getURI()));
	}

	@Override
	public Match[] computeContainedMatches(AbstractTextSearchResult result, IEditorPart editor) {
		IEditorInput ei= editor.getEditorInput();
		if (ei instanceof IFileEditorInput fi) {
			return getMatches(fi.getFile());
		} else if (ei instanceof IURIEditorInput uriInput) {
			return getMatches(uriInput.getURI());
		}
		return EMPTY_ARR;
	}

	@Override
	public LSSearchQuery getQuery() {
		return (LSSearchQuery)super.getQuery();
	}

	@Override
	public IFileMatchAdapter getFileMatchAdapter() {
		return this;
	}

	@Override
	public IEditorMatchAdapter getEditorMatchAdapter() {
		return this;
	}

	@Override
	public String getLabel() {
		return "References"; //$NON-NLS-1$
	}

}
