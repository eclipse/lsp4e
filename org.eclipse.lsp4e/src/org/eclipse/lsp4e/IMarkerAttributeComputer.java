/*******************************************************************************
 * Copyright (c) 2016, 2017 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Rubén Porras Campo (Avaloq) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e;

import java.util.Map;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4j.Diagnostic;

public interface IMarkerAttributeComputer {
	public static final String LSP_DIAGNOSTIC = "lspDiagnostic"; //$NON-NLS-1$
	public static final String LANGUAGE_SERVER_ID = "languageServerId"; //$NON-NLS-1$

	/**
	 * Defines the expected number of attributes that will be computed, it is used
	 * to optimize size of the map at creation time.
	 *
	 * @return the expected number of attributes that will be computed
	 */
	int attributeCount();

	/**
	 * @param languageServerId
	 */
	void initilize(@NonNull String languageServerId);

	/**
	 * @param resource
	 * @param document
	 * @param diagnostic,
	 *            the diagnostic to me mapped to a marker.
	 * @return a map with the marker attributes
	 * @throws CoreException
	 */
	Map<String, Object> computeMarkerAttributes(@NonNull IResource resource, @Nullable IDocument document,
			@NonNull Diagnostic diagnostic) throws CoreException;

}
