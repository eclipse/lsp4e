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
 *   Angelo Zerr <angelo.zerr@gmail.com> - fix Bug 526255
 *   Lucas Bullen (Red Hat Inc.) - [Bug 517428] Requests sent before initialization
 *******************************************************************************/
package org.eclipse.lsp4e.operations.references;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.ReferenceContext;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.osgi.util.NLS;
import org.eclipse.search.internal.ui.text.FileMatch;
import org.eclipse.search.internal.ui.text.FileSearchQuery;
import org.eclipse.search.internal.ui.text.LineElement;
import org.eclipse.search.ui.ISearchQuery;
import org.eclipse.search.ui.ISearchResult;
import org.eclipse.search.ui.text.AbstractTextSearchResult;
import org.eclipse.search.ui.text.Match;

/**
 * {@link ISearchQuery} implementation for LSP.
 *
 */
public class LSSearchQuery extends FileSearchQuery {

	private final @NonNull IDocument document;
	private final @NonNull LanguageServer languageServer;
	private final Position position;
	private final String filename;

	private LSSearchResult result;
	private long startTime;

	private CompletableFuture<List<? extends Location>> references;

	/**
	 * LSP search query to "Find references" from the given offset of the given
	 * {@link LSPDocumentInfo}.
	 *
	 * @param document
	 * @param offset
	 * @param languageServer
	 * @throws BadLocationException
	 */
	public LSSearchQuery(@NonNull IDocument document, int offset, @NonNull LanguageServer languageServer)
			throws BadLocationException {
		super("", false, false, null); //$NON-NLS-1$
		this.document = document;
		this.languageServer = languageServer;
		this.position = LSPEclipseUtils.toPosition(offset, document);
		this.filename = Path.fromPortableString(LSPEclipseUtils.toUri(document).getPath()).lastSegment();
	}

	@Override
	public IStatus run(IProgressMonitor monitor) throws OperationCanceledException {
		startTime = System.currentTimeMillis();
		// Cancel last references future if needed.
		if (references != null) {
			references.cancel(true);
		}
		AbstractTextSearchResult textResult = (AbstractTextSearchResult) getSearchResult();
		textResult.removeAll();

		try {
			// Execute LSP "references" service
			ReferenceParams params = new ReferenceParams();
			params.setContext(new ReferenceContext(true));
			params.setTextDocument(new TextDocumentIdentifier(LSPEclipseUtils.toUri(document).toString()));
			params.setPosition(position);
			languageServer.getTextDocumentService().references(params)
					.thenAcceptAsync(locs -> {
						// Loop for each LSP Location and convert it to Match search.
						for (Location loc : locs) {
							Match match = toMatch(loc);
							result.addMatch(match);
						}
					});
			return Status.OK_STATUS;
		} catch (Exception ex) {
			return new Status(IStatus.ERROR, LanguageServerPlugin.getDefault().getBundle().getSymbolicName(),
					ex.getMessage(), ex);
		}
	}

	/**
	 * Convert the given LSP {@link Location} to Eclipse search {@link Match}.
	 *
	 * @param location
	 *            the LSP location to convert.
	 * @return the converted Eclipse search {@link Match}.
	 */
	private static Match toMatch(Location location) {
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
			LanguageServerPlugin.logError(ex);
		}
		return null;
	}

	@Override
	public ISearchResult getSearchResult() {
		if (result == null) {
			result = new LSSearchResult(this);
		}
		return result;
	}

	@Override
	public String getLabel() {
		return Messages.LSSearchQuery_label;
	}

	@Override
	public String getResultLabel(int nMatches) {
		long time = 0;
		if (startTime > 0) {
			time = System.currentTimeMillis() - startTime;
		}
		if (nMatches == 1) {
			return NLS.bind(Messages.LSSearchQuery_singularReference,
					new Object[] { filename, position.getLine() + 1, position.getCharacter() + 1, time });
		}
		return NLS.bind(Messages.LSSearchQuery_pluralReferences,
				new Object[] { filename, position.getLine() + 1, position.getCharacter() + 1, nMatches, time });
	}

	@Override
	public boolean isFileNameSearch() {
		// Return false to display lines where references are found
		return false;
	}
}
