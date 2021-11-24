/*******************************************************************************
 * Copyright (c) 2016 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.symbols;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.dialogs.PopupDialog;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.outline.CNFOutlinePage;
import org.eclipse.lsp4e.outline.LSSymbolsContentProvider.OutlineViewerInput;
import org.eclipse.lsp4e.outline.SymbolsModel.DocumentSymbolWithFile;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.FilteredTree;
import org.eclipse.ui.dialogs.PatternFilter;
import org.eclipse.ui.internal.navigator.NavigatorContentService;
import org.eclipse.ui.internal.navigator.NavigatorDecoratingLabelProvider;
import org.eclipse.ui.navigator.CommonViewerSorter;
import org.eclipse.ui.texteditor.ITextEditor;

public class LSPSymbolInFileDialog extends PopupDialog {

	private final OutlineViewerInput outlineViewerInput;
	private final ITextEditor textEditor;

	public LSPSymbolInFileDialog(@NonNull Shell parentShell, @NonNull ITextEditor textEditor,
			@NonNull IDocument document, @NonNull LanguageServer languageServer) {
		super(parentShell, PopupDialog.INFOPOPUPRESIZE_SHELLSTYLE, true, true, true, false, false, null, null);
		outlineViewerInput = new OutlineViewerInput(document, languageServer, textEditor);
		this.textEditor = textEditor;
		create();
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		IFile documentFile = outlineViewerInput.documentFile;
		getShell().setText(NLS.bind(Messages.symbolsInFile, documentFile == null ? null : documentFile.getName()));

		FilteredTree filteredTree = new FilteredTree(parent, SWT.BORDER, new PatternFilter(), true, false);
		TreeViewer viewer = filteredTree.getViewer();

		final var contentService = new NavigatorContentService(CNFOutlinePage.ID, viewer);
		filteredTree.addDisposeListener(ev -> contentService.dispose());
		final var contentProvider = contentService.createCommonContentProvider();
		viewer.setContentProvider(contentProvider);

		var sorter = new CommonViewerSorter();
		sorter.setContentService(contentService);
		viewer.setComparator(sorter);

		viewer.setLabelProvider(new NavigatorDecoratingLabelProvider(contentService.createCommonLabelProvider()));
		viewer.setUseHashlookup(true);
		viewer.addSelectionChangedListener(event -> {
			IStructuredSelection selection = (IStructuredSelection) event.getSelection();
			if (selection.isEmpty()) {
				return;
			}
			Object item = selection.getFirstElement();
			if (item instanceof Either<?, ?>) {
				item = ((Either<?, ?>) item).get();
			}

			Range range = null;
			if (item instanceof SymbolInformation) {
				range = ((SymbolInformation) item).getLocation().getRange();
			} else if (item instanceof DocumentSymbol) {
				range = ((DocumentSymbol) item).getSelectionRange();
			} else if (item instanceof DocumentSymbolWithFile) {
				range = ((DocumentSymbolWithFile) item).symbol.getSelectionRange();
			}
			if (range != null) {
				try {
					int offset = LSPEclipseUtils.toOffset(range.getStart(), outlineViewerInput.document);
					int endOffset = LSPEclipseUtils.toOffset(range.getEnd(), outlineViewerInput.document);
					textEditor.selectAndReveal(offset, endOffset - offset);
				} catch (BadLocationException e) {
					LanguageServerPlugin.logError(e);
				}
			}
		});

		viewer.setInput(outlineViewerInput);
		return filteredTree;
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);

		shell.setSize(280, 300);
		Control control = textEditor.getAdapter(Control.class);
		if (control != null) {
			shell.setLocation(
					control.toDisplay(control.getBounds().width - shell.getSize().x, control.getLocation().y));
		}
	}
}
