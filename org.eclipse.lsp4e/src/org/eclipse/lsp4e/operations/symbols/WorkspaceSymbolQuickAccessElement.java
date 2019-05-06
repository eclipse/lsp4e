/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.symbols;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.outline.SymbolsLabelProvider;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.quickaccess.QuickAccessElement;

public class WorkspaceSymbolQuickAccessElement extends QuickAccessElement {

	private static final SymbolsLabelProvider LABEL_PROVIDER = new SymbolsLabelProvider(false, false);
	private final SymbolInformation symbol;

	public WorkspaceSymbolQuickAccessElement(SymbolInformation symbol) {
		this.symbol = symbol;
	}

	@Override
	public String getLabel() {
		return LABEL_PROVIDER.getText(symbol);
	}

	@Override
	public ImageDescriptor getImageDescriptor() {
		return ImageDescriptor.createFromImage(LABEL_PROVIDER.getImage(symbol));
	}

	@Override
	public String getId() {
		Range range = symbol.getLocation().getRange();
		return symbol.getName() + '@' + symbol.getLocation().getUri() + '[' + range.getStart().getLine() + ',' + range.getStart().getCharacter() + ':' + range.getEnd().getLine() + ',' + range.getEnd().getCharacter() + ']';
	}

	@Override
	public void execute() {
		LSPEclipseUtils.openInEditor(symbol.getLocation(), PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage());
	}

}
