/*******************************************************************************
 * Copyright (c) 2016, 2017 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Rubén Porras Campo (Avaloq) - extracted to separate file
 *******************************************************************************/
package org.eclipse.lsp4e;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;

/**
 * A class that computes the attributes of a
 * {@link org.eclipse.core.resources.IMarker}.
 *
 * It can be extended by sub-classing it to add additional attributes to the
 * ones already set by this class.
 * <p>
 * When doing so {@link #computeMarkerAttributes(IResource)} and
 * {@link #computeMarkerAttributes(IDocument, Diagnostic)} should reuse the
 * basis implementation and only add attributes from the returned map.
 *
 * <p>
 * Implementation detail: For performance we allow subclasses to work directly
 * on the map returned by
 * {@link #computeMarkerAttributes(IDocument, Diagnostic)}. However
 * {@link #computeMarkerAttributes(IResource)} returns an immutable empty
 * collection, which cannot be extended.
 */
public class MarkerAttributeComputer {

	// Specific marker attributes defined by LSP4E
	public static final String LSP_DIAGNOSTIC = "lspDiagnostic"; //$NON-NLS-1$

	/**
	 * Computes the attributes of a marker for the given resource.
	 *
	 * @param resource,
	 *            the {@link Resource} where this marker will be created
	 * @return a map with the marker attributes
	 */
	public Map<String, Object> computeMarkerAttributes(@NonNull IResource resource) {
		return Collections.emptyMap();
	}

	/**
	 * Computes the attributes of a marker for the given document and diagnostic.
	 *
	 * @param document,
	 *            the {@link Document} attached to the given resource
	 * @param diagnostic,
	 *            the diagnostic to me mapped to a marker.
	 * @return a map with the marker attributes
	 */
	public Map<String, Object> computeMarkerAttributes(@Nullable IDocument document, @NonNull Diagnostic diagnostic) {
		Map<String, Object> targetAttributes = new HashMap<>(8);
		targetAttributes.put(MarkerAttributeComputer.LSP_DIAGNOSTIC, diagnostic);
		targetAttributes.put(IMarker.MESSAGE, diagnostic.getMessage());
		targetAttributes.put(IMarker.SEVERITY, LSPEclipseUtils.toEclipseMarkerSeverity(diagnostic.getSeverity()));

		if (document != null) {
			try {
				Range range = diagnostic.getRange();
				int documentLength = document.getLength();
				int start = Math.min(LSPEclipseUtils.toOffset(range.getStart(), document), documentLength);
				int end = Math.min(LSPEclipseUtils.toOffset(range.getEnd(), document), documentLength);
				if (start == end && documentLength > end) {
					end++;
					if (document.getLineOfOffset(end) != document.getLineOfOffset(start)) {
						start--;
						end--;
					}
				}
				targetAttributes.put(IMarker.CHAR_START, start);
				targetAttributes.put(IMarker.CHAR_END, end);
				targetAttributes.put(IMarker.LINE_NUMBER, document.getLineOfOffset(start) + 1);
			} catch (BadLocationException ex) {
				LanguageServerPlugin.logError(ex);
			}
		}
		return targetAttributes;
	}

}
