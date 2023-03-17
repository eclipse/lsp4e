/*******************************************************************************
 * Copyright (c) 2016, 2020 Red Hat Inc. and others.
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
 *  Martin Lippert (Pivotal Inc.) - [Bug 561270] labels include more details now
 *******************************************************************************/
package org.eclipse.lsp4e.operations.declaration;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.ui.intro.config.IIntroURL;
import org.eclipse.ui.intro.config.IntroURLFactory;

public class LSBasedHyperlink implements IHyperlink {

	private final Either<Location, LocationLink> location;
	private final IRegion highlightRegion;
	private final String locationType;
	private final IDocument originDocument;

	public LSBasedHyperlink(Either<Location, LocationLink> location, IRegion highlightRegion, String locationType, IDocument originDocument) {
		this.location = location;
		this.highlightRegion = highlightRegion;
		this.locationType = locationType;
		this.originDocument = originDocument;
	}

	public LSBasedHyperlink(@NonNull Location location, IRegion linkRegion, String locationType, IDocument originDocument) {
		this(Either.forLeft(location), linkRegion, locationType, originDocument);
	}

	public LSBasedHyperlink(@NonNull LocationLink locationLink, IRegion linkRegion, String locationType, IDocument originDocument) {
		this(Either.forRight(locationLink), linkRegion, locationType, originDocument);
	}

	@Override
	public IRegion getHyperlinkRegion() {
		return this.highlightRegion;
	}

	@Override
	public String getTypeLabel() {
		return getLabel();
	}

	@Override
	public String getHyperlinkText() {
		return getLabel();
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
		if (location.isLeft()) {
			LSPEclipseUtils.openInEditor(location.getLeft(), originDocument);
		} else {
			LSPEclipseUtils.openInEditor(location.getRight(), originDocument);
		}
	}

	private String getLabel() {
		if (this.location != null) {
			String uri = this.location.isLeft() ? this.location.getLeft().getUri() : this.location.getRight().getTargetUri();
			if (uri != null) {
				if (uri.startsWith(LSPEclipseUtils.FILE_URI) && uri.length() > LSPEclipseUtils.FILE_URI.length()) {
					return getFileBasedLabel(uri);
				}
				else if (uri.startsWith(LSPEclipseUtils.INTRO_URL)) {
					return getIntroUrlBasedLabel(uri);
				}
				return getGenericUriBasedLabel(uri);
			}
		}

		return locationType;
	}

	private String getIntroUrlBasedLabel(String uri) {
		try {
			IIntroURL introUrl = IntroURLFactory.createIntroURL(uri);
			if (introUrl != null) {
				String label = introUrl.getParameter("label"); //$NON-NLS-1$
				if (label != null) {
					return locationType + " - " + label; //$NON-NLS-1$
				}
			}
		}
		catch (Exception e) {
			LanguageServerPlugin.logError(e.getMessage(), e);
		}

		return locationType;
	}

	private String getGenericUriBasedLabel(String uri) {
		return locationType + " - " + uri; //$NON-NLS-1$
	}

	private String getFileBasedLabel(String uri) {
		return locationType + " - " + uri.substring(LSPEclipseUtils.FILE_URI.length()); //$NON-NLS-1$
	}

}
