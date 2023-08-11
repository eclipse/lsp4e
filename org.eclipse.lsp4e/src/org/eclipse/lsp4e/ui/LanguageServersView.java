/*******************************************************************************
 * Copyright (c) 2023 Vegard IT GmbH and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Sebastian Thomschke (Vegard IT GmbH) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.TableEditor;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.widgets.Widget;
import org.eclipse.ui.part.ViewPart;

public class LanguageServersView extends ViewPart {

	public static final String ID = "org.eclipse.lsp4e.ui.LanguageServersView"; //$NON-NLS-1$

	private static final String EMPTY = ""; //$NON-NLS-1$
	private static final String NOT_AVAILABLE = "n/a"; //$NON-NLS-1$

	private TableViewer viewer;
	private Job viewerRefreshJob;
	private final Map<LanguageServerWrapper, ToolBar> actionButtons = new HashMap<>();
	private final List<ColumnLabelProvider> columnLabelProviders = new ArrayList<>();

	private int tableSortColumn = 1;
	private int tableSortDirection = 1; // 1 = ascending, -1 = descending
	private final ViewerComparator tableSorter = new ViewerComparator() {

		private int compare(int columnIndex, @Nullable final Object e1, @Nullable final Object e2) {
			var labelProvider = columnLabelProviders.get(columnIndex);
			return getComparator().compare( //
					Objects.toString(labelProvider.getText(e1), EMPTY),
					Objects.toString(labelProvider.getText(e2), EMPTY));
		}

		@Override
		public int compare(final Viewer viewer, @Nullable final Object e1, @Nullable final Object e2) {
			int sortResult = compare(tableSortColumn, e1, e2);

			// use the "Initial Project" column as secondary sort column
			if (sortResult == 0 && tableSortColumn != 1) {
				sortResult = compare(1, e1, e2);
			}
			return sortResult * tableSortDirection;
		}
	};

	private void createColumn(String name, int width, ColumnLabelProvider labelProvider) {
		final var viewerColumn = new TableViewerColumn(viewer, SWT.NONE);
		final var tableColumn = viewerColumn.getColumn();
		tableColumn.setText(name);
		tableColumn.setWidth(width);
		tableColumn.setResizable(true);
		final var columnIndex = columnLabelProviders.size();
		tableColumn.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(@Nullable final SelectionEvent e) {
				if (columnIndex == 0) // ignore the column with buttons
					return;
				if (tableSortColumn == columnIndex) {
					tableSortDirection = tableSortDirection * -1;
				}
				tableSortColumn = columnIndex;
				final var table = viewer.getTable();
				table.setSortDirection(tableSortDirection == 1 ? SWT.DOWN : SWT.UP);
				table.setSortColumn(tableColumn);
				viewer.refresh();
			}
		});
		viewerColumn.setLabelProvider(labelProvider);
		columnLabelProviders.add(labelProvider);
	}

	@Override
	public void createPartControl(Composite parent) {
		viewer = new TableViewer(parent, SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER);
		viewer.setComparator(tableSorter);

		final var table = viewer.getTable();
		table.setHeaderVisible(true);
		table.setLinesVisible(true);

		createColumn(EMPTY, 26, new ColumnLabelProvider() {
			@Override
			public void update(ViewerCell cell) {
				final var lsWrapper = (LanguageServerWrapper) cell.getElement();
				final var item = (TableItem) cell.getItem();
				final var buttons = actionButtons.computeIfAbsent(lsWrapper, unused -> {
					final var toolBar = new ToolBar((Composite) cell.getViewerRow().getControl(), SWT.FLAT);
					toolBar.setBackground(cell.getBackground());
					final var terminateButton = new ToolItem(toolBar, SWT.PUSH);
					terminateButton.setImage(LSPImages.getImage(LSPImages.IMG_TERMINATE_CO));
					terminateButton.setToolTipText("Terminate this language server"); //$NON-NLS-1$
					terminateButton.addSelectionListener(new SelectionAdapter() {
						@Override
						public void widgetSelected(SelectionEvent ev) {
							lsWrapper.stop();
							updateViewerInput();
						}
					});
					return toolBar;
				});
				final var editor = new TableEditor(item.getParent());
				editor.setEditor(buttons, item, cell.getColumnIndex());
				editor.grabHorizontal = true;
				editor.grabVertical = true;
				editor.layout();
			}
		});

		createColumn("Initial Project", 150, new ColumnLabelProvider() { //$NON-NLS-1$
			@Override
			public String getText(Object element) {
				final var lsWrapper = (LanguageServerWrapper) element;
				final var p = lsWrapper.initialProject;
				return p == null ? NOT_AVAILABLE : p.getName();
			}
		});

		createColumn("Name", 150, new ColumnLabelProvider() { //$NON-NLS-1$
			@Override
			public String getText(Object element) {
				final var lsWrapper = (LanguageServerWrapper) element;
				return lsWrapper.serverDefinition.label;
			}
		});

		createColumn("Executable", 100, new ColumnLabelProvider() { //$NON-NLS-1$
			@Override
			public String getText(Object element) {
				final ProcessHandle ph = ((LanguageServerWrapper) element).getProcessHandle();
				final String exe = ph == null ? null : ph.info().command().orElse(null);
				if (exe == null)
					return NOT_AVAILABLE;
				int backslash = exe.lastIndexOf('\\');
				int slash = exe.lastIndexOf('/');
				int lastDirSep = Math.max(slash, backslash);
				return exe.substring(lastDirSep + 1);
			}
		});

		createColumn("PID", 50, new ColumnLabelProvider() { //$NON-NLS-1$
			@Override
			public String getText(Object element) {
				final ProcessHandle ph = ((LanguageServerWrapper) element).getProcessHandle();
				if (ph == null)
					return NOT_AVAILABLE;
				try {
					return Long.toString(ph.pid());
				} catch (UnsupportedOperationException ex) {
					return NOT_AVAILABLE;
				}
			}
		});

		createColumn("Command Line", 400, new ColumnLabelProvider() { //$NON-NLS-1$
			@Override
			public String getText(Object element) {
				final ProcessHandle ph = ((LanguageServerWrapper) element).getProcessHandle();
				if (ph == null)
					return NOT_AVAILABLE;
				final ProcessHandle.Info pi = ph.info();
				return pi.commandLine().orElse(pi.command().orElse(NOT_AVAILABLE));
			}
		});

		createColumn("ID", 150, new ColumnLabelProvider() { //$NON-NLS-1$
			@Override
			public String getText(Object element) {
				final var lsWrapper = (LanguageServerWrapper) element;
				return lsWrapper.serverDefinition.id;
			}
		});

		table.setSortDirection(tableSortDirection == 1 ? SWT.DOWN : SWT.UP);
		table.setSortColumn(table.getColumn(tableSortColumn));

		viewer.setContentProvider(new ArrayContentProvider());

		scheduleRefreshJob();
	}

	@Override
	public void dispose() {
		final var viewerRefreshJob = this.viewerRefreshJob;
		if (viewerRefreshJob != null)
			viewerRefreshJob.cancel();
		super.dispose();
	}

	private void scheduleRefreshJob() {
		viewerRefreshJob = new Job("Refresh Language Server Processes view") { //$NON-NLS-1$
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				if (getSite().getPage().isPartVisible(LanguageServersView.this)) {
					updateViewerInput();
				}
				schedule(2000);
				return Status.OK_STATUS;
			}
		};
		viewerRefreshJob.setPriority(Job.DECORATE);
		viewerRefreshJob.setSystem(true);
		viewerRefreshJob.schedule();
	}

	@Override
	public void setFocus() {
		viewer.getControl().setFocus();
	}

	private void updateViewerInput() {
		final var currentElements = (Object[]) viewer.getInput();
		final var newElements = LanguageServiceAccessor.getStartedWrappers(null, capability -> true, true).toArray();
		if (!Arrays.equals(currentElements, newElements)) {
			UI.getDisplay().execute(() -> {
				actionButtons.values().forEach(Widget::dispose);
				actionButtons.clear();
				viewer.setInput(newElements);
			});
		}
	}
}
