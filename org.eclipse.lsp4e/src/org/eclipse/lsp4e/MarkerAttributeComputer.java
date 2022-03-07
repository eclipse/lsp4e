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
 * Implementations can also customize {@link #attributeCount()} to optimize the
 * size of the attribute map when it is created.
 *
 * <p>
 * Implementation detail: For performance reasons we allow subclasses to add
 * attributes directly to the map returned by
 * {@link #computeMarkerAttributes(IDocument, Diagnostic)}. The attributes
 * returned by {@link #computeMarkerAttributes(IResource)} as well as an
 * attribute containing the language server ID are added as well to this map.
 * <p>
 * {@link #computeMarkerAttributes(IResource)} returns an immutable empty
 * collection, which cannot be modified.
 */
public class MarkerAttributeComputer {

	// Specific marker attributes defined by LSP4E
	public static final String LSP_DIAGNOSTIC = "lspDiagnostic"; //$NON-NLS-1$

	/**
	 * Defines the expected number of attributes that will be computed, it is used
	 * to optimize size of the map at creation time.
	 *
	 * @return the expected number of attributes that will be computed
	 */
	public int attributeCount() {
		return 8;
	}

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
	 *            the {@link Diagnostic} to me mapped to a marker.
	 * @return a map with the marker attributes
	 */
	public Map<String, Object> computeMarkerAttributes(@Nullable IDocument document, @NonNull Diagnostic diagnostic) {
		Map<String, Object> attributes = new HashMap<>(attributeCount());
		attributes.put(MarkerAttributeComputer.LSP_DIAGNOSTIC, diagnostic);
		attributes.put(IMarker.MESSAGE, diagnostic.getMessage());
		attributes.put(IMarker.SEVERITY, LSPEclipseUtils.toEclipseMarkerSeverity(diagnostic.getSeverity()));

		if (document != null) {
			Range range = diagnostic.getRange();
			int documentLength = document.getLength();
			try {
				int start = Math.min(LSPEclipseUtils.toOffset(range.getStart(), document), documentLength);
				int end = Math.min(LSPEclipseUtils.toOffset(range.getEnd(), document), documentLength);
				int lineOfStartOffset = document.getLineOfOffset(start);
				if (start == end && documentLength > end) {
					end++;
					if (document.getLineOfOffset(end) != lineOfStartOffset) {
						start--;
						end--;
					}
				}
				attributes.put(IMarker.CHAR_START, start);
				attributes.put(IMarker.CHAR_END, end);
				attributes.put(IMarker.LINE_NUMBER, lineOfStartOffset + 1);
			} catch (BadLocationException ex) {
				LanguageServerPlugin.logError(ex);
			}
		}
		return attributes;
	}

}
