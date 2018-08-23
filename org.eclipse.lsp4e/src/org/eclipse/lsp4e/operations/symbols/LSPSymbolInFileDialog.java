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

import java.net.URI;
import java.util.List;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.dialogs.PopupDialog;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.outline.SymbolsLabelProvider;
import org.eclipse.lsp4e.outline.SymbolsModel.DocumentSymbolWithFile;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.FilteredTree;
import org.eclipse.ui.dialogs.PatternFilter;
import org.eclipse.ui.texteditor.ITextEditor;

public class LSPSymbolInFileDialog extends PopupDialog {

	private static class SymbolsContentProvider extends ArrayContentProvider implements ITreeContentProvider {

		@Override
		public Object[] getChildren(Object parentElement) {
			return null;
		}

		@Override
		public Object getParent(Object element) {
			return null;
		}

		@Override
		public boolean hasChildren(Object element) {
			return false;
		}
	}

	private ITextEditor fTextEditor;
	private List<Either<SymbolInformation, DocumentSymbol>> fSymbols;

	private FilteredTree fFilteredTree;
	private @NonNull URI fileURI;

	public LSPSymbolInFileDialog(Shell parentShell, ITextEditor textEditor,
			@NonNull URI fileURI, List<Either<SymbolInformation, DocumentSymbol>> t) {
		super(parentShell, PopupDialog.INFOPOPUP_SHELLSTYLE, true, true, true, false, false, null, null);
		this.fTextEditor = textEditor;
		this.fileURI = fileURI;
		this.fSymbols = t;
		create();
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		fFilteredTree = new FilteredTree(parent, SWT.BORDER, new PatternFilter(), true);
		TreeViewer viewer = fFilteredTree.getViewer();

		viewer.setContentProvider(new SymbolsContentProvider());
		IResource targetResource = LSPEclipseUtils.findResourceFor(this.fileURI.toString());
		viewer.setLabelProvider(new SymbolsLabelProvider());
		viewer.setUseHashlookup(true);
		viewer.addSelectionChangedListener(event -> {
			if (targetResource == null) {
				return;
			}
			IStructuredSelection selection = (IStructuredSelection) event.getSelection();
			if (selection.isEmpty()) {
				return;
			}
			Object item = selection.getFirstElement();
			if (item instanceof Either<?, ?>) {
				item = ((Either<?, ?>) item).get();
			}
			IDocument targetDocument = FileBuffers.getTextFileBufferManager()
					.getTextFileBuffer(targetResource.getFullPath(), LocationKind.IFILE).getDocument();

			Range range = null;
			if (item instanceof SymbolInformation) {
				range = ((SymbolInformation) item).getLocation().getRange();
			} else if (item instanceof DocumentSymbol) {
				range = ((DocumentSymbol) item).getRange();
			} else if (item instanceof DocumentSymbolWithFile) {
				range = ((DocumentSymbolWithFile) item).symbol.getRange();
			}
			if (targetDocument != null && range != null) {
				try {
					int offset = LSPEclipseUtils.toOffset(range.getStart(), targetDocument);
					int endOffset = LSPEclipseUtils.toOffset(range.getEnd(), targetDocument);
					fTextEditor.selectAndReveal(offset, endOffset - offset);
				} catch (BadLocationException e) {
					LanguageServerPlugin.logError(e);
				}
			}
		});

		viewer.setInput(fSymbols);
		return fFilteredTree;
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);

		shell.setSize(280, 300);
		Control control = fTextEditor.getAdapter(Control.class);
		if (control != null) {
			shell.setLocation(
			        control.toDisplay(control.getBounds().width - shell.getSize().x, control.getLocation().y));
		}
	}

}
