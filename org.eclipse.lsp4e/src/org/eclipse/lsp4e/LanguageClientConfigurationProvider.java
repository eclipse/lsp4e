package org.eclipse.lsp4e;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.ConfigurationParams;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.TextDocumentIdentifier;

public interface LanguageClientConfigurationProvider {

	CompletableFuture<List<Object>> configuration(ConfigurationParams configurationParams);

	default void collectFormatting(FormattingOptions formattingOptions, TextDocumentIdentifier identifier, String languageId) {
		// Do nothing
	}
}
