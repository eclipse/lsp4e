/*******************************************************************************
 * Copyright (c) 2016, 2023 Red Hat Inc. and others.
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

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.annotation.NonNull;
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

	private static final String DASH_SEPARATOR = " - "; //$NON-NLS-1$
	private final Either<Location, LocationLink> location;
	private final IRegion highlightRegion;
	private final String locationType;

	public LSBasedHyperlink(Either<Location, LocationLink> location, IRegion highlightRegion, String locationType) {
		this.location = location;
		this.highlightRegion = highlightRegion;
		this.locationType = locationType;
	}

	public LSBasedHyperlink(@NonNull Location location, IRegion linkRegion, String locationType) {
		this(Either.forLeft(location), linkRegion, locationType);
	}

	public LSBasedHyperlink(@NonNull LocationLink locationLink, IRegion linkRegion, String locationType) {
		this(Either.forRight(locationLink), linkRegion, locationType);
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
			LSPEclipseUtils.openInEditor(location.getLeft());
		} else {
			LSPEclipseUtils.openInEditor(location.getRight());
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
					return locationType + DASH_SEPARATOR + label;
				}
			}
		}
		catch (Exception e) {
			LanguageServerPlugin.logError(e.getMessage(), e);
		}

		return locationType;
	}

	private String getGenericUriBasedLabel(String uri) {
		return locationType + DASH_SEPARATOR + uri;
	}

	private String getFileBasedLabel(String uriStr) {
		URI uri = URI.create(uriStr);
		IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
		IFile[] files = workspaceRoot.findFilesForLocationURI(uri);
		if (files != null && files.length == 1 && files[0].getProject() != null) {
			IFile file = files[0];
			IPath containerPath = file.getParent().getProjectRelativePath();
			return locationType + DASH_SEPARATOR + file.getName() + DASH_SEPARATOR + file.getProject().getName()
					+ (containerPath.isEmpty() ? "" : IPath.SEPARATOR + containerPath.toString()); //$NON-NLS-1$
		}
		Path path = Paths.get(uri);
		return locationType + DASH_SEPARATOR + path.getFileName()
				+ (path.getParent() == null || path.getParent().getParent() == null ? "" //$NON-NLS-1$
						: DASH_SEPARATOR + path.toString());
	}

}
