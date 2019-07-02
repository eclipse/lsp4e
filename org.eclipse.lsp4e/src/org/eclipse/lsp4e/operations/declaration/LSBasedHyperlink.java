/*******************************************************************************
 * Copyright (c) 2016, 2019 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Michał Niewrzał (Rogue Wave Software Inc.) - hyperlink range detection
 *  Lucas Bullen (Red Hat Inc.) - [Bug 517428] Requests sent before initialization
 *******************************************************************************/
package org.eclipse.lsp4e.operations.declaration;

import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

public class LSBasedHyperlink implements IHyperlink {

	private final Either<Location, LocationLink> location;
	private final IRegion highlightRegion;

	public LSBasedHyperlink(Either<Location, LocationLink> location, IRegion highlightRegion) {
		this.location = location;
		this.highlightRegion = highlightRegion;
	}

	public LSBasedHyperlink(Location location, IRegion linkRegion) {
		this(Either.forLeft(location), linkRegion);
	}

	public LSBasedHyperlink(LocationLink locationLink, IRegion linkRegion) {
		this(Either.forRight(locationLink), linkRegion);
	}

	@Override
	public IRegion getHyperlinkRegion() {
		return this.highlightRegion;
	}

	@Override
	public String getTypeLabel() {
		return Messages.hyperlinkLabel;
	}

	@Override
	public String getHyperlinkText() {
		return Messages.hyperlinkLabel;
	}

	/**
	 *
	 * @return
	 * @noreference test only
	 */
	public Either<Location, LocationLink> getLocation() {
		return location;
	}

	@Override
	public void open() {
		IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		if (location.isLeft()) {
			LSPEclipseUtils.openInEditor(location.getLeft(), page);
		} else {
			LSPEclipseUtils.openInEditor(location.getRight(), page);
		}
	}

}