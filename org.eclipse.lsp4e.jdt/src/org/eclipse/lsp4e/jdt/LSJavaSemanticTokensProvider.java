/*******************************************************************************
 * Copyright (c) 2024 Broadcom Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  - Alex Boyko (Broadcom Inc.) - Initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.jdt;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.ui.text.java.ISemanticTokensProvider;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.LanguageServers.LanguageServerDocumentExecutor;
import org.eclipse.lsp4e.operations.semanticTokens.SemanticHighlightReconcilerStrategy;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;

public class LSJavaSemanticTokensProvider implements ISemanticTokensProvider {
	
	@Override
	public Collection<ISemanticTokensProvider.SemanticToken> computeSemanticTokens(CompilationUnit ast) {
		IPreferenceStore prefStore = LanguageServerPlugin.getDefault().getPreferenceStore();
		LanguageServerJdtPlugin plugin = LanguageServerJdtPlugin.getDefault();
		if (plugin == null) {
			throw new IllegalStateException("Plugin hasn't been started!");
		}
		IPreferenceStore jstPrefStore = plugin.getPreferenceStore();
		
		if (prefStore.getBoolean(SemanticHighlightReconcilerStrategy.SEMANTIC_HIGHLIGHT_RECONCILER_DISABLED)
				|| !jstPrefStore.getBoolean(LspJdtConstants.PREF_SEMANTIC_TOKENS_SWITCH)) {
			return Collections.emptyList();
		}
		
		IResource resource = ast.getTypeRoot().getResource();
		if (resource == null) {
			return Collections.emptyList();
		}
		
		final IDocument theDocument = LSPEclipseUtils.getDocument(resource);
		if (theDocument == null) {
			return Collections.emptyList();
		}
		
		SemanticTokensParams params = getSemanticTokensParams(theDocument);
		if (params == null) {
			return Collections.emptyList();
		}
		
		LanguageServerDocumentExecutor executor = LanguageServers.forDocument(theDocument)
				.withFilter(this::hasSemanticTokensFull);
			
		try {
			return executor
				.computeFirst((w, ls) -> {
					return ls.getTextDocumentService().semanticTokensFull(params)
							.thenApply(semanticTokens -> {
								if (semanticTokens == null) {
									return Collections.<SemanticToken>emptyList();
								}
								SemanticTokensLegend legend = getSemanticTokensLegend(w);
								if (legend == null) {
									return Collections.<SemanticToken>emptyList();
								}
								return new JavaSemanticTokensProcessor(this::mapToTokenType, p -> {
									try {
										return LSPEclipseUtils.toOffset(p, theDocument);
									} catch (BadLocationException e) {
										throw new RuntimeException(e);
									}
								}).getSemanticTokens(semanticTokens.getData(), legend);
							});
				})
				.thenApply(o -> o.orElse(Collections.emptyList())).get(300, TimeUnit.MILLISECONDS);
		} catch (InterruptedException | ExecutionException | TimeoutException e) {
			LanguageServerPlugin.logError("Failed to fetch semantic tokens for '%s' from Language Servers".formatted(resource.getLocation()), e);
			return Collections.emptyList();
		}		
	}
	
	private @Nullable SemanticTokensParams getSemanticTokensParams(IDocument document) {
		URI uri = LSPEclipseUtils.toUri(document);
		if (uri != null) {
			final var semanticTokensParams = new SemanticTokensParams();
			semanticTokensParams.setTextDocument(LSPEclipseUtils.toTextDocumentIdentifier(uri));
			return semanticTokensParams;
		}
		return null;
	}

	private @Nullable SemanticTokensLegend getSemanticTokensLegend(final LanguageServerWrapper wrapper) {
		ServerCapabilities serverCapabilities = wrapper.getServerCapabilities();
		if (serverCapabilities != null) {
			SemanticTokensWithRegistrationOptions semanticTokensProvider = serverCapabilities
					.getSemanticTokensProvider();
			if (semanticTokensProvider != null) {
				return semanticTokensProvider.getLegend();
			}
		}
		return null;
	}

	private boolean hasSemanticTokensFull(final ServerCapabilities serverCapabilities) {
		return serverCapabilities.getSemanticTokensProvider() != null
				&& LSPEclipseUtils.hasCapability(serverCapabilities.getSemanticTokensProvider().getFull());
	}

	private ISemanticTokensProvider.TokenType mapToTokenType(String tokeTypeStr) {
		switch (tokeTypeStr) {
		case "method":
			return ISemanticTokensProvider.TokenType.METHOD;
		case "comment":
			return ISemanticTokensProvider.TokenType.SINGLE_LINE_COMMENT;
		case "variable":
			return ISemanticTokensProvider.TokenType.LOCAL_VARIABLE;
		case "type":	
			return ISemanticTokensProvider.TokenType.CLASS;
		case "property":
			return ISemanticTokensProvider.TokenType.FIELD;
		case "keyword":
			return ISemanticTokensProvider.TokenType.KEYWORD;
		case "operator":
			return ISemanticTokensProvider.TokenType.OPERATOR;
		case "number":
			return ISemanticTokensProvider.TokenType.NUMBER;
		case "string":
			return ISemanticTokensProvider.TokenType.STRING;
		case "enum":
			return ISemanticTokensProvider.TokenType.ENUM;
		case "class":
			return ISemanticTokensProvider.TokenType.CLASS;
		case "macro":
			return ISemanticTokensProvider.TokenType.STATIC_METHOD_INVOCATION;
		case "parameter":
			return ISemanticTokensProvider.TokenType.PARAMETER_VARIABLE;
		default:
			return ISemanticTokensProvider.TokenType.DEFAULT;
		}
	}


}
