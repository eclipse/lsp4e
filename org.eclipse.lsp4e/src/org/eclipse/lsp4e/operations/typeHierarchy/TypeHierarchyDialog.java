/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.operations.typeHierarchy;


import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.dialogs.PopupDialog;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServersRegistry.LanguageServerDefinition;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.FilteredTree;
import org.eclipse.ui.dialogs.PatternFilter;
import org.eclipse.ui.navigator.CommonViewerSorter;

public class TypeHierarchyDialog extends PopupDialog {


	private final LanguageServerDefinition lsDefinition;
	private final @NonNull IDocument document;
	private final ITextSelection textSelection;

	public TypeHierarchyDialog(@NonNull Shell parentShell, ITextSelection textSelection, @NonNull IDocument document, @NonNull LanguageServerDefinition ls) {
		super(parentShell, PopupDialog.INFOPOPUPRESIZE_SHELLSTYLE, true, true, true, false, false, null, null);
		this.lsDefinition = ls;
		this.document = document;
		this.textSelection = textSelection;
		create();
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		final var filteredTree = new FilteredTree(parent, SWT.BORDER, new PatternFilter(), true, false);
		TreeViewer viewer = filteredTree.getViewer();
		viewer.setContentProvider(new TypeHierarchyContentProvider(lsDefinition, document));
		// Maybe consider making this a CNF defined label provider
		viewer.setLabelProvider(new TypeHierarchyItemLabelProvider());
		viewer.addDoubleClickListener(event -> {
			TypeHierarchyItem item = (TypeHierarchyItem)((IStructuredSelection)event.getSelection()).getFirstElement();
			LSPEclipseUtils.open(item.getUri(), item.getSelectionRange());
		});

		final var sorter = new CommonViewerSorter();
		viewer.setComparator(sorter);

		viewer.setUseHashlookup(true);

		viewer.setInput(textSelection);
		return filteredTree;
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText(Messages.typeHierarchy);
		shell.setSize(280, 300);
	}
}


