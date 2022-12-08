package org.eclipse.lsp4e;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.services.LanguageServer;

public abstract class LSPExecutor {


	// Pluggable strategy for getting the set of LSWrappers to dispatch operations on
	protected abstract List<CompletableFuture<LanguageServerWrapper>> getServers();

	private Predicate<ServerCapabilities> filter = s -> true;



	public <T> CompletableFuture<List<T>> collectAll(BiFunction<LanguageServerWrapper, LanguageServer, ? extends CompletionStage<T>> fn) {
		return null;
	}

	public <T> List<CompletableFuture<T>> computeAll(BiFunction<LanguageServerWrapper, LanguageServer, ? extends CompletionStage<T>> fn) {
		return null;
	}

	public <T> CompletableFuture<Optional<T>> computeFirst(BiFunction<LanguageServerWrapper, LanguageServer, ? extends CompletionStage<T>> fn) {
		return null;
	}


	public static LSPProjectExecutor forProject(final IProject proj) {
		// TODO: return LSPProjectExecutor
		return null;
	}

	public static LSPDocumentExecutor forDocument(final IDocument document) {
		// TODO: return LSPDocumentExecutor
		return null;
	}


	public LSPExecutor withFilter(Predicate<ServerCapabilities> filter) {
		this.filter = filter;
		return this;
	}


	public static class LSPDocumentExecutor extends LSPExecutor {
		public IDocument getDocument() {
			return null;
		}

		@Override
		protected List<CompletableFuture<LanguageServerWrapper>> getServers() {
			// Compute list of servers from document & filter
			return null;
		}
	}

	public static class LSPProjectExecutor extends LSPExecutor {

		@Override
		protected List<CompletableFuture<LanguageServerWrapper>> getServers() {
			// Compute list of servers from project & filter
			return null;
		}

	}



}
