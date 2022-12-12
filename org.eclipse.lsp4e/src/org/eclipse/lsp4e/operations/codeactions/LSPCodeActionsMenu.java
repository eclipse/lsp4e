/*******************************************************************************
 * Copyright (c) 2016, 2022 Red Hat Inc. and others.
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

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.action.ContributionItem;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LSPExecutor;
import org.eclipse.lsp4e.LSPExecutor.LSPDocumentExecutor;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServerWrapper;
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

	private Range range;

	private LSPDocumentExecutor executor;

	@Override
	public void initialize(IServiceLocator serviceLocator) {
		IEditorPart editor = UI.getActiveTextEditor();
		if (editor != null) {
			ITextEditor textEditor = (ITextEditor) editor;
			IDocument document = LSPEclipseUtils.getDocument(textEditor);
			if (document == null) {
				return;
			}

			executor = LSPExecutor.forDocument(document);
			executor.withFilter(LSPCodeActionMarkerResolution::providesCodeActions);
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

		final URI fileUri = LSPEclipseUtils.toUri(this.executor.getDocument());

		CodeActionContext context = new CodeActionContext(Collections.emptyList());
		CodeActionParams params = new CodeActionParams();
		params.setTextDocument(new TextDocumentIdentifier(fileUri.toString()));
		params.setRange(this.range);
		params.setContext(context);

		final @NonNull List<@NonNull CompletableFuture<List<Either<Command, CodeAction>>>> actions
			= executor.computeAll((w, ls) -> ls.getTextDocumentService().codeAction(params)
					.whenComplete((codeActions, t) -> scheduleMenuUpdate(menu, index, w, t, codeActions)));

		if (actions.isEmpty()) {
			item.setText(Messages.notImplemented);
			return;
		}
		item.setText(Messages.computing);

		super.fill(menu, index);
	}

	private void scheduleMenuUpdate(final Menu menu, final int index, final LanguageServerWrapper wrapper, final Throwable u, final List<Either<Command, CodeAction>> codeActions) {
		UIJob job = new UIJob(menu.getDisplay(), Messages.updateCodeActions_menu) {
			@Override
			public IStatus runInUIThread(IProgressMonitor monitor) {
				if (u != null) {
					final MenuItem item = new MenuItem(menu, SWT.NONE, index);
					item.setText(u.getMessage());
					item.setImage(LSPImages.getSharedImage(ISharedImages.IMG_DEC_FIELD_ERROR));
					item.setEnabled(false);
				} else if (codeActions != null) {
					for (Either<Command, CodeAction> command : codeActions) {
						if (command != null) {
							if (command.isLeft()) {
								final MenuItem item = new MenuItem(menu, SWT.NONE, index);
								item.setText(command.getLeft().getTitle());
								item.addSelectionListener(new SelectionAdapter() {
									@Override
									public void widgetSelected(SelectionEvent e) {
										executeCommand(wrapper, command.getLeft());
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
											LSPEclipseUtils.applyWorkspaceEdit(codeAction.getEdit(), codeAction.getTitle());
										}
										if (codeAction.getCommand() != null) {
											executeCommand(wrapper, codeAction.getCommand());
										}
									}
								});
							}
						}
					}
				}
				return Status.OK_STATUS;
			}
		};
		job.schedule();
	}

	private void executeCommand(final LanguageServerWrapper wrapper, final Command command) {
		final ServerCapabilities capabilities = wrapper.getServerCapabilities();
		if (capabilities != null) {
			ExecuteCommandOptions provider = capabilities.getExecuteCommandProvider();
			if (provider != null && provider.getCommands().contains(command.getCommand())) {
				ExecuteCommandParams params = new ExecuteCommandParams();
				params.setCommand(command.getCommand());
				params.setArguments(command.getArguments());
				wrapper.execute(ls -> ls.getWorkspaceService().executeCommand(params));
			}
		}
	}

}
