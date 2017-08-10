/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.codeactions;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.ContributionItem;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.menus.IWorkbenchContribution;
import org.eclipse.ui.progress.UIJob;
import org.eclipse.ui.services.IServiceLocator;
import org.eclipse.ui.texteditor.ITextEditor;

public class LSPCodeActionsMenu extends ContributionItem implements IWorkbenchContribution {

	private List<LSPDocumentInfo> infos;
	private Range range;

	@Override
	public void initialize(IServiceLocator serviceLocator) {
		IEditorPart editor = LSPEclipseUtils.getActiveTextEditor();
		if (editor != null) {
			ITextEditor textEditor = (ITextEditor) editor;
			IDocument document = LSPEclipseUtils.getDocument(textEditor);
			if (document == null) {
				return;
			}
			infos = LanguageServiceAccessor.getLSPDocumentInfosFor(
					document,
					(capabilities) -> Boolean.TRUE.equals(capabilities.getCodeActionProvider()));
			ITextSelection selection = (ITextSelection) textEditor.getSelectionProvider().getSelection();
			try {
				this.range = new Range(
						LSPEclipseUtils.toPosition(selection.getOffset(), document),
						LSPEclipseUtils.toPosition(selection.getOffset() + selection.getLength(), document));
			} catch (BadLocationException e) {
				LanguageServerPlugin.logError(e);
			}
		}
	}

	@Override
	public void fill(final Menu menu, int index) {
		final MenuItem item = new MenuItem(menu, SWT.NONE, index);
		item.setEnabled(false);
		if (infos.isEmpty()){
			item.setText(Messages.notImplemented);
			return;
		}

		item.setText(Messages.computing);
		CodeActionContext context = new CodeActionContext(Collections.emptyList());
		CodeActionParams params = new CodeActionParams();
		params.setTextDocument(new TextDocumentIdentifier(infos.get(0).getFileUri().toString()));
		params.setRange(this.range);
		params.setContext(context);
		Set<CompletableFuture<?>> runningFutures = new HashSet<>();
		for (LSPDocumentInfo info : this.infos) {
			final CompletableFuture<List<? extends Command>> codeActions = info.getLanguageClient().getTextDocumentService().codeAction(params);
			runningFutures.add(codeActions);
			codeActions.whenComplete(new BiConsumer<List<? extends Command>, Throwable>() {
				@Override
				public void accept(List<? extends Command> t, Throwable u) {
					runningFutures.remove(codeActions);
					UIJob job = new UIJob(menu.getDisplay(), Messages.updateCodeActions_menu) {
						@Override
						public IStatus runInUIThread(IProgressMonitor monitor) {
							if (u != null) {
								final MenuItem item = new MenuItem(menu, SWT.NONE, index);
								item.setText(u.getMessage());
								item.setImage(PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_DEC_FIELD_ERROR));
								item.setEnabled(false);
							} else {
								for (Command command : t) {
									if (command != null) {
										final MenuItem item = new MenuItem(menu, SWT.NONE, index);
										item.setText(command.getTitle());
										item.setEnabled(false);
									}
								}
							}
							if (runningFutures.isEmpty()) {
								item.dispose();
							}
							return Status.OK_STATUS;
						}
					};
					job.schedule();
				}

			});
		}
		super.fill(menu, index);
	}

}
