/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.hover;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.eclipse.jface.internal.text.html.BrowserInformationControl;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.DefaultInformationControl;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextHoverExtension;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.Range;
import org.eclipse.mylyn.wikitext.core.parser.MarkupParser;
import org.eclipse.mylyn.wikitext.markdown.core.MarkdownLanguage;
import org.eclipse.swt.widgets.Shell;

public class LSBasedHover implements ITextHover, ITextHoverExtension {

	private static final MarkupParser MARKDOWN_PARSER = new MarkupParser(new MarkdownLanguage());
	
	private CompletableFuture<Hover> hoverRequest;
	private IRegion lastRegion;
	private ITextViewer textViewer;

	public LSBasedHover() {
	}

	@Override
	public String getHoverInfo(ITextViewer textViewer, IRegion hoverRegion) {
		if (!(hoverRegion.equals(this.lastRegion) && textViewer.equals(this.textViewer) && this.hoverRequest != null)) {
			initiateHoverRequest(textViewer, hoverRegion.getOffset());
		}
		if (this.hoverRequest == null) {
			return null;
		}
		Hover hoverResult = null;
		try {
			hoverResult = this.hoverRequest.get(500, TimeUnit.MILLISECONDS);
		} catch (InterruptedException | ExecutionException | TimeoutException e) {
			e.printStackTrace(); // TODO
		}
		if (hoverResult == null) {
			return null;
		}
		String result = hoverResult.getContents().stream().collect(Collectors.joining("\n\n")); //$NON-NLS-1$
		return MARKDOWN_PARSER.parseToHtml(result);
	}

	@Override
	public IRegion getHoverRegion(ITextViewer textViewer, int offset) {
		IRegion res = new Region(offset, 0);
		final LSPDocumentInfo info = LanguageServiceAccessor.getLSPDocumentInfoFor(textViewer, (capabilities) -> Boolean.TRUE.equals(capabilities.getHoverProvider()));
		if (info != null) {
			try {
				initiateHoverRequest(textViewer, offset);
				Range range = hoverRequest.get(800, TimeUnit.MILLISECONDS).getRange();
				if (range != null) {
					int rangeOffset = LSPEclipseUtils.toOffset(range.getStart(), info.getDocument());
					res = new Region(rangeOffset, LSPEclipseUtils.toOffset(range.getEnd(), info.getDocument()) - rangeOffset);
				}
			} catch (Exception e) {
				e.printStackTrace();
				res = new Region(offset, 1);
			}
		} else {
			res = new Region(offset, 1);
		}

		this.lastRegion = res;
		this.textViewer = textViewer;
		return res;
	}

	private void initiateHoverRequest(ITextViewer viewer, int offset) {
		this.textViewer = viewer;
		final LSPDocumentInfo info = LanguageServiceAccessor.getLSPDocumentInfoFor(viewer, (capabilities) -> Boolean.TRUE.equals(capabilities.getHoverProvider()));
		if (info != null) {
			try {
				this.hoverRequest = info.getLanguageClient().getTextDocumentService().hover(LSPEclipseUtils.toTextDocumentPosistionParams(info.getFileUri(), offset, info.getDocument()));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public IInformationControlCreator getHoverControlCreator() {
		return new IInformationControlCreator() {

			@Override
			public IInformationControl createInformationControl(Shell parent) {
				// DefaultInformationControl is not able to render HTML use browser version if available
				if (BrowserInformationControl.isAvailable(parent)) {
					return new BrowserInformationControl(parent, JFaceResources.DEFAULT_FONT, true);
				} else {
					return new DefaultInformationControl(parent);
				}
			}
		};
	}
}
