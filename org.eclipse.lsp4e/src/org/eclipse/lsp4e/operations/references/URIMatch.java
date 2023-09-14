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

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4j.Location;
import org.eclipse.search.ui.text.Match;

public class URIMatch extends Match {

	public final @NonNull Location location;

	public URIMatch(@NonNull Location location) throws BadLocationException {
		super(URI.create(location.getUri()), //
			LSPEclipseUtils.toOffset(location.getRange().getStart(), LSPEclipseUtils.getDocument(URI.create(location.getUri()))),
			LSPEclipseUtils.toOffset(location.getRange().getEnd(),  LSPEclipseUtils.getDocument(URI.create(location.getUri()))) - LSPEclipseUtils.toOffset(location.getRange().getStart(), LSPEclipseUtils.getDocument(URI.create(location.getUri()))));
		this.location = location;
	}

}
