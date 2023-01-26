package org.eclipse.lsp4e;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.ConfigurationParams;

public class DefaultLanguageClientConfigurationProvider implements LanguageClientConfigurationProvider {

	private static final LanguageClientConfigurationProvider INSTANCE = new DefaultLanguageClientConfigurationProvider();

	public static LanguageClientConfigurationProvider getInstance() {
		return INSTANCE;
	}

	@Override
	public CompletableFuture<List<Object>> configuration(ConfigurationParams configurationParams) {
		return CompletableFuture.completedFuture(Collections.emptyList());
	}
}
