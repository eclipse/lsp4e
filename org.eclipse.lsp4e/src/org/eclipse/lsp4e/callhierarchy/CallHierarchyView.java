/*******************************************************************************
 * Copyright (c) 2022 Avaloq Group AG (http://www.avaloq.com).
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Andrew Lamb (Avaloq Group AG) - Initial implementation
 *******************************************************************************/

package org.eclipse.lsp4e.callhierarchy;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.part.ViewPart;

/**
 * An LSP based CallHierarchyView.
 */
public class CallHierarchyView extends ViewPart {
	public static final String ID = "org.eclipse.lsp4e.callHierarchy.callHierarchyView"; //$NON-NLS-1$

	private TreeViewer treeViewer;

	private final CallHierarchyContentProvider contentProvider = new CallHierarchyContentProvider();

	@Override
	public void createPartControl(final Composite parent) {
		// Create the tree viewer as a child of the composite parent
		treeViewer = new TreeViewer(parent);
		treeViewer.setContentProvider(contentProvider);

		treeViewer.setLabelProvider(new DelegatingStyledCellLabelProvider(new CallHierarchyLabelProvider()));

		treeViewer.setUseHashlookup(true);
		treeViewer.getControl().setEnabled(false);
		treeViewer.addDoubleClickListener(new IDoubleClickListener() {

			@SuppressWarnings("unchecked")
			@Override
			public void doubleClick(final DoubleClickEvent event) {
				if (event.getSelection() instanceof IStructuredSelection structuredSelection) {
					structuredSelection.iterator().forEachRemaining(selectedObject -> {
						if (selectedObject instanceof CallHierarchyViewTreeNode selectedNode) {
							CallHierarchyItem callContainer = selectedNode.getCallContainer();
							LSPEclipseUtils.open(callContainer.getUri(), selectedNode.getSelectionRange(), null);
						}
					});
				}
			}
		});
	}

	@Override
	public void setFocus() {
		treeViewer.getControl().setFocus();
	}

	/**
	 * Initialise this view with the call hierarchy for the specified selection.
	 *
	 * @param document
	 *            the document containing the current selection.
	 * @param offset
	 *            the offset into the document of the current selection.
	 */
	public void initialize(final IDocument document, final int offset) {
		CallHierarchyViewInput viewInput = new CallHierarchyViewInput(document, offset);
		treeViewer.setInput(viewInput);
	}
}
