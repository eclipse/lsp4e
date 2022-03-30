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

import java.util.Map;

import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4j.Diagnostic;

/**
 * An interface that allows adding custom attributes to a
 * {@link org.eclipse.core.resources.IMarker}.
 *
 */
public interface IMarkerAttributeComputer {

	/**
	 * Adds new attributes to a marker for the given document, diagnostic and
	 * resource.
	 *
	 * @param diagnostic
	 *            the {@link Diagnostic} to me mapped to a marker
	 * @param document
	 *            the {@link IDocument} attached to the given resource
	 * @param resource
	 *            the {@link IResource} that contains the document
	 * @param attributes
	 *            the map with the attributes for the marker, where the
	 *            implementation can add attributes
	 */
	public void addMarkerAttributesForDiagnostic(@NonNull Diagnostic diagnostic, @Nullable IDocument document,
			@NonNull IResource resource, @NonNull Map<String, Object> attributes);
}
