/*******************************************************************************
 * Copyright (c) 2017 Rogue Wave Software Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.documentLink;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.jface.text.hyperlink.AbstractHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.lsp4j.DocumentLink;
import org.eclipse.lsp4j.DocumentLinkParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;

public class DocumentLinkDetector extends AbstractHyperlinkDetector {

	public static class DocumentHyperlink implements IHyperlink {

		private String uri;
		private IRegion highlightRegion;

		public DocumentHyperlink(String uri, IRegion highlightRegion) {
			this.uri = uri;
			this.highlightRegion = highlightRegion;
		}

		@Override
		public IRegion getHyperlinkRegion() {
			return this.highlightRegion;
		}

		@Override
		public String getTypeLabel() {
			return uri;
		}

		@Override
		public String getHyperlinkText() {
			return uri;
		}

		@Override
		public void open() {
			IResource resource = LSPEclipseUtils.findResourceFor(uri);
			if (resource != null && resource.getType() == IResource.FILE) {
				IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
				try {
					IDE.openEditor(page, (IFile) resource);
				} catch (PartInitException e) {
					LanguageServerPlugin.logError(e);
				}
			}
		}

	}

	@Override
	public IHyperlink[] detectHyperlinks(ITextViewer textViewer, IRegion region, boolean canShowMultipleHyperlinks) {
		for (@NonNull LSPDocumentInfo info : LanguageServiceAccessor.getLSPDocumentInfosFor(textViewer.getDocument(), capabilities -> capabilities.getDocumentLinkProvider() != null)) {
			try {
				DocumentLinkParams params = new DocumentLinkParams(
						new TextDocumentIdentifier(info.getFileUri().toString()));
				CompletableFuture<List<DocumentLink>> documentLink = info.getInitializedLanguageClient()
						.thenCompose(languageServer -> languageServer.getTextDocumentService().documentLink(params));
				List<DocumentLink> links = documentLink.get(2, TimeUnit.SECONDS);
				if (links == null || links.isEmpty()) {
					continue;
				}

				List<IHyperlink> hyperlinks = new ArrayList<IHyperlink>(links.size());
				for (DocumentLink link : links) {
					int start = LSPEclipseUtils.toOffset(link.getRange().getStart(), textViewer.getDocument());
					int end = LSPEclipseUtils.toOffset(link.getRange().getEnd(), textViewer.getDocument());
					IRegion linkRegion = new Region(start, end - start);
					if (TextUtilities.overlaps(region, linkRegion)) {
						if (link.getTarget() != null) {
							hyperlinks.add(new DocumentHyperlink(link.getTarget(), linkRegion));
						}
					}
				}
				if (hyperlinks.isEmpty()) {
					return null;
				}
				return hyperlinks.toArray(new IHyperlink[hyperlinks.size()]);
			} catch (BadLocationException | InterruptedException | ExecutionException | TimeoutException e) {
				LanguageServerPlugin.logError(e);
			}
		}
		return null;
	}

}
