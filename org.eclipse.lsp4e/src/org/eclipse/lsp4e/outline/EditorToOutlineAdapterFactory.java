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
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.views.contentoutline.ContentOutline;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

public class EditorToOutlineAdapterFactory implements IAdapterFactory {

	private static final String OUTLINE_VIEW_ID = "org.eclipse.ui.views.ContentOutline"; //$NON-NLS-1$

	private static final Map<IEditorPart, LanguageServer> LANG_SERVER_CACHE = Collections.synchronizedMap(new HashMap<>());

	@Override
	public <T> T getAdapter(Object adaptableObject, Class<T> adapterType) {
		if (adapterType == IContentOutlinePage.class && adaptableObject instanceof IEditorPart) {

			final IEditorPart editorPart = (IEditorPart) adaptableObject;
			final IEditorInput editorInput = editorPart.getEditorInput();

			if (editorInput != null && LanguageServersRegistry.getInstance().canUseLanguageServer(editorInput)) {

				// first try to get / remove language server from cache from a previous call
				LanguageServer server = LANG_SERVER_CACHE.remove(adaptableObject);
				if (server != null && LanguageServiceAccessor.isStillRunning(server)) {
					return adapterType.cast(createOutlinePage(editorPart, server));
				}

				IDocument document = LSPEclipseUtils.getDocument(editorInput);
				if (document != null) {
					CompletableFuture<List<@NonNull LanguageServer>> languageServers = LanguageServiceAccessor
							.getLanguageServers(document, capabilities -> LSPEclipseUtils
									.hasCapability(capabilities.getDocumentSymbolProvider()));

					List<@NonNull LanguageServer> servers = Collections.emptyList();
					try {
						 servers = languageServers.get(50, TimeUnit.MILLISECONDS);
					} catch (TimeoutException e) {
						refreshContentOutlineAsync(languageServers, editorPart);
					} catch (ExecutionException e) {
						LanguageServerPlugin.logError(e);
					} catch (InterruptedException e) {
						LanguageServerPlugin.logError(e);
						Thread.currentThread().interrupt();
					}
					if (!servers.isEmpty()) {
						// TODO consider other strategies (select, merge...?)
						LanguageServer languageServer = servers.get(0);
						if (LanguageServiceAccessor.isStillRunning(languageServer)) {
							return adapterType.cast(createOutlinePage(editorPart, languageServer));
						}
					}
				}
			}
		}
		return null;
	}

	@Override
	public Class<?>[] getAdapterList() {
		return new Class<?>[] { IContentOutlinePage.class };
	}

	private static CNFOutlinePage createOutlinePage(IEditorPart editorPart, @NonNull LanguageServer languageServer) {
		ITextEditor textEditor = null;
		if (editorPart instanceof ITextEditor) {
			textEditor = (ITextEditor) editorPart;
		}
		return new CNFOutlinePage(languageServer, textEditor);
	}

	private static void refreshContentOutlineAsync(CompletableFuture<List<@NonNull LanguageServer>> languageServers,
			IEditorPart editorPart) {
		// try to get language server asynchronously
		languageServers.thenAcceptAsync(servers -> {
			if (!servers.isEmpty()) {
				Display.getDefault().asyncExec(() -> {
					var page = UI.getActivePage();
					if(page != null) {
						IViewPart viewPart = page.findView(OUTLINE_VIEW_ID);
						if (viewPart instanceof ContentOutline) {
							LANG_SERVER_CACHE.put(editorPart, servers.get(0));
							((ContentOutline) viewPart).partActivated(editorPart);
						}
					}
				});
			}
		});
	}
}
