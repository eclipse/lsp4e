/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.operations.references;

import static org.eclipse.lsp4e.internal.NullSafetyHelper.castNonNull;

import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4j.Location;
import org.eclipse.search.ui.text.Match;

public class URIMatch extends Match {

	public static URIMatch create(final Location location) throws BadLocationException, URISyntaxException {
		final URI uri = new URI(location.getUri());
		final IDocument doc = castNonNull(LSPEclipseUtils.getDocument(uri));
		final int offset = LSPEclipseUtils.toOffset(location.getRange().getStart(), doc);
		final int length = LSPEclipseUtils.toOffset(location.getRange().getEnd(), doc) - LSPEclipseUtils.toOffset(location.getRange().getStart(), doc);
		return new URIMatch(location, uri, offset, length);
	}

	public final Location location;

	protected URIMatch(final Location location, final URI uri, final int offset, final int length) {
		super(uri, offset, length);
		this.location = location;
	}
}
