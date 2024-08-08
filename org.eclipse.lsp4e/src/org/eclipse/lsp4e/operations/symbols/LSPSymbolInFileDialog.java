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
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.dialogs.PopupDialog;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.outline.CNFOutlinePage;
import org.eclipse.lsp4e.outline.LSSymbolsContentProvider;
import org.eclipse.lsp4e.outline.LSSymbolsContentProvider.OutlineViewerInput;
import org.eclipse.lsp4e.outline.SymbolsModel.DocumentSymbolWithURI;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.dialogs.FilteredTree;
import org.eclipse.ui.dialogs.PatternFilter;
import org.eclipse.ui.internal.navigator.NavigatorContentService;
import org.eclipse.ui.internal.navigator.NavigatorDecoratingLabelProvider;
import org.eclipse.ui.navigator.CommonViewerSorter;
import org.eclipse.ui.texteditor.ITextEditor;

public class LSPSymbolInFileDialog extends PopupDialog {

	private final OutlineViewerInput outlineViewerInput;
	private final ITextEditor textEditor;
	private @Nullable TreeViewer viewer;

	public LSPSymbolInFileDialog(Shell parentShell, ITextEditor textEditor,
			IDocument document, LanguageServerWrapper wrapper) {
		super(parentShell, PopupDialog.INFOPOPUPRESIZE_SHELLSTYLE, true, true, true, false, false, null, null);
		outlineViewerInput = new OutlineViewerInput(document, wrapper, textEditor);
		this.textEditor = textEditor;
		create();
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		IFile documentFile = outlineViewerInput.documentFile;
		getShell().setText(NLS.bind(Messages.symbolsInFile, documentFile == null ? null : documentFile.getName()));

		final var filteredTree = new FilteredTree(parent, SWT.BORDER, new PatternFilter(), true, false);
		final TreeViewer viewer = this.viewer = filteredTree.getViewer();
		viewer.setData(LSSymbolsContentProvider.VIEWER_PROPERTY_IS_QUICK_OUTLINE, Boolean.TRUE);

		final var contentService = new NavigatorContentService(CNFOutlinePage.ID, viewer);
		filteredTree.addDisposeListener(ev -> contentService.dispose());
		final var contentProvider = contentService.createCommonContentProvider();
		viewer.setContentProvider(contentProvider);

		final var sorter = new CommonViewerSorter();
		sorter.setContentService(contentService);
		viewer.setComparator(sorter);

		viewer.setLabelProvider(new NavigatorDecoratingLabelProvider(contentService.createCommonLabelProvider()));
		viewer.setUseHashlookup(true);

		final Tree tree = viewer.getTree();

		tree.addKeyListener(new KeyListener() {
			@Override
			public void keyPressed(KeyEvent e)  {
				if (e.character == SWT.ESC) {
					close();
				}
			}
			@Override
			public void keyReleased(KeyEvent e) {
				// do nothing
			}
		});

		// listen to enter key or other platform-specific default selection events
		tree.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				// do nothing
			}
			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				gotoSelectedElement();
			}
		});

		// listen to left mouse button's single click
		tree.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseUp(MouseEvent e) {
				if (tree.getSelectionCount() < 1 || e.button != 1) {
					return;
				}

				if (tree.equals(e.getSource())) {
					TreeItem selection= tree.getSelection()[0];
					if (selection.equals(tree.getItem(new Point(e.x, e.y)))) {
						gotoSelectedElement();
					}
				}
			}
		});

		getShell().addDisposeListener(event -> this.viewer = null);

		viewer.setInput(outlineViewerInput);
		return filteredTree;
	}

	private void gotoSelectedElement() {
		Object selectedElement = getSelectedElement();

		if (selectedElement != null) {
			close();

			Range range = null;
			if (selectedElement instanceof SymbolInformation symbolInformation) {
				range = symbolInformation.getLocation().getRange();
			} else if (selectedElement instanceof DocumentSymbol documentSymbol) {
				range = documentSymbol.getSelectionRange();
			} else if (selectedElement instanceof DocumentSymbolWithURI documentSymbolWithFile) {
				range = documentSymbolWithFile.symbol.getSelectionRange();
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
		}
	}

	private @Nullable Object getSelectedElement() {
		TreeViewer treeViewer = this.viewer;

		if (treeViewer == null) {
			return null;
		}

		IStructuredSelection selection = treeViewer.getStructuredSelection();
		if (selection == null) {
			return null;
		}

		Object selectedElement = selection.getFirstElement();

		if (selectedElement instanceof Either<?, ?> either) {
			selectedElement = either.get();
		}

		return selectedElement;
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
