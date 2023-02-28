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
 *  Angelo Zerr <angelo.zerr@gmail.com> - Bug 525400 - [rename] improve rename support with ltk UI
 *  Jan Koehnlein (TypeFox) - handle missing existing document gracefully
 *  Martin Lippert (Pivotal) - Bug 561373 - added async enablement for late language servers
 *  Vincent Lorenzo (CEA LIST) vincent.lorenzo@cea.fr - Bug 564839
 *******************************************************************************/
package org.eclipse.lsp4e.operations.rename;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.HandlerEvent;
import org.eclipse.core.commands.IHandler;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.LanguageServers.LanguageServerDocumentExecutor;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.ltk.core.refactoring.participants.ProcessorBasedRefactoring;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

public class LSPRenameHandler extends AbstractHandler implements IHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IEditorPart part = HandlerUtil.getActiveEditor(event);
		if (part instanceof ITextEditor textEditor) {
			ISelectionProvider provider = textEditor.getSelectionProvider();
			if (provider == null) {
				return null;
			}
			ISelection sel = provider.getSelection();
			if (sel instanceof ITextSelection textSelection && !textSelection.isEmpty()) {
				IDocument document = LSPEclipseUtils.getDocument(textEditor);
				if (document != null) {
					Shell shell = part.getSite().getShell();
					LanguageServerDocumentExecutor executor = LanguageServers.forDocument(document).withCapability(ServerCapabilities::getRenameProvider);
					if (executor.anyMatching()) {
						int offset = textSelection.getOffset();

						final var processor = new LSPRenameProcessor(document, offset);
						final var refactoring = new ProcessorBasedRefactoring(processor);
						final var wizard = new LSPRenameRefactoringWizard(refactoring);
						final var operation = new RefactoringWizardOpenOperation(wizard);
						shell.getDisplay().asyncExec(() -> {
							try {
								operation.run(shell, Messages.rename_title);
							} catch (InterruptedException e1) {
								LanguageServerPlugin.logError(e1);
								Thread.currentThread().interrupt();
							}
						});
					}
				}
			}
		}
		return null;
	}

	@Override
	public void setEnabled(Object evaluationContext) {
		if (UI.getActivePart() instanceof ITextEditor textEditor) {
			ISelectionProvider provider = textEditor.getSelectionProvider();
			ISelection selection = (provider == null) ? null : provider.getSelection();
			var isEnable = selection instanceof ITextSelection && !selection.isEmpty();

			IDocument document = LSPEclipseUtils.getDocument(textEditor);
			if (document != null && isEnable) {
				// in case the language servers take longer to kick in, defer the final enablement to a later time
				isEnable = LanguageServers.forDocument(document).withCapability(ServerCapabilities::getRenameProvider)
						.anyMatching(() -> fireHandlerChanged(new HandlerEvent(this, true, false)));
			}
			setBaseEnabled(isEnable);
		} else {
			setBaseEnabled(false);
		}
	}

}
