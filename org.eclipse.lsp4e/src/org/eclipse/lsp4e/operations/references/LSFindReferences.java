/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Michał Niewrzał (Rogue Wave Software Inc.)
 *******************************************************************************/
package org.eclipse.lsp4e.operations.references;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.ReferenceContext;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.search.ui.NewSearchUI;
import org.eclipse.search2.internal.ui.SearchView;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.progress.UIJob;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.ui.texteditor.ITextEditor;

public class LSFindReferences extends AbstractHandler implements IHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IEditorPart part = HandlerUtil.getActiveEditor(event);
		final SearchView searchView = getSearchView(event);
		if (searchView == null) {
			return null;
		}
		if (part instanceof ITextEditor) {
			LSPDocumentInfo info = LanguageServiceAccessor.getLSPDocumentInfoFor(
					LSPEclipseUtils.getDocument((ITextEditor)part),
					(capabilities) -> Boolean.TRUE.equals(capabilities.getReferencesProvider()));

			if (info != null) {
				ISelection sel = ((AbstractTextEditor) part).getSelectionProvider().getSelection();

				if (sel instanceof TextSelection) {
					try {
						ReferenceParams params = new ReferenceParams();
						params.setContext(new ReferenceContext(true));
						params.setTextDocument(new TextDocumentIdentifier(info.getFileUri().toString()));
						params.setPosition(LSPEclipseUtils.toPosition(((TextSelection) sel).getOffset(), info.getDocument()));
						CompletableFuture<List<? extends Location>> references = info.getLanguageClient()
						        .getTextDocumentService().references(params);
						LSSearchResult search = new LSSearchResult(references);
						searchView.getProgressService().schedule(new Job(Messages.findReferences_jobName) {
							@Override
							public IStatus run(IProgressMonitor monitor) {
								search.getQuery().run(monitor);
								UIJob refresh = new UIJob(Messages.findReferences_updateResultView_jobName) {
									@Override
									public IStatus runInUIThread(IProgressMonitor monitor) {
										searchView.showSearchResult(search);
										return Status.OK_STATUS;
									}
								};
								refresh.schedule();
								return Status.OK_STATUS;
							}
						});
					} catch (Exception e) {
						LanguageServerPlugin.logError(e);
					}
				}
			}
		}
		return null;
	}

	private SearchView getSearchView(ExecutionEvent event) {
		SearchView searchView = null;
		try {
			searchView = (SearchView) HandlerUtil.getActiveWorkbenchWindow(event).getActivePage().showView(NewSearchUI.SEARCH_VIEW_ID);
		} catch (PartInitException e) {
			LanguageServerPlugin.logError(e);
		}
		return searchView;
	}

	@Override
	public boolean isEnabled() {
		IWorkbenchPart part = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActivePart();
		if (part instanceof ITextEditor) {
			LSPDocumentInfo info = LanguageServiceAccessor.getLSPDocumentInfoFor(
				LSPEclipseUtils.getDocument((ITextEditor) part),
				(capabilities) -> Boolean.TRUE.equals(capabilities.getReferencesProvider()));
			ISelection selection = ((ITextEditor) part).getSelectionProvider().getSelection();
			return info != null && !selection.isEmpty() && selection instanceof ITextSelection;
		}
		return false;
	}

	@Override
	public boolean isHandled() {
		return true;
	}

}
