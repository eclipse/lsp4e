/*******************************************************************************
 * Copyright (c) 2023 Avaloq Group AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Rubén Porras Campo (Avaloq Group AG) - initial implementation
 *
 *******************************************************************************/
package org.eclipse.lsp4e.internal;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Predicate;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.HandlerEvent;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServers.LanguageServerDocumentExecutor;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

public abstract class LSPDocumentAbstractHandler extends AbstractHandler {

	private static class LanguageServerDocumentHandlerExecutor extends LanguageServerDocumentExecutor {
		LanguageServerDocumentHandlerExecutor(@NonNull IDocument document) {
			super(document);
		}

		/**
		 * Waits if necessary for at most 50 milliseconds for getting a server, if that
		 * is not enough:
		 * <li>It is assumed that the server would not be matching.
		 * <li>The server is get asynchronously and then a runner will be called if
		 * the call completes with true as final result.
		 *
		 * @return True if there is a language server for this document & server
		 *         capabilities.
		 */
		boolean anyMatching(Runnable runner) {
			return getServers().stream().filter(wF -> matches(wF, runner)).findFirst().isPresent();
		}

		@Override
		public @NonNull LanguageServerDocumentHandlerExecutor withFilter(final @NonNull Predicate<ServerCapabilities> filter) {
			return (LanguageServerDocumentHandlerExecutor) super.withFilter(filter);
		}

		Boolean matches(@NonNull CompletableFuture<@Nullable LanguageServerWrapper> wrapperFuture, Runnable runner) {
			try {
				return wrapperFuture.thenApply(Objects::nonNull).get(50, TimeUnit.MILLISECONDS);
			} catch (java.util.concurrent.ExecutionException e) {
				LanguageServerPlugin.logError(e);
			} catch (InterruptedException e) {
				LanguageServerPlugin.logError(e);
				Thread.currentThread().interrupt();
			} catch (TimeoutException e) {
				wrapperFuture.thenAcceptAsync(w -> {
					if (w != null) {
						runner.run();
					}
				});
				return Boolean.FALSE;
			}
			return Boolean.FALSE;
		}
	}

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		Optional.ofNullable(UI.asTextEditor(HandlerUtil.getActiveEditor(event)))
				.ifPresent(textEditor -> execute(event, textEditor));
		return null;
	}

	/**
	 * Intended to be implemented by sub-classes which work on the text editor. This
	 * method is only called if an {@link ITextEditor} can be obtained. Sub-classes
	 * may still override {@link #execute(ExecutionEvent)} for custom behavior.
	 *
	 * @param event
	 * @param textEditor
	 */
	protected abstract void execute(ExecutionEvent event, ITextEditor textEditor);

	protected boolean hasSelection(ITextEditor textEditor) {
		ISelectionProvider provider = textEditor.getSelectionProvider();
		ISelection selection = provider == null ? null : provider.getSelection();
		return selection instanceof ITextSelection && !selection.isEmpty();
	}

	protected void setEnabled(final @NonNull Function<ServerCapabilities, Either<Boolean, ? extends Object>> serverCapabilities, Predicate<ITextEditor> condition) {
		Predicate<ServerCapabilities> filter = f -> LSPEclipseUtils.hasCapability(serverCapabilities.apply(f));
		setEnabled(filter, condition);
	}

	protected void setEnabled(final @NonNull Predicate<ServerCapabilities> filter, Predicate<ITextEditor> condition) {
		ITextEditor textEditor = UI.getActiveTextEditor();
		if (textEditor != null && condition.test(textEditor)) {
			IDocument document = LSPEclipseUtils.getDocument(textEditor);
			if (document != null) {
				setBaseEnabled(new LanguageServerDocumentHandlerExecutor(document)
						.withFilter(filter)
						.anyMatching(() -> fireHandlerChanged(new HandlerEvent(this, true, false))));
				return;
			}
		}
		setBaseEnabled(false);
	}
}
