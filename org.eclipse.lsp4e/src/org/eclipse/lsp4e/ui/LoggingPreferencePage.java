/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Lucas Bullen (Red Hat Inc.) - initial implementation.
 *  Pierre-Yves B. <pyvesdev@gmail.com> - Bug 553376 - Logs preference page does not work on Windows
 *******************************************************************************/
package org.eclipse.lsp4e.ui;

import static org.eclipse.swt.events.SelectionListener.widgetSelectedAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.CheckboxCellEditor;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.lsp4e.ContentTypeToLSPLaunchConfigEntry;
import org.eclipse.lsp4e.ContentTypeToLanguageServerDefinition;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServersRegistry;
import org.eclipse.lsp4e.LoggingStreamConnectionProviderProxy;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.wizards.datatransfer.SmartImportWizard;

public class LoggingPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

	private final class BooleanMapEditingSupport extends EditingSupport {

		private final Map<String, Boolean> map;

		private BooleanMapEditingSupport(TableViewer viewer,  Map<String, Boolean> map) {
			super(viewer);
			this.map = map;
		}

		@Override
		protected void setValue(Object element, Object value) {
			ContentTypeToLanguageServerDefinition server = (ContentTypeToLanguageServerDefinition)element;
			map.put(server.getValue().id, (Boolean)value);
			hasLoggingBeenChanged = true;
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

	private static final class BooleanMapLabelProvider extends ColumnLabelProvider {
		private final Map<String, Boolean> map;

		private BooleanMapLabelProvider(Map<String, Boolean> map) {
			super();
			this.map = map;
		}

		@Override
		public String getText(Object element) {
			return map.getOrDefault(((ContentTypeToLanguageServerDefinition) element).getValue().id, false)
							? Messages.PreferencePage_enablementCondition_true
							: Messages.PreferencePage_enablementCondition_false;
		}
	}

	private TableViewer languageServerViewer;
	private TableViewer launchConfigurationViewer;
	private final Map<String, Boolean> serverEnableLoggingToFile = new HashMap<>();
	private final Map<String, Boolean> serverEnableLoggingToConsole = new HashMap<>();
	private final IPreferenceStore store = LanguageServerPlugin.getDefault().getPreferenceStore();
	private boolean hasLoggingBeenChanged = false;

	@Override
	public void init(IWorkbench workbench) {
		//nothing to do
	}

	@Override
	protected Control createContents(Composite parent) {
		Composite res = new Composite(parent, SWT.NONE);
		res.setLayout(new GridLayout(1, false));

		createStaticServersTable(res);
		createLaunchConfigurationServersTable(res);
		createLoggingContents(res);
		updateInputs();
		return res;
	}

	private void createStaticServersTable(Composite res) {
		languageServerViewer = new TableViewer(res, SWT.FULL_SELECTION);
		languageServerViewer.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		languageServerViewer.setContentProvider(new ArrayContentProvider());

		TableViewerColumn launchConfigColumn = new TableViewerColumn(languageServerViewer, SWT.NONE);
		launchConfigColumn.getColumn().setText(Messages.PreferencesPage_languageServer);
		launchConfigColumn.getColumn().setWidth(300);
		launchConfigColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return ((ContentTypeToLanguageServerDefinition) element).getValue().label;
			}
		});
		addLoggingColumnsToViewer(languageServerViewer);
		languageServerViewer.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		languageServerViewer.getTable().setHeaderVisible(true);
		languageServerViewer.getTable().setLinesVisible(true);
	}

	private void createLaunchConfigurationServersTable(Composite res) {
		launchConfigurationViewer = new TableViewer(res, SWT.FULL_SELECTION);
		launchConfigurationViewer.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		launchConfigurationViewer.setContentProvider(new ArrayContentProvider());

		TableViewerColumn launchConfigColumn = new TableViewerColumn(launchConfigurationViewer, SWT.NONE);
		launchConfigColumn.getColumn().setText(Messages.PreferencesPage_LaunchConfiguration);
		launchConfigColumn.getColumn().setWidth(300);
		launchConfigColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return ((ContentTypeToLSPLaunchConfigEntry) element).getLaunchConfiguration().getName();
			}
		});
		addLoggingColumnsToViewer(launchConfigurationViewer);
		launchConfigurationViewer.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		launchConfigurationViewer.getTable().setHeaderVisible(true);
		languageServerViewer.getTable().setLinesVisible(true);
	}

	private void addLoggingColumnsToViewer(TableViewer viewer) {
		TableViewerColumn logToFileColumn = new TableViewerColumn(viewer, SWT.NONE);
		logToFileColumn.getColumn().setText(Messages.PreferencesPage_logging_toFile_title);
		logToFileColumn.getColumn().setWidth(100);
		logToFileColumn.setLabelProvider(new BooleanMapLabelProvider(serverEnableLoggingToFile));
		logToFileColumn.setEditingSupport(new BooleanMapEditingSupport(viewer, serverEnableLoggingToFile));

		TableViewerColumn logToConsoleColumn = new TableViewerColumn(viewer, SWT.NONE);
		logToConsoleColumn.getColumn().setText(Messages.PreferencesPage_logging_toConsole_title);
		logToConsoleColumn.getColumn().setWidth(125);
		logToConsoleColumn.setLabelProvider(new BooleanMapLabelProvider(serverEnableLoggingToConsole));
		logToConsoleColumn.setEditingSupport(new BooleanMapEditingSupport(viewer, serverEnableLoggingToConsole));
	}

	private void createLoggingContents(Composite res) {
		Composite loggingComposite = new Composite(res, SWT.NONE);
		loggingComposite.setLayout(new GridLayout(3, false));
		loggingComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));

		Label infoLabel = new Label(loggingComposite, SWT.NONE);
		infoLabel.setText(Messages.preferencesPage_logging_info);
		infoLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 3, 1));
		Link logFolderLabel = new Link(loggingComposite, SWT.NONE);
		logFolderLabel.setText(NLS.bind(Messages.preferencesPage_logging_fileLogsLocation, LoggingStreamConnectionProviderProxy.getLogDirectory()));
		logFolderLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 3, 1));
		logFolderLabel.addSelectionListener(widgetSelectedAdapter(e -> {
			SmartImportWizard importWizard = new SmartImportWizard();
			importWizard.setInitialImportSource(LoggingStreamConnectionProviderProxy.getLogDirectory());
			WizardDialog dialog = new WizardDialog(logFolderLabel.getShell(), importWizard);
			dialog.open();
		}));
		Label fileLoggingLabel = new Label(loggingComposite, SWT.NONE);
		fileLoggingLabel.setText(Messages.PreferencesPage_logging_toFile_description);
		Button disableFileLogging = new Button(loggingComposite, SWT.NONE);
		disableFileLogging.setText(Messages.PreferencePage_enablementCondition_disableAll);
		disableFileLogging.addSelectionListener(widgetSelectedAdapter(e -> {
			serverEnableLoggingToFile.forEach((s, b) -> serverEnableLoggingToFile.put(s, false));
			hasLoggingBeenChanged = true;
			languageServerViewer.refresh();
			launchConfigurationViewer.refresh();
		}));
		Button enableFileLogging = new Button(loggingComposite, SWT.NONE);
		enableFileLogging.setText(Messages.PreferencePage_enablementCondition_enableAll);
		enableFileLogging.addSelectionListener(widgetSelectedAdapter(e -> {
			serverEnableLoggingToFile.forEach((s, b) -> serverEnableLoggingToFile.put(s, true));
			hasLoggingBeenChanged = true;
			languageServerViewer.refresh();
			launchConfigurationViewer.refresh();
		}));

		Label consoleLoggingLabel = new Label(loggingComposite, SWT.NONE);
		consoleLoggingLabel.setText(Messages.PreferencesPage_logging_toConsole_description);
		Button disableConsoleLogging = new Button(loggingComposite, SWT.NONE);
		disableConsoleLogging.setText(Messages.PreferencePage_enablementCondition_disableAll);
		disableConsoleLogging.addSelectionListener(widgetSelectedAdapter(e -> {
			serverEnableLoggingToConsole.forEach((s, b) -> serverEnableLoggingToConsole.put(s, false));
			hasLoggingBeenChanged = true;
			languageServerViewer.refresh();
			launchConfigurationViewer.refresh();
		}));
		Button enableConsoleLogging = new Button(loggingComposite, SWT.NONE);
		enableConsoleLogging.setText(Messages.PreferencePage_enablementCondition_enableAll);
		enableConsoleLogging.addSelectionListener(widgetSelectedAdapter(e -> {
			serverEnableLoggingToConsole.forEach((s, b) -> serverEnableLoggingToConsole.put(s, true));
			hasLoggingBeenChanged = true;
			languageServerViewer.refresh();
			launchConfigurationViewer.refresh();
		}));
	}

	@Override
	protected void performDefaults() {
		serverEnableLoggingToFile.forEach((s, b) -> serverEnableLoggingToFile.put(s,
				store.getBoolean(LoggingStreamConnectionProviderProxy.lsToFileLoggingId(s))));
		serverEnableLoggingToConsole.forEach((s, b) -> serverEnableLoggingToConsole.put(s,
				store.getBoolean(LoggingStreamConnectionProviderProxy.lsToConsoleLoggingId(s))));
		launchConfigurationViewer.refresh();
		languageServerViewer.refresh();
		super.performDefaults();
	}

	private void applyLoggingEnablment() {
		serverEnableLoggingToFile.forEach((s, b) -> store.setValue(LoggingStreamConnectionProviderProxy.lsToFileLoggingId(s), b));
		serverEnableLoggingToConsole.forEach((s, b) -> store.setValue(LoggingStreamConnectionProviderProxy.lsToConsoleLoggingId(s), b));
		hasLoggingBeenChanged = false;
	}

	@Override
	public boolean performOk() {
		if (hasLoggingBeenChanged) {
			applyLoggingEnablment();
			MessageDialog dialog = new MessageDialog(getShell(), Messages.PreferencesPage_restartWarning_title, null,
					Messages.PreferencesPage_restartWarning_message, MessageDialog.WARNING,
					new String[] { IDialogConstants.NO_LABEL, Messages.PreferencesPage_restartWarning_restart }, 1);
			if (dialog.open() == 1) {
				PlatformUI.getWorkbench().restart();
			}
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
		List<ContentTypeToLSPLaunchConfigEntry> contentTypeToLSPLaunchConfigEntries = new ArrayList<>();
		LanguageServersRegistry.getInstance().getContentTypeToLSPLaunches().forEach(o -> {
			String id = o.getValue().id;
			if (languageServerIDs.add(id)) {
				contentTypeToLSPLaunchConfigEntries.add(o);
				serverEnableLoggingToFile.put(id, serverEnableLoggingToFile.getOrDefault(id,
						store.getBoolean(LoggingStreamConnectionProviderProxy.lsToFileLoggingId(id))));
				serverEnableLoggingToConsole.put(id, serverEnableLoggingToConsole.getOrDefault(id,
						store.getBoolean(LoggingStreamConnectionProviderProxy.lsToConsoleLoggingId(id))));
			}
		});

		launchConfigurationViewer.setInput(contentTypeToLSPLaunchConfigEntries);
		launchConfigurationViewer.refresh();

		languageServerIDs.clear();
		List<ContentTypeToLanguageServerDefinition> contentTypeToLanguageServerDefinitions = new ArrayList<>();
		LanguageServersRegistry.getInstance().getContentTypeToLSPExtensions().forEach(o -> {
			String id = o.getValue().id;
			if (languageServerIDs.add(id)) {
				contentTypeToLanguageServerDefinitions.add(o);
				serverEnableLoggingToFile.put(id, serverEnableLoggingToFile.getOrDefault(id,
						store.getBoolean(LoggingStreamConnectionProviderProxy.lsToFileLoggingId(id))));
				serverEnableLoggingToConsole.put(id, serverEnableLoggingToConsole.getOrDefault(id,
						store.getBoolean(LoggingStreamConnectionProviderProxy.lsToConsoleLoggingId(id))));
			}
		});

		languageServerViewer.setInput(contentTypeToLanguageServerDefinitions);
		launchConfigurationViewer.refresh();
	}
}
