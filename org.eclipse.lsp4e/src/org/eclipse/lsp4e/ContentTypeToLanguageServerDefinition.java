/*******************************************************************************
 * Copyright (c) 2016-2017 Rogue Wave Software Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *  Mickael Istria (Red Hat Inc.) - Introduce LanguageServerDefinition
 *******************************************************************************/
package org.eclipse.lsp4e;

import java.util.AbstractMap.SimpleEntry;

import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.lsp4e.LanguageServersRegistry.LanguageServerDefinition;

public class ContentTypeToLanguageServerDefinition extends SimpleEntry<IContentType, LanguageServerDefinition> {

	private static final long serialVersionUID = 6002703726009331762L;

	public ContentTypeToLanguageServerDefinition(@NonNull IContentType contentType, @NonNull LanguageServerDefinition provider) {
		super(contentType, provider);
	}

}
