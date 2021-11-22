/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Lucas Bullen (Red Hat Inc.) - [Bug 517428] Requests sent before initialization
 *******************************************************************************/
package org.eclipse.lsp4e.operations.codeactions;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

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
import org.eclipse.lsp4e.ui.LSPImages;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.menus.IWorkbenchContribution;
import org.eclipse.ui.progress.UIJob;
import org.eclipse.ui.services.IServiceLocator;
import org.eclipse.ui.texteditor.ITextEditor;

public class LSPCodeActionsMenu extends ContributionItem implements IWorkbenchContribution {

	private List<LSPDocumentInfo> infos;
	private Range range;

	@Override
	public void initialize(IServiceLocator serviceLocator) {
		IEditorPart editor = UI.getActiveTextEditor();
		if (editor != null) {
			ITextEditor textEditor = (ITextEditor) editor;
			IDocument document = LSPEclipseUtils.getDocument(textEditor);
			if (document == null) {
				return;
			}
			infos = LanguageServiceAccessor.getLSPDocumentInfosFor(document,
					LSPCodeActionMarkerResolution::providesCodeActions);
			ITextSelection selection = (ITextSelection) textEditor.getSelectionProvider().getSelection();
			try {
				this.range = new Range(LSPEclipseUtils.toPosition(selection.getOffset(), document),
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
		if (infos.isEmpty()) {
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
			final CompletableFuture<List<Either<Command, CodeAction>>> codeActions = info.getInitializedLanguageClient()
					.thenComposeAsync(languageServer -> languageServer.getTextDocumentService().codeAction(params));
			runningFutures.add(codeActions);
			codeActions.whenComplete((t, u) -> {
				runningFutures.remove(codeActions);
				UIJob job = new UIJob(menu.getDisplay(), Messages.updateCodeActions_menu) {
					@Override
					public IStatus runInUIThread(IProgressMonitor monitor) {
						if (u != null) {
							final MenuItem item = new MenuItem(menu, SWT.NONE, index);
							item.setText(u.getMessage());
							item.setImage(LSPImages.getSharedImage(ISharedImages.IMG_DEC_FIELD_ERROR));
							item.setEnabled(false);
						} else if (t != null) {
							for (Either<Command, CodeAction> command : t) {
								if (command != null) {
									if (command.isLeft()) {
										final MenuItem item = new MenuItem(menu, SWT.NONE, index);
										item.setText(command.getLeft().getTitle());
										item.addSelectionListener(new SelectionAdapter() {
											@Override
											public void widgetSelected(SelectionEvent e) {
												executeCommand(info, command.getLeft());
											}
										});
									} else if (command.isRight()) {
										CodeAction codeAction = command.getRight();
										final MenuItem item = new MenuItem(menu, SWT.NONE, index);
										item.setText(codeAction.getTitle());
										item.addSelectionListener(new SelectionAdapter() {
											@Override
											public void widgetSelected(SelectionEvent e) {
												if (codeAction.getEdit() != null) {
													LSPEclipseUtils.applyWorkspaceEdit(codeAction.getEdit());
												}
												if (codeAction.getCommand() != null) {
													executeCommand(info, codeAction.getCommand());
												}
											}
										});
									}
								}
							}
						}
						if (menu.getItemCount() == 1) {
							item.setText(Messages.codeActions_emptyMenu);
						} else {
							item.dispose();
						}
						return Status.OK_STATUS;
					}
				};
				job.schedule();
			});
		}
		super.fill(menu, index);
	}

	private void executeCommand(LSPDocumentInfo info, Command command) {
		ServerCapabilities capabilities = info.getCapabilites();
		if (capabilities != null) {
			ExecuteCommandOptions provider = capabilities.getExecuteCommandProvider();
			if (provider != null && provider.getCommands().contains(command.getCommand())) {
				ExecuteCommandParams params = new ExecuteCommandParams();
				params.setCommand(command.getCommand());
				params.setArguments(command.getArguments());
				info.getInitializedLanguageClient()
						.thenAcceptAsync(ls -> ls.getWorkspaceService().executeCommand(params));
			}
		}
	}

}
