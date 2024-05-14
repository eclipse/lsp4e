/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.filebuffers.IDocumentSetupParticipant;
import org.eclipse.core.filebuffers.IDocumentSetupParticipantExtension;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.text.IDocument;

/**
 * Implements a file buffer lifecycle hook: when a document is opened in the IDE, ensure that all LS that deal with this document's
 * content type are started. For many LS this is not needed, as other features such as outlining or semantic highlighting will also
 * be set up when the editor is configured, and all such features will trigger a connect before requesting data from the server.
 * However lightweight LS (i.e. linters and other supplementary servers that only e.g. contribute diagnostic markers) will not
 * support such rich functionality and so need an explicit connect so that they can begin their analysis.
 */
public class ConnectDocumentToLanguageServerSetupParticipant implements IDocumentSetupParticipant, IDocumentSetupParticipantExtension {

	private static final Set<CompletableFuture<?>> PENDING_CONNECTIONS = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

	@Override
	public void setup(IDocument document) {
		ITextFileBuffer buffer = ITextFileBufferManager.DEFAULT.getTextFileBuffer(document);
		if (buffer == null || buffer.getLocation() == null) {
			return;
		}
		setup(document, buffer.getLocation(), LocationKind.IFILE);
	}

	@Override
	public void setup(final IDocument document, IPath location, LocationKind locationKind) {
		if (document == null) {
			return;
		}
		// Force document connect
		CompletableFuture.runAsync(
				() -> PENDING_CONNECTIONS.add(LanguageServers.forDocument(document).collectAll(ls -> CompletableFuture.completedFuture(null))),
				CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS)); // delay to ensure the document is initialized and can be resolved by LSPEclipseUtils.toUri
	}

	/**
	 * Testing hook to ensure teardown doesn't remove documents while the LSP has in-flight async
	 * jobs trying to attach to them
	 */
	public static void waitForAll() {
		PENDING_CONNECTIONS.forEach(cf -> {
			try {
				cf.get(1000, TimeUnit.MILLISECONDS);
			} catch (InterruptedException | ExecutionException | TimeoutException e) {
				LanguageServerPlugin.logInfo("Interrupted trying to cancel document setup"); //$NON-NLS-1$;
			}
		});
	}

}
