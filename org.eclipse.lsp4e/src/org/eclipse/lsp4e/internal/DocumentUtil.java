/*******************************************************************************
 * Copyright (c) 2023 Avaloq Group AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Rubén Porras Campo (Avaloq Group AG) - Initial Implementation
 *******************************************************************************/
package org.eclipse.lsp4e.internal;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension4;
import org.eclipse.lsp4e.LSPEclipseUtils;

public final class DocumentUtil {

	private DocumentUtil() {
		// this class shouldn't be instantiated
	}

	/**
	 * Gets the modification stamp for the supplied document, or returns
	 * {@code IDocumentExtension4.UNKNOWN_MODIFICATION_STAMP} if not available.
	 *
	 * In practice just a sanity-checked downcast of a legacy API: should expect the platform to be instantiating
	 * Documents that implement the later interfaces.
	 *
	 * @param document Document to check
	 * @return Opaque version stamp, or {@code IDocumentExtension4.UNKNOWN_MODIFICATION_STAMP} if not available
	 */
	public static long getDocumentModificationStamp(@Nullable IDocument document) {
		if (document instanceof IDocumentExtension4 ext) {
			return ext.getModificationStamp();
		} else if (document != null){
			IFile file = LSPEclipseUtils.getFile(document);
			if (file != null) {
				return file.getModificationStamp();
			}
		}
		return IDocumentExtension4.UNKNOWN_MODIFICATION_STAMP;
	}

}
