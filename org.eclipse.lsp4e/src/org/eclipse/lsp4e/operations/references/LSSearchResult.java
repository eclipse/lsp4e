/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Mickael Istria (Red Hat Inc.) - initial implementation
 *   Michał Niewrzał (Rogue Wave Software Inc.)
 *   Angelo Zerr <angelo.zerr@gmail.com> - fix Bug 526255
 *******************************************************************************/
package org.eclipse.lsp4e.operations.references;

import org.eclipse.search.internal.ui.text.FileSearchResult;
import org.eclipse.search.ui.ISearchResult;

/**
 * {@link ISearchResult} implementation for LSP.
 *
 */
public class LSSearchResult extends FileSearchResult {

	public LSSearchResult(LSSearchQuery job) {
		super(job);
	}

}
