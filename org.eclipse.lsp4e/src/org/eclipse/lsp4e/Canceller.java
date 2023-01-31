package org.eclipse.lsp4e;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.eclipse.lsp4j.services.LanguageServer;

public class Canceller {

	private boolean cancelled;

	private List<CompletableFuture<?>> running = new ArrayList<>();

	private final Object lock = new Object();

	public Canceller() {}


	public boolean isCancelled() {
		synchronized (this.lock) {
			return this.cancelled;
		}
	}

	public void cancel() {
		synchronized(this.lock) {
			if (!this.cancelled) {
				this.cancelled = true;
				this.running.forEach(cf -> cf.cancel(true));
			}
		}
	}

	protected void checkCancelled() {
		if (this.cancelled) {
			throw new CancellationException();
		}
	}

	public <T> BiFunction<? super LanguageServerWrapper, LanguageServer, ? extends CompletableFuture<T>> wrap(BiFunction<? super LanguageServerWrapper, LanguageServer, ? extends CompletableFuture<T>> fn) {

		return (w, ls) -> {
			synchronized (this.lock) {
				checkCancelled();
				CompletableFuture<T> pending = fn.apply(w,  ls);
				this.running.add(pending);

				return pending;
			}
		};
	}

	public <T> Function<LanguageServer, ? extends CompletableFuture<T>> wrap(Function<LanguageServer, ? extends CompletableFuture<T>> fn) {
		return ls -> {
			checkCancelled();
			CompletableFuture<T> pending = fn.apply(ls);
			this.running.add(pending);

			return pending;

		};
	}

}
