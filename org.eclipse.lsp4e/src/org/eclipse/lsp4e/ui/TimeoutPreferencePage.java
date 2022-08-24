/*******************************************************************************
 * Copyright (c) 2022 Avaloq Evolution AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Rubén Porras Campo (Avaloq Evolution AG) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.CheckboxCellEditor;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.lsp4e.ContentTypeToLanguageServerDefinition;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServersRegistry;
import org.eclipse.lsp4e.Timeouts;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

public class TimeoutPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

	private final class IntegerMapEditingSupport extends EditingSupport {

		private final Map<String, Integer> map;

		private IntegerMapEditingSupport(TableViewer viewer,  Map<String, Integer> map) {
			super(viewer);
			this.map = map;
		}

		@Override
		protected void setValue(Object element, Object value) {
			ContentTypeToLanguageServerDefinition server = (ContentTypeToLanguageServerDefinition)element;
			map.put(server.getValue().id, (Integer)value);
			hasTimeoutBeenChanged = true;
			getViewer().refresh(element);
		}

		@Override
		protected Object getValue(Object element) {
			ContentTypeToLanguageServerDefinition server = (ContentTypeToLanguageServerDefinition)element;
			return map.get(server.getValue().id);
		}

		@Override
		protected CellEditor getCellEditor(Object element) {
			return new CheckboxCellEditor();
		}

		@Override
		protected boolean canEdit(Object element) {
			return true;
		}
	}

	private static final class IntegerMapLabelProvider extends ColumnLabelProvider {
		private final Map<String, Integer> map;

		private IntegerMapLabelProvider(Map<String, Integer> map) {
			super();
			this.map = map;
		}

		@Override
		public String getText(Object element) {
			return map.get(((ContentTypeToLanguageServerDefinition) element).getValue().id).toString();
		}
	}

	private TableViewer languageServerViewer;
	private final Map<String, Integer> serverTimeout = new HashMap<>();
	private final IPreferenceStore store = LanguageServerPlugin.getDefault().getPreferenceStore();
	private boolean hasTimeoutBeenChanged = false;

	@Override
	public void init(IWorkbench workbench) {
		//nothing to do
	}

	@Override
	protected Control createContents(Composite parent) {
		Composite res = new Composite(parent, SWT.NONE);
		res.setLayout(new GridLayout(1, false));

		createStaticServersTable(res);
		updateInputs();
		return res;
	}

	private void createStaticServersTable(Composite res) {
		languageServerViewer = new TableViewer(res, SWT.FULL_SELECTION);
		languageServerViewer.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		languageServerViewer.setContentProvider(new ArrayContentProvider());

		TableViewerColumn launchConfigColumn = new TableViewerColumn(languageServerViewer, SWT.NONE);
		launchConfigColumn.getColumn().setText(Messages.PreferencesPage_timeout);
		launchConfigColumn.getColumn().setWidth(300);
		launchConfigColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return ((ContentTypeToLanguageServerDefinition) element).getValue().label;
			}
		});
		addOnSaveTimeoutColumnsToViewer(languageServerViewer);
		languageServerViewer.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		languageServerViewer.getTable().setHeaderVisible(true);
		languageServerViewer.getTable().setLinesVisible(true);
	}

	private void addOnSaveTimeoutColumnsToViewer(TableViewer viewer) {
		TableViewerColumn timeoutColumn = new TableViewerColumn(viewer, SWT.NONE);
		timeoutColumn.getColumn().setText(Messages.PreferencesPage_willSaveWaitUntilTimeout);
		timeoutColumn.getColumn().setWidth(100);
		timeoutColumn.setLabelProvider(new IntegerMapLabelProvider(serverTimeout));
		timeoutColumn.setEditingSupport(new IntegerMapEditingSupport(viewer, serverTimeout));
	}

	@Override
	protected void performDefaults() {
		serverTimeout.forEach((s, b) -> serverTimeout.put(s, Timeouts.lsToWillSaveWaitUntilTimeout(store, s)));
		languageServerViewer.refresh();
		super.performDefaults();
	}

	private void applyTimeoutChange() {
		serverTimeout.forEach((s, b) -> store.setValue(Timeouts.lsToWillSaveWaitUntilTimeoutKey(s), b));
		hasTimeoutBeenChanged = false;
	}

	@Override
	public boolean performOk() {
		if (hasTimeoutBeenChanged) {
			applyTimeoutChange();
		}
		return super.performOk();
	}

	@Override
	public void setVisible(boolean visible) {
		super.setVisible(visible);
		updateInputs();
	}

	private void updateInputs() {
		Set<String> languageServerIDs = new HashSet<>();
		List<ContentTypeToLanguageServerDefinition> contentTypeToLanguageServerDefinitions = new ArrayList<>();
		LanguageServersRegistry.getInstance().getContentTypeToLSPExtensions().forEach(o -> {
			String serverId = o.getValue().id;
			if (languageServerIDs.add(serverId)) {
				contentTypeToLanguageServerDefinitions.add(o);
				serverTimeout.put(serverId, serverTimeout.getOrDefault(serverId,
						Timeouts.lsToWillSaveWaitUntilTimeout(store, serverId)));
			}
		});

		languageServerViewer.setInput(contentTypeToLanguageServerDefinitions);
	}
}
