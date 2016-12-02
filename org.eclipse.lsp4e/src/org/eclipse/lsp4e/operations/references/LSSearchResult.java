/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Mickael Istria (Red Hat Inc.) - initial implementation
 *   Michał Niewrzał (Rogue Wave Software Inc.)
 *******************************************************************************/
package org.eclipse.lsp4e.operations.references;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.search.internal.ui.text.FileMatch;
import org.eclipse.search.internal.ui.text.FileSearchQuery;
import org.eclipse.search.internal.ui.text.FileSearchResult;
import org.eclipse.search.internal.ui.text.LineElement;
import org.eclipse.search.ui.ISearchQuery;
import org.eclipse.search.ui.ISearchResult;
import org.eclipse.search.ui.text.AbstractTextSearchResult;
import org.eclipse.search.ui.text.IEditorMatchAdapter;
import org.eclipse.search.ui.text.IFileMatchAdapter;
import org.eclipse.search.ui.text.Match;

public class LSSearchResult extends FileSearchResult {

	private class LSSearchQuery extends FileSearchQuery {

		public LSSearchQuery() {
			super(Messages.referenceSearchQuery, false, false, null);
		}

		@Override
		public IStatus run(IProgressMonitor monitor) throws OperationCanceledException {
			AbstractTextSearchResult textResult = (AbstractTextSearchResult) getSearchResult();
			textResult.removeAll();

			try {
				for (Location loc : references.get(4, TimeUnit.SECONDS)) {
					Match match = toMatch(loc, monitor);
					addMatch(match);
				}
				return Status.OK_STATUS;
			} catch (Exception ex) {
				return new Status(IStatus.ERROR,
				        LanguageServerPlugin.getDefault().getBundle().getSymbolicName(), ex.getMessage(), ex);
			}
		}

		@Override
		public ISearchResult getSearchResult() {
			return LSSearchResult.this;
		}
	}

	private ISearchQuery query;
	private CompletableFuture<List<? extends Location>> references;

	public LSSearchResult(CompletableFuture<List<? extends Location>> references) {
		super(null);
		this.references = references;
	}

	protected Match toMatch(Location location, IProgressMonitor monitor) {
		try {
			IResource resource = LSPEclipseUtils.findResourceFor(location.getUri());
			IDocument document = LSPEclipseUtils.getDocument(resource);
			if (document != null) {
				int startOffset = LSPEclipseUtils.toOffset(location.getRange().getStart(), document);
				int endOffset = LSPEclipseUtils.toOffset(location.getRange().getEnd(), document);

				IRegion lineInformation = document.getLineInformationOfOffset(startOffset);
				LineElement lineEntry = new LineElement(resource, document.getLineOfOffset(startOffset),
				        lineInformation.getOffset(),
				        document.get(lineInformation.getOffset(), lineInformation.getLength()));
				return new FileMatch((IFile) resource, startOffset, endOffset - startOffset, lineEntry);

			}
			Position startPosition = location.getRange().getStart();
			LineElement lineEntry = new LineElement(resource, startPosition.getLine(), 0,
			        String.format("%s:%s", startPosition.getLine(), startPosition.getCharacter())); //$NON-NLS-1$
			return new FileMatch((IFile) resource, 0, 0, lineEntry);
		} catch (BadLocationException ex) {
			ex.printStackTrace();
		}
		return null;
	}

	@Override
	public String getLabel() {
		return "References TODO Label"; //$NON-NLS-1$
	}

	@Override
	public String getTooltip() {
		return "References TODO Tooltip"; //$NON-NLS-1$
	}

	@Override
	public ImageDescriptor getImageDescriptor() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ISearchQuery getQuery() {
		if (this.query == null) {
			this.query = new LSSearchQuery();
		}
		return this.query;
	}

	@Override
	public IEditorMatchAdapter getEditorMatchAdapter() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IFileMatchAdapter getFileMatchAdapter() {
		// TODO Auto-generated method stub
		return null;
	}

}
