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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.ui.quickaccess.IQuickAccessComputer;
import org.eclipse.ui.quickaccess.IQuickAccessComputerExtension;
import org.eclipse.ui.quickaccess.QuickAccessElement;

public class WorkspaceSymbolsQuickAccessProvider implements IQuickAccessComputer, IQuickAccessComputerExtension {


	private List<@NonNull LanguageServer> usedLanguageServers;

	@Override
	public QuickAccessElement[] computeElements() {
		return new QuickAccessElement[0];
	}

	@Override
	public void resetState() {
	}

	@Override
	public boolean needsRefresh() {
		return this.usedLanguageServers == null
				|| !this.usedLanguageServers.equals(LanguageServiceAccessor.getActiveLanguageServers(capabilities -> LSPEclipseUtils.hasCapability(capabilities.getWorkspaceSymbolProvider())));
	}

	@Override
	public QuickAccessElement[] computeElements(String query, IProgressMonitor monitor) {
		this.usedLanguageServers = LanguageServiceAccessor.getActiveLanguageServers(capabilities -> LSPEclipseUtils.hasCapability(capabilities.getWorkspaceSymbolProvider()));
		if (usedLanguageServers.isEmpty()) {
			return new QuickAccessElement[0];
		}
		WorkspaceSymbolParams params = new WorkspaceSymbolParams(query);
		List<QuickAccessElement> res = Collections.synchronizedList(new ArrayList<>());

		try {
			CompletableFuture.allOf(usedLanguageServers.stream()
					.map(ls -> ls.getWorkspaceService().symbol(params).thenAcceptAsync(symbols -> {
						if (symbols != null) {
							res.addAll(symbols.stream().map(WorkspaceSymbolQuickAccessElement::new)
									.collect(Collectors.toList()));
						}
					})).toArray(CompletableFuture[]::new)).get(1000, TimeUnit.MILLISECONDS);
		}
		catch (ExecutionException | TimeoutException | InterruptedException e) {
			LanguageServerPlugin.logError(e);
		}

		return res.toArray(new QuickAccessElement[res.size()]);
	}

}
