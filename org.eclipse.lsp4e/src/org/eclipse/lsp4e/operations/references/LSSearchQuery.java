/*******************************************************************************
 * Copyright (c) 2016-23 Red Hat Inc. and others.
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

import java.net.URI;
import java.util.Objects;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.LanguageServers.LanguageServerDocumentExecutor;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.ReferenceContext;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.osgi.util.NLS;
import org.eclipse.search.internal.ui.text.FileMatch;
import org.eclipse.search.internal.ui.text.FileSearchQuery;
import org.eclipse.search.internal.ui.text.LineElement;
import org.eclipse.search.ui.ISearchQuery;
import org.eclipse.search.ui.text.Match;

/**
 * {@link ISearchQuery} implementation for LSP.
 *
 */
public class LSSearchQuery extends FileSearchQuery {

	private final @NonNull IDocument document;
	private final int offset;

	private LSSearchResult result;
	private long startTime;

	/**
	 * LSP search query to "Find references" from the given offset of the given
	 * {@link LanguageServerDocumentExecutor}.
	 *
	 * @param offset
	 * @param document
	 */
	public LSSearchQuery(int offset, @NonNull IDocument document)
			throws BadLocationException {
		super("", false, false, null); //$NON-NLS-1$
		this.document = document;
		this.offset = offset;
	}

	@Override
	public IStatus run(IProgressMonitor monitor) throws OperationCanceledException {
		startTime = System.currentTimeMillis();
		getSearchResult().removeAll();

		try {
			// Execute LSP "references" service
			final var params = new ReferenceParams();
			params.setContext(new ReferenceContext(false));
			params.setTextDocument(LSPEclipseUtils.toTextDocumentIdentifier(document));
			params.setPosition(LSPEclipseUtils.toPosition(offset, document));

			LanguageServers.forDocument(document).withCapability(ServerCapabilities::getReferencesProvider)
				.collectAll(languageServer -> languageServer.getTextDocumentService().references(params)
					.thenAcceptAsync(locations -> {
						if (locations != null) {
							// Convert each LSP Location to a Match search.
							locations.stream().filter(Objects::nonNull).map(LSSearchQuery::toMatch)
							.filter(Objects::nonNull).forEach(result::addMatch);
						}
					}).exceptionally(e -> {
						LanguageServerPlugin.logError(e);
						return null;
					})
			);

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
	private static Match toMatch(@NonNull Location location) {
		IResource resource = LSPEclipseUtils.findResourceFor(location.getUri());
		if (resource != null) {
			IDocument document = LSPEclipseUtils.getExistingDocument(resource);
			boolean temporaryLoadDocument = document == null;
			if (temporaryLoadDocument) {
				document = LSPEclipseUtils.getDocument(resource);
			}
			if (document != null) {
				try {
					int startOffset = LSPEclipseUtils.toOffset(location.getRange().getStart(), document);
					int endOffset = LSPEclipseUtils.toOffset(location.getRange().getEnd(), document);

					IRegion lineInformation = document.getLineInformationOfOffset(startOffset);
					final var lineEntry = new LineElement(resource, document.getLineOfOffset(startOffset) + 1,
							lineInformation.getOffset(),
							document.get(lineInformation.getOffset(), lineInformation.getLength()));
					return new FileMatch((IFile) resource, startOffset, endOffset - startOffset, lineEntry);
				} catch (BadLocationException ex) {
					LanguageServerPlugin.logError(ex);
				} finally {
					if (temporaryLoadDocument) {
						try {
							FileBuffers.getTextFileBufferManager().disconnect(resource.getFullPath(), LocationKind.IFILE, new NullProgressMonitor());
						} catch (CoreException e) {
							LanguageServerPlugin.logError(e);
						}
					}
				}
			}

			Position startPosition = location.getRange().getStart();
			final var lineEntry = new LineElement(resource, startPosition.getLine() + 1, 0,
					String.format("%s:%s", startPosition.getLine(), startPosition.getCharacter())); //$NON-NLS-1$
			return new FileMatch((IFile) resource, 0, 0, lineEntry);
		}
		return null;
	}

	@Override
	public LSSearchResult getSearchResult() {
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
		URI uri = LSPEclipseUtils.toUri(document);
		String filename;
		if (uri != null) {
			filename = Path.fromPortableString(uri.getPath()).lastSegment();
		} else {
			filename = "unkown"; //$NON-NLS-1$
		}
		Position position;
		try {
			position = LSPEclipseUtils.toPosition(offset, document);
		} catch (BadLocationException ex) {
			LanguageServerPlugin.logError(ex);
			position = new Position(0,0);
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
