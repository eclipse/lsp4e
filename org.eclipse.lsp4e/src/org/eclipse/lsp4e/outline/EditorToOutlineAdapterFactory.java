/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Lucas Bullen (Red Hat Inc.) - Bug 520053 - Clicking nodes in the 'Outline' should navigate
 *******************************************************************************/
package org.eclipse.lsp4e.outline;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.runtime.IAdapterFactory;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServersRegistry;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.views.contentoutline.ContentOutline;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

public class EditorToOutlineAdapterFactory implements IAdapterFactory {

	private static final Map<IEditorPart, LanguageServer> lsCache = Collections.synchronizedMap(new HashMap<>());

	@Override
	public <T> T getAdapter(Object adaptableObject, Class<T> adapterType) {
		if (adapterType == IContentOutlinePage.class && adaptableObject instanceof IEditorPart
				&& LanguageServersRegistry.getInstance()
						.canUseLanguageServer(((IEditorPart) adaptableObject).getEditorInput())) {

			// first try to get / remove language server from cache from a previous call
			LanguageServer server = lsCache.remove(adaptableObject);
			if (server != null) {
				return (T) createOutlinePage(adaptableObject, server);
			}

			IDocument document = LSPEclipseUtils.getDocument(((IEditorPart) adaptableObject).getEditorInput());
			if (document != null) {
				CompletableFuture<List<@NonNull LanguageServer>> languageServers = LanguageServiceAccessor
						.getLanguageServers(document,
								capabilities -> LSPEclipseUtils.hasCapability(capabilities.getDocumentSymbolProvider()));

				List<@NonNull LanguageServer> servers = Collections.emptyList();
				try {
					servers = languageServers.get(50, TimeUnit.MILLISECONDS);
				} catch (TimeoutException e) {
					refreshContentOutlineAsync(languageServers, (IEditorPart) adaptableObject);
				} catch (ExecutionException e) {
					LanguageServerPlugin.logError(e);
				} catch (InterruptedException e) {
					LanguageServerPlugin.logError(e);
					Thread.currentThread().interrupt();
				}

				if (!servers.isEmpty()) {
					LanguageServer languageServer = servers.get(0); // TODO consider other strategies (select, //
																	// merge...?)
					return (T) createOutlinePage(adaptableObject, languageServer);
				}
			}
		}
		return null;
	}

	@Override
	public Class<?>[] getAdapterList() {
		return new Class<?>[] { IContentOutlinePage.class };
	}

	private static CNFOutlinePage createOutlinePage(Object adaptableObject, LanguageServer languageServer) {
		ITextEditor textEditor = null;
		if (adaptableObject instanceof ITextEditor) {
			textEditor = (ITextEditor) adaptableObject;
		}
		return new CNFOutlinePage(languageServer, textEditor);
	}

	private static void refreshContentOutlineAsync(CompletableFuture<List<@NonNull LanguageServer>> languageServers,
			IEditorPart editorPart) {
		// try to get language server asynchronously
		languageServers.thenAcceptAsync(servers -> {
			if (!servers.isEmpty()) {
				Display.getDefault().asyncExec(() -> {
					IViewPart viewPart = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
							.findView("org.eclipse.ui.views.ContentOutline"); //$NON-NLS-1$
					if (viewPart instanceof ContentOutline) {
						lsCache.put(editorPart, servers.get(0));
						((ContentOutline)viewPart).partActivated(editorPart);
					}
				});
			}
		});
	}
}
