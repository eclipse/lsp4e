/*******************************************************************************
 * Copyright (c) 2017, 2020 Pivotal Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  - Martin Lippert (Pivotal Inc.) - Initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.jdt;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.internal.ui.text.java.hover.JavadocBrowserInformationControlInput;
import org.eclipse.jdt.internal.ui.text.java.hover.JavadocHover;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.lsp4e.operations.hover.LSPTextHover;

@SuppressWarnings("restriction")
public class LSJavaHoverProvider extends JavadocHover {
	
	private LSPTextHover lsBasedHover;

	public LSJavaHoverProvider() {
		super();
		lsBasedHover = new LSPTextHover();
	}

	@Override
	public Object getHoverInfo2(ITextViewer textViewer, IRegion hoverRegion) {
		// Launch javadoc hover computation in async fashion
		CompletableFuture<JavadocBrowserInformationControlInput> javadocHoverFuture = CompletableFuture.supplyAsync(
				() -> (JavadocBrowserInformationControlInput) super.getHoverInfo2(textViewer, hoverRegion));
		String bootContent = this.lsBasedHover.getHoverInfo(textViewer, hoverRegion);

		if (bootContent != null && !bootContent.isEmpty()) {
			IJavaElement javaElement = null;
			JavadocBrowserInformationControlInput previous = null;
			int leadingImageWidth = 0;
			JavadocBrowserInformationControlInput input;
			String htmlContentFromOtherLs = "";

			try {
				input = javadocHoverFuture.get(500, TimeUnit.MILLISECONDS);
				if (input != null) {
					previous = (JavadocBrowserInformationControlInput) input.getPrevious();
					javaElement = input.getElement();
					leadingImageWidth = input.getLeadingImageWidth();
					htmlContentFromOtherLs = input.getHtml();
				}
			} catch (InterruptedException e) {
				htmlContentFromOtherLs = noJavadocMessage("Javadoc unavailable.");
			} catch (ExecutionException e) {
				htmlContentFromOtherLs = noJavadocMessage("Javadoc unavailable. Failed to obtain it.");
			} catch (TimeoutException e) {
				htmlContentFromOtherLs = noJavadocMessage("Javadoc unavailable. Took too long to obtain it.");
			}
			String content = formatContent(bootContent, htmlContentFromOtherLs);
			return new JavadocBrowserInformationControlInput(previous, javaElement, content, leadingImageWidth);
		} else {
			javadocHoverFuture.cancel(true);
		}
		return null;
	}
	
	private String formatContent(String content, String contentFromElsewhere) {
		if (content != null && content.trim().length() > 0 && contentFromElsewhere != null
				&& contentFromElsewhere.trim().length() > 0) {
			return content + "<hr/>" + contentFromElsewhere;
		} else {
			return content + contentFromElsewhere;
		}
	}

	private String noJavadocMessage(String message) {
		StringBuilder sb = new StringBuilder();
		sb.append("<h4>");
		sb.append(message);
		sb.append("</h4>");
		return sb.toString();
	}

	@Override
	public IRegion getHoverRegion(ITextViewer textViewer, int offset) {
		return this.lsBasedHover.getHoverRegion(textViewer, offset);
	}
	
}
