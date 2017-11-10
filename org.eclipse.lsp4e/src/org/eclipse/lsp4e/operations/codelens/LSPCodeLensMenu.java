/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Lucas Bullen (Red Hat Inc.) - [Bug 517428] Requests sent before initialization
 *******************************************************************************/
package org.eclipse.lsp4e.operations.codelens;

import java.util.Collection;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.ContributionItem;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.ui.menus.IWorkbenchContribution;
import org.eclipse.ui.progress.UIJob;
import org.eclipse.ui.services.IServiceLocator;
import org.eclipse.ui.texteditor.ITextEditor;

public class LSPCodeLensMenu extends ContributionItem implements IWorkbenchContribution {

	private LSPDocumentInfo info;

	@Override
	public void initialize(IServiceLocator serviceLocator) {
		ITextEditor editor = LSPEclipseUtils.getActiveTextEditor();
		if (editor != null) {
			Collection<LSPDocumentInfo> infos = LanguageServiceAccessor.getLSPDocumentInfosFor(
					LSPEclipseUtils.getDocument(editor),
					capabilities -> capabilities.getCodeLensProvider() != null);
			if (!infos.isEmpty()) {
				this.info = infos.iterator().next();
			} else {
				this.info = null;
			}
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
		info.getInitializedLanguageClient()
				.thenCompose(languageServer -> languageServer.getTextDocumentService().codeLens(param))
				.whenComplete((t, u) -> {
					UIJob job = new UIJob(menu.getDisplay(), Messages.updateCodelensMenu_job) {
						@Override
						public IStatus runInUIThread(IProgressMonitor monitor) {
							if (u != null) {
								// log?
								item.setText(u.getMessage());
							} else if (t != null) {
								for (CodeLens lens : t) {
									if (lens != null && lens.getCommand() != null) {
										final MenuItem item = new MenuItem(menu, SWT.NONE, index);
										item.setText(lens.getCommand().getTitle());
										item.setEnabled(false);
									}
								}
							}
							if (menu.getItemCount() == 1) {
								item.setText(Messages.codeLens_emptyMenu);
							} else {
								item.dispose();
							}
							return Status.OK_STATUS;
						}
					};
					job.schedule();
				});
		super.fill(menu, index);
	}

}
