/*******************************************************************************
 * Copyright (c) 2016 Rogue Wave Software Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.symbols;

import java.util.List;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IResource;
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
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.SymbolInformation;
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
	private List<? extends SymbolInformation> fSymbols;

	private FilteredTree fFilteredTree;

	public LSPSymbolInFileDialog(Shell parentShell, ITextEditor textEditor, List<? extends SymbolInformation> symbols) {
		super(parentShell, PopupDialog.INFOPOPUP_SHELLSTYLE, true, true, true, false, false, null, null);
		this.fTextEditor = textEditor;
		this.fSymbols = symbols;
		create();

	}

	@Override
	protected Control createDialogArea(Composite parent) {
		fFilteredTree = new FilteredTree(parent, SWT.BORDER, new PatternFilter(), true);
		TreeViewer viewer = fFilteredTree.getViewer();

		viewer.setContentProvider(new SymbolsContentProvider());
		viewer.setLabelProvider(new SymbolsLabelProvider());
		viewer.setUseHashlookup(true);
		viewer.addSelectionChangedListener(event -> {
			IStructuredSelection selection = (IStructuredSelection) event.getSelection();
			if (selection.isEmpty()) {
				return;
			}
			SymbolInformation symbolInformation = (SymbolInformation) selection.getFirstElement();
			Location location = symbolInformation.getLocation();

			IResource targetResource = LSPEclipseUtils.findResourceFor(location.getUri());
			if (targetResource == null) {
				return;
			}
			IDocument targetDocument = FileBuffers.getTextFileBufferManager()
			        .getTextFileBuffer(targetResource.getFullPath(), LocationKind.IFILE).getDocument();
			if (targetDocument != null) {
				try {
					int offset = LSPEclipseUtils.toOffset(location.getRange().getStart(), targetDocument);
					int endOffset = LSPEclipseUtils.toOffset(location.getRange().getEnd(), targetDocument);
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
