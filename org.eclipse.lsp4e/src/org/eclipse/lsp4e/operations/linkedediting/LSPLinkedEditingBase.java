/*******************************************************************************
 * Copyright (c) 2021 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Victor Rubezhny (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.linkedediting;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.PreferenceChangeEvent;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4j.LinkedEditingRanges;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;

public class LSPLinkedEditingBase implements IPreferenceChangeListener {
	public static final String LINKED_EDITING_PREFERENCE = "org.eclipse.ui.genericeditor.linkedediting"; //$NON-NLS-1$

	protected LinkedEditingRanges fLinkedEditingRanges;
	private CompletableFuture<Void> request;
	protected boolean fEnabled;

	protected void install() {
		IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode(LanguageServerPlugin.PLUGIN_ID);
		preferences.addPreferenceChangeListener(this);
		this.fEnabled = preferences.getBoolean(LINKED_EDITING_PREFERENCE, true);
	}

	protected void uninstall() {
		IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode(LanguageServerPlugin.PLUGIN_ID);
		preferences.removePreferenceChangeListener(this);
		cancel();
	}

	protected CompletableFuture<Void> collectLinkedEditingRanges(IDocument document, int offset) {
		fLinkedEditingRanges = null;
		cancel();

		if (document == null) {
			return CompletableFuture.completedFuture(null);
		}
		Position position;
		try {
			position = LSPEclipseUtils.toPosition(offset, document);
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
			return CompletableFuture.completedFuture(null);
		}
		URI uri = LSPEclipseUtils.toUri(document);
		if(uri == null) {
			return CompletableFuture.completedFuture(null);
		}
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri.toString());
		TextDocumentPositionParams params = new TextDocumentPositionParams(identifier, position);

		return request = LanguageServiceAccessor.getLanguageServers(document,
					capabilities -> LSPEclipseUtils.hasCapability(capabilities.getLinkedEditingRangeProvider()))
				.thenComposeAsync(languageServers ->
					CompletableFuture.allOf(languageServers.stream()
							.map(ls -> ls.getTextDocumentService().linkedEditingRange(LSPEclipseUtils.toLinkedEditingRangeParams(params))
								.thenAcceptAsync(result -> fLinkedEditingRanges = result))
									.toArray(CompletableFuture[]::new)));
	}

	/**
	 * Cancel the last call of 'linkedEditing'.
	 */
	private void cancel() {
		if (request != null && !request.isDone()) {
			request.cancel(true);
			request = null;
		}
	}

	@Override
	public void preferenceChange(PreferenceChangeEvent event) {
		if (event.getKey().equals(LINKED_EDITING_PREFERENCE)) {
			this.fEnabled = Boolean.valueOf(event.getNewValue().toString());
		}
	}
}
