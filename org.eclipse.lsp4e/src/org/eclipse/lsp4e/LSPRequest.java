package org.eclipse.lsp4e;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.lsp4j.DocumentLink;
import org.eclipse.lsp4j.DocumentLinkParams;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.services.LanguageServer;

public class LSPRequest implements CancelChecker {

	private final CompletableFuture<List<@NonNull LanguageServer>> languageServers;

	private final List<CompletableFuture<?>> futures;

	private boolean cancel;

	public LSPRequest(@NonNull CompletableFuture<List<@NonNull LanguageServer>> languageServers) {
		this.languageServers = languageServers;
		this.futures = new ArrayList<>();
	}

	public <U> CompletableFuture<U> thenApplyAsync(Function<? super List<@NonNull LanguageServer>, ? extends U> fn) {
		return languageServers.thenApplyAsync(fn);
	}

	public CompletableFuture<List<DocumentLink>> documentLink(LanguageServer languageServer,
			DocumentLinkParams params) {
		checkCanceled();
		CompletableFuture<List<DocumentLink>> request = languageServer.getTextDocumentService().documentLink(params);
		futures.add(request);
		return request;
	}

	public void cancel() {
		this.cancel = true;
		for (CompletableFuture<?> future : new ArrayList<>(futures)) {
			if (!future.isDone()) {
				future.cancel(true);
			}
		}
	}

	@Override
	public void checkCanceled() {
		if (cancel) {
			throw new CancellationException();
		}
	}
}
