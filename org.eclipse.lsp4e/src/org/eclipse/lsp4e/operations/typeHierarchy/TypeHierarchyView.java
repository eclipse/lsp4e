/*******************************************************************************
 * Copyright (c) 2023 Bachmann electronic GmbH and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * Gesa Hentschke (Bachmann electronic GmbH) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.typeHierarchy;

import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.callhierarchy.CallHierarchyView;
import org.eclipse.lsp4e.ui.LSPImages;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.ui.dialogs.FilteredTree;
import org.eclipse.ui.dialogs.PatternFilter;

public class TypeHierarchyView extends CallHierarchyView {
	public static final String ID = "org.eclipse.lsp4e.operations.typeHierarchy.TypeHierarchyView"; //$NON-NLS-1$
	TypeHierarchyViewContentProvider contentProvider = new TypeHierarchyViewContentProvider();

	@Override
	public void createPartControl(Composite parent) {
		final var filteredTree = new FilteredTree(parent, SWT.BORDER, new PatternFilter(), true, false) {
			@Override
			protected Composite createFilterControls(Composite parent) {
				Composite composite = new Composite(parent, SWT.NONE);
				GridLayout layout = new GridLayout(2, false);
				layout.horizontalSpacing=0;
				layout.marginWidth=0;
				layout.marginHeight=0;
				composite.setLayout(layout);

				Composite filterControls = super.createFilterControls(composite);
				filterControls.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));

				createToolBar(composite);

				return composite;
			}

			private void createToolBar(Composite composite) {
				ToolBar toolbar = new ToolBar(composite, org.eclipse.jface.dialogs.PopupDialog.HOVER_SHELLSTYLE);
				ToolItem hierchyModeItem = new ToolItem(toolbar, SWT.PUSH);
				updateHierarchyModeItem(hierchyModeItem, contentProvider.showSuperTypes);

				hierchyModeItem.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						contentProvider.showSuperTypes = !contentProvider.showSuperTypes;
						updateHierarchyModeItem(hierchyModeItem, contentProvider.showSuperTypes);
						getViewer().refresh();
					}
				});
			}

			private void updateHierarchyModeItem(ToolItem hierchyModeItem, boolean showSuperTypes) {
				hierchyModeItem.setImage(LSPImages.getImage(showSuperTypes ? LSPImages.IMG_SUBTYPE : LSPImages.IMG_SUPERTYPE));
				hierchyModeItem.setToolTipText(showSuperTypes ? Messages.typeHierarchy_show_subtypes : Messages.typeHierarchy_show_supertypes);
			}
		};
		treeViewer = filteredTree.getViewer();
		treeViewer.setContentProvider(contentProvider);

		treeViewer.setLabelProvider(new TypeHierarchyItemLabelProvider());

		treeViewer.setUseHashlookup(true);
		treeViewer.getControl().setEnabled(false);
		treeViewer.addDoubleClickListener(new IDoubleClickListener() {
			@Override
			public void doubleClick(final DoubleClickEvent event) {
				TypeHierarchyItem item = (TypeHierarchyItem)((IStructuredSelection)event.getSelection()).getFirstElement();
				LSPEclipseUtils.open(item.getUri(), item.getSelectionRange());
			}
		});

	}

}
