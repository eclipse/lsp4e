/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.languageserver.ui;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.languageserver.ContentTypeToLSPLaunchConfigEntry;
import org.eclipse.languageserver.LSPStreamConnectionProviderRegistry;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

public class LanguageServerPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

	private LSPStreamConnectionProviderRegistry registry;
	private List<ContentTypeToLSPLaunchConfigEntry> workingCopy;
	private Button removeButton;
	private TableViewer viewer;

	public LanguageServerPreferencePage() {
	}

	public LanguageServerPreferencePage(String title) {
		super(title);
	}

	public LanguageServerPreferencePage(String title, ImageDescriptor image) {
		super(title, image);
	}

	@Override
	public void init(IWorkbench workbench) {
		this.registry = LSPStreamConnectionProviderRegistry.getInstance();
	}

	@Override
	protected Control createContents(Composite parent) {
		Composite res = new Composite(parent, SWT.NONE);
		res.setLayout(new GridLayout(2, false));
		Label intro = new Label(res, SWT.WRAP);
		GridData introLayoutData = new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1);
		introLayoutData.widthHint = 400;
		intro.setLayoutData(introLayoutData);
		intro.setText(Messages.PreferencesPage_Intro);
		viewer = new TableViewer(res);
		viewer.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		viewer.setContentProvider(new ArrayContentProvider());
		workingCopy = new ArrayList<>();
		workingCopy.addAll(LSPStreamConnectionProviderRegistry.getInstance().getContentTypeToLSPLaunches());
		TableViewerColumn contentTypeColumn = new TableViewerColumn(viewer, SWT.NONE);
		contentTypeColumn.getColumn().setText(Messages.PreferencesPage_contentType);
		contentTypeColumn.getColumn().setWidth(200);
		contentTypeColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return ((ContentTypeToLSPLaunchConfigEntry)element).getContentType().getName();
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
					workingCopy.add(new ContentTypeToLSPLaunchConfigEntry(dialog.getContentType(), dialog.getLaunchConfiguration(), dialog.getLaunchMode()));
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
						workingCopy.remove((ContentTypeToLSPLaunchConfigEntry)item);
					}
					viewer.refresh();
				}
			}
		});
		viewer.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				updateButtons();
			}
		});
		viewer.setInput(workingCopy);
		updateButtons();
		return res;
	}
	
	protected void updateButtons() {
		this.removeButton.setEnabled(!this.viewer.getSelection().isEmpty());
	}

	@Override
	public boolean performOk() {
		this.registry.setAssociations(this.workingCopy);
		return super.performOk();
	}

}
