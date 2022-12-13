/*******************************************************************************
 * Copyright (c) 2022 Avaloq Group AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 *******************************************************************************/
package org.eclipse.lsp4e.operations.semanticTokens;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServersRegistry.LanguageServerDefinition;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.services.LanguageServer;


/**
 * A provider for {@link SemanticTokensLegend}.
 */
public class SemanticTokensLegendProvider {
private Map<String, SemanticTokensLegend> semanticTokensLegendMap;
private IDocument document;

/**
   * Tells the provider strategy on which document it will
   * work.
   *
   * @param document
   *          the document on which this mapper will work
   */
public void setDocument(final IDocument document) {
	this.document = document;
}

private void initSemanticTokensLegendMap() {
	IFile file = LSPEclipseUtils.getFile(document);
	if (file != null) {
	  try {
		semanticTokensLegendMap = new HashMap<>();
		for (LanguageServerWrapper wrapper: LanguageServiceAccessor.getLSWrappers(file, x -> true)) {
			ServerCapabilities serverCapabilities = wrapper.getServerCapabilities();
			if (serverCapabilities != null) {
				SemanticTokensWithRegistrationOptions semanticTokensProvider = serverCapabilities.getSemanticTokensProvider();
				if (semanticTokensProvider != null) {
					semanticTokensLegendMap.put(wrapper.serverDefinition.id, semanticTokensProvider.getLegend());
				}
			}
		}
	  } catch (IOException e) {
		semanticTokensLegendMap = Collections.emptyMap();
		LanguageServerPlugin.logError(e);
	  }
	} else {
	  semanticTokensLegendMap = Collections.emptyMap();
	}
}

/**
   * Gets the {@link SemanticTokensLegend} for the given {@link LanguageServer}.
   *
   * @param languageSever
   * @return
   */
public SemanticTokensLegend getSemanticTokensLegend(@NonNull final LanguageServer languageSever) {
	Optional<LanguageServerDefinition> serverDefinition = LanguageServiceAccessor.resolveServerDefinition(languageSever);
	if (serverDefinition.isPresent()) {
	  if (semanticTokensLegendMap == null) {
		initSemanticTokensLegendMap();
	  }
	  return semanticTokensLegendMap.get(serverDefinition.get().id);
	}
	return null;
}
}
