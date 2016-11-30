/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Michał Niewrzał (Rogue Wave Software Inc.) - hyperlink range detection
 *******************************************************************************/
package org.eclipse.languageserver.operations.declaration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.hyperlink.AbstractHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.languageserver.LSPEclipseUtils;
import org.eclipse.languageserver.LanguageServiceAccessor;
import org.eclipse.languageserver.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.languageserver.ui.Messages;
import org.eclipse.lsp4j.Location;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

public class OpenDeclarationHyperlinkDetector extends AbstractHyperlinkDetector {

	public class LSBasedHyperlink implements IHyperlink {

		private Location location;
		private IRegion region;

		public LSBasedHyperlink(Location response, IRegion region) {
			this.location = response;
			this.region = region;
		}

		@Override
		public IRegion getHyperlinkRegion() {
			return this.region;
		}

		@Override
		public String getTypeLabel() {
			return Messages.hyperlinkLabel;
		}

		@Override
		public String getHyperlinkText() {
			return Messages.hyperlinkLabel;
		}

		@Override
		public void open() {
			IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
			LSPEclipseUtils.openInEditor(location, page);
		}

	}

	@Override
	public IHyperlink[] detectHyperlinks(ITextViewer textViewer, IRegion region, boolean canShowMultipleHyperlinks) {
		final LSPDocumentInfo info = LanguageServiceAccessor.getLSPDocumentInfoFor(textViewer, (capabilities) -> Boolean.TRUE.equals(capabilities.getDefinitionProvider()));
		if (info != null) {
			try {
				CompletableFuture<List<? extends Location>> documentHighlight = info.getLanguageClient().getTextDocumentService()
						.definition(LSPEclipseUtils.toTextDocumentPosistionParams(info.getFileUri(), region.getOffset(), info.getDocument()));
				List<? extends Location> response = documentHighlight.get(2, TimeUnit.SECONDS);
				if (response.isEmpty()) {
					return null;
				}
				IRegion linkRegion = findWord(textViewer.getDocument(), region.getOffset());
				if (linkRegion == null) {
					linkRegion = region;
				}
				List<IHyperlink> hyperlinks = new ArrayList<IHyperlink>(response.size());
				for (Location responseLocation : response) {
					hyperlinks.add(new LSBasedHyperlink(responseLocation, linkRegion));
				}
				return hyperlinks.toArray(new IHyperlink[hyperlinks.size()]);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return null;
	}

	/**
	 * This method is only a workaround for missing range value (which can be
	 * used to highlight hyperlink) in LSP 'definition' response.
	 * 
	 * Should be removed when protocol will be updated
	 * (https://github.com/Microsoft/language-server-protocol/issues/3)
	 * 
	 * @param document
	 * @param offset
	 * @return
	 */
	private IRegion findWord(IDocument document, int offset) {
		int start = -2;
		int end = -1;

		try {

			int pos = offset;
			char c;

			while (pos >= 0) {
				c = document.getChar(pos);
				if (!Character.isUnicodeIdentifierPart(c))
					break;
				--pos;
			}

			start = pos;

			pos = offset;
			int length = document.getLength();

			while (pos < length) {
				c = document.getChar(pos);
				if (!Character.isUnicodeIdentifierPart(c))
					break;
				++pos;
			}

			end = pos;

		} catch (BadLocationException x) {
		}

		if (start >= -1 && end > -1) {
			if (start == offset && end == offset)
				return new Region(offset, 0);
			else if (start == offset)
				return new Region(start, end - start);
			else
				return new Region(start + 1, end - start - 1);
		}

		return null;
	}

}
