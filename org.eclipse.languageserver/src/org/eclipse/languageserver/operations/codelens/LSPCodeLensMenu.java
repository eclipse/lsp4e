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
package org.eclipse.languageserver.operations.codelens;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.ContributionItem;
import org.eclipse.languageserver.LanguageServiceAccessor;
import org.eclipse.languageserver.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.languageserver.ui.Messages;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.menus.IWorkbenchContribution;
import org.eclipse.ui.progress.UIJob;
import org.eclipse.ui.services.IServiceLocator;
import org.eclipse.ui.texteditor.ITextEditor;

public class LSPCodeLensMenu extends ContributionItem implements IWorkbenchContribution {

	private LSPDocumentInfo info;

	@Override
	public void initialize(IServiceLocator serviceLocator) {
		IEditorPart editor = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor();
		if (editor instanceof ITextEditor) {
			info = LanguageServiceAccessor.getLSPDocumentInfoFor((ITextEditor) editor, (capabilities) -> Boolean.TRUE.equals(capabilities.getCodeLensProvider()));
			// TODO should be ServerCapabilities::isCodeLensProvider, when available in ls-api
		}
	}

	@Override
	public void fill(final Menu menu, int index) {
		final MenuItem item = new MenuItem(menu, SWT.NONE, index);
		item.setEnabled(false);
		if (info == null){
			item.setText(Messages.notImplemented);
			return;
		}
		
		item.setText(Messages.computing);
		CodeLensParams param = new CodeLensParams(new TextDocumentIdentifier(info.getFileUri().toString()));;
		final CompletableFuture<List<? extends CodeLens>> codeLens = info.getLanguageClient().getTextDocumentService().codeLens(param);
		codeLens.whenComplete(new BiConsumer<List<? extends CodeLens>, Throwable>() {

			@Override
			public void accept(List<? extends CodeLens> t, Throwable u) {
				UIJob job = new UIJob(menu.getDisplay(), Messages.updateCodelensMenu_job) {
					@Override
					public IStatus runInUIThread(IProgressMonitor monitor) {
						if (u != null) {
							// log?
							item.setText(u.getMessage());
						} else {
							for (CodeLens lens : t) {
								if (lens != null) {
									final MenuItem item = new MenuItem(menu, SWT.NONE, index);
									item.setText(lens.getCommand().getTitle());
									item.setEnabled(false);
								}
							}
						}
						return Status.OK_STATUS;
					}
				};
				job.schedule();
			}

		});
		super.fill(menu, index);
	}

}
