/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.ui;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.lsp4e.ContentTypeToLSPLaunchConfigEntry;
import org.eclipse.lsp4e.ContentTypeToLanguageServerDefinition;
import org.eclipse.lsp4e.LanguageServersRegistry;
import org.eclipse.lsp4e.enablement.EnablementTester;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Link;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.preferences.IWorkbenchPreferenceContainer;

public class LanguageServerPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

	private LanguageServersRegistry registry;
	private List<ContentTypeToLSPLaunchConfigEntry> workingCopy;
	private Button removeButton;
	private CheckboxTableViewer checkboxViewer;
	private TableViewer viewer;
	private final SelectionAdapter contentTypeLinkListener;
	private List<ContentTypeToLanguageServerDefinition> changedDefinitions;

	public LanguageServerPreferencePage() {

		contentTypeLinkListener = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (getContainer() instanceof IWorkbenchPreferenceContainer) {
					((IWorkbenchPreferenceContainer)getContainer()).openPage("org.eclipse.ui.preferencePages.ContentTypes", null); //$NON-NLS-1$
				}
			}
		};
	}

	@Override
	public void init(IWorkbench workbench) {
		this.changedDefinitions = new ArrayList<>();
		this.registry = LanguageServersRegistry.getInstance();
	}

	@Override
	public void setVisible(boolean visible) {
		super.setVisible(visible);
		if (visible) {
			this.checkboxViewer.refresh();
		}
	}

	@Override
	protected Control createContents(Composite parent) {
		Composite res = new Composite(parent, SWT.NONE);
		res.setLayout(new GridLayout(2, false));
		Link intro = new Link(res, SWT.WRAP);
		GridDataFactory.swtDefaults().align(SWT.FILL, SWT.TOP).grab(true, false).span(2, 1).hint(400, SWT.DEFAULT).applyTo(intro);
		intro.setText(Messages.PreferencesPage_Intro);
		intro.addSelectionListener(this.contentTypeLinkListener);

		createStaticServersTable(res);

		Link manualServersIntro = new Link(res, SWT.WRAP);
		GridDataFactory.swtDefaults().align(SWT.FILL, SWT.TOP).grab(true, false).span(2, 1).hint(400, SWT.DEFAULT).applyTo(manualServersIntro);
		manualServersIntro.setText(Messages.PreferencesPage_manualServers);
		manualServersIntro.addSelectionListener(this.contentTypeLinkListener);
		viewer = new TableViewer(res);
		viewer.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		viewer.setContentProvider(new ArrayContentProvider());
		workingCopy = new ArrayList<>();
		workingCopy.addAll(LanguageServersRegistry.getInstance().getContentTypeToLSPLaunches());
		TableViewerColumn contentTypeColumn = new TableViewerColumn(viewer, SWT.NONE);
		contentTypeColumn.getColumn().setText(Messages.PreferencesPage_contentType);
		contentTypeColumn.getColumn().setWidth(200);
		contentTypeColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return ((ContentTypeToLanguageServerDefinition)element).getKey().getName();
			}
		});
		TableViewerColumn launchConfigColumn = new TableViewerColumn(viewer, SWT.NONE);
		launchConfigColumn.getColumn().setText(Messages.PreferencesPage_LaunchConfiguration);
		launchConfigColumn.getColumn().setWidth(300);
		launchConfigColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return ((ContentTypeToLSPLaunchConfigEntry)element).getLaunchConfiguration().getName();
			}
		});
		TableViewerColumn launchModeColumn = new TableViewerColumn(viewer, SWT.NONE);
		launchModeColumn.getColumn().setText(Messages.PreferencesPage_LaunchMode);
		launchModeColumn.getColumn().setWidth(100);
		launchModeColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				StringBuilder res = new StringBuilder();
				for (String s : ((ContentTypeToLSPLaunchConfigEntry)element).getLaunchModes()) {
					res.append(s);
					res.append(',');
				}
				if (res.length() > 0) {
					res.deleteCharAt(res.length() - 1);
				}
				return res.toString();
			}
		});
		viewer.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		viewer.getTable().setHeaderVisible(true);
		Composite buttonComposite = new Composite(res, SWT.NONE);
		buttonComposite.setLayout(new GridLayout(1, false));
		Button addButton = new Button(buttonComposite, SWT.PUSH);
		addButton.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));
		addButton.setText(Messages.PreferencesPage_Add);
		addButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				NewContentTypeLSPLaunchDialog dialog = new NewContentTypeLSPLaunchDialog(getShell());
				if (dialog.open() == IDialogConstants.OK_ID) {
					workingCopy.add(new ContentTypeToLSPLaunchConfigEntry(dialog.getContentType(),
							dialog.getLaunchConfiguration(), dialog.getLaunchMode()));
					viewer.refresh();
				}
				super.widgetSelected(e);
			}
		});
		removeButton = new Button(buttonComposite, SWT.PUSH);
		removeButton.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));
		removeButton.setText(Messages.PreferencesPage_Remove);
		removeButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ISelection sel = viewer.getSelection();
				if (!sel.isEmpty() && sel instanceof IStructuredSelection) {
					for (Object item : ((IStructuredSelection)sel).toArray()) {
						workingCopy.remove(item);
					}
					viewer.refresh();
				}
			}
		});
		viewer.addSelectionChangedListener(event -> updateButtons());
		viewer.setInput(workingCopy);
		updateButtons();
		return res;
	}

	private void createStaticServersTable(Composite res) {
		Link staticServersIntro = new Link(res, SWT.WRAP);
		GridDataFactory.swtDefaults().align(SWT.FILL, SWT.TOP).grab(true, false).span(2, 1).hint(400, SWT.DEFAULT).applyTo(staticServersIntro);
		staticServersIntro.setText(Messages.PreferencesPage_staticServers);
		staticServersIntro.addSelectionListener(this.contentTypeLinkListener);
		checkboxViewer = CheckboxTableViewer.newCheckList(res, SWT.NONE);
		checkboxViewer.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		checkboxViewer.setContentProvider(new ArrayContentProvider());

		TableViewerColumn enablementColumn = new TableViewerColumn(checkboxViewer, SWT.NONE);
		enablementColumn.getColumn().setText(Messages.PreferencesPage_Enabled);
		enablementColumn.getColumn().setWidth(70);
		enablementColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return null;
			}
		});

		TableViewerColumn contentTypeColumn = new TableViewerColumn(checkboxViewer, SWT.NONE);
		contentTypeColumn.getColumn().setText(Messages.PreferencesPage_contentType);
		contentTypeColumn.getColumn().setWidth(200);
		contentTypeColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return ((ContentTypeToLanguageServerDefinition)element).getKey().getName();
			}
		});

		TableViewerColumn launchConfigColumn = new TableViewerColumn(checkboxViewer, SWT.NONE);
		launchConfigColumn.getColumn().setText(Messages.PreferencesPage_languageServer);
		launchConfigColumn.getColumn().setWidth(300);
		launchConfigColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return ((ContentTypeToLanguageServerDefinition)element).getValue().label;
			}
		});


		List<ContentTypeToLanguageServerDefinition> contentTypeToLanguageServerDefinitions = registry.getContentTypeToLSPExtensions();
		if (contentTypeToLanguageServerDefinitions.stream()
				.anyMatch(definition -> definition.getEnablementCondition() != null)) {

			TableViewerColumn conditionColumn = new TableViewerColumn(checkboxViewer, SWT.NONE);
			conditionColumn.getColumn().setText(Messages.PreferencesPage_enablementCondition);
			conditionColumn.getColumn().setWidth(150);
			conditionColumn.setLabelProvider(new ColumnLabelProvider() {
				@Override
				public String getText(Object element) {
					EnablementTester tester = ((ContentTypeToLanguageServerDefinition) element)
							.getEnablementCondition();

					if(tester == null) {
						// table does not support mnemonic
						return Action.removeMnemonics(IDialogConstants.NO_LABEL);

					}
					String extensionStatus = ((ContentTypeToLanguageServerDefinition) element).isExtensionEnabled()
							? Messages.PreferencePage_enablementCondition_true
							: Messages.PreferencePage_enablementCondition_false;
					return tester.getDescription() + " (" + extensionStatus + ")"; //$NON-NLS-1$ //$NON-NLS-2$
				}

				@Override
				public Color getBackground(Object element) {
					EnablementTester tester = ((ContentTypeToLanguageServerDefinition) element)
							.getEnablementCondition();
					if (tester == null) {
						return null;
					}
					Color red = Display.getDefault().getSystemColor(SWT.COLOR_RED);
					Color green = Display.getDefault().getSystemColor(SWT.COLOR_GREEN);
					return ((ContentTypeToLanguageServerDefinition) element).isExtensionEnabled() ? green : red;
				}
			});
		}

		checkboxViewer.setInput(contentTypeToLanguageServerDefinitions);
		checkboxViewer.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
		checkboxViewer.getTable().setHeaderVisible(true);
		checkboxViewer.getTable().setLinesVisible(true);

		this.checkboxViewer.setCheckedElements(contentTypeToLanguageServerDefinitions.stream()
				.filter(ContentTypeToLanguageServerDefinition::isUserEnabled).toArray());

		checkboxViewer.addCheckStateListener(event -> {
			if (event.getElement() instanceof ContentTypeToLanguageServerDefinition) {
				ContentTypeToLanguageServerDefinition contentTypeToLanguageServerDefinition = (ContentTypeToLanguageServerDefinition) event
						.getElement();
				contentTypeToLanguageServerDefinition.setUserEnabled(event.getChecked());
				changedDefinitions.add(contentTypeToLanguageServerDefinition);
			}
		});
	}

	protected void updateButtons() {
		this.removeButton.setEnabled(!this.viewer.getSelection().isEmpty());
	}

	@Override
	public boolean performOk() {
		this.registry.setAssociations(this.workingCopy);
		EnableDisableLSJob enableDisableLSJob = new EnableDisableLSJob(changedDefinitions, getEditors());
		enableDisableLSJob.schedule();
		return super.performOk();
	}

	private IEditorReference[] getEditors() {
		var page = UI.getActivePage();
		if (page != null) {
			return page.getEditorReferences();
		}
		return null;
	}

}
