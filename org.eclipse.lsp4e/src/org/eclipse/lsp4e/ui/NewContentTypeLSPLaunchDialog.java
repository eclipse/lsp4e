/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
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
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.core.runtime.content.IContentTypeManager;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.internal.ui.launchConfigurations.LaunchConfigurationTreeContentProvider;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.DecoratingLabelProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.FilteredTree;
import org.eclipse.ui.dialogs.PatternFilter;

public class NewContentTypeLSPLaunchDialog extends Dialog {

	////
	// copied from ContentTypesPreferencePage

	private static class ContentTypesLabelProvider extends LabelProvider {
		@Override
		public String getText(Object element) {
			IContentType contentType = (IContentType) element;
			return contentType.getName();
		}
	}

	private static class ContentTypesContentProvider implements ITreeContentProvider {

		private IContentTypeManager manager;

		@Override
		public Object[] getChildren(Object parentElement) {
			List<IContentType> elements = new ArrayList<>();
			IContentType baseType = (IContentType) parentElement;
			IContentType[] contentTypes = manager.getAllContentTypes();
			for (int i = 0; i < contentTypes.length; i++) {
				IContentType type = contentTypes[i];
				if (Objects.equals(type.getBaseType(), baseType)) {
					elements.add(type);
				}
			}
			return elements.toArray();
		}

		@Override
		public Object getParent(Object element) {
			IContentType contentType = (IContentType) element;
			return contentType.getBaseType();
		}

		@Override
		public boolean hasChildren(Object element) {
			return getChildren(element).length > 0;
		}

		@Override
		public Object[] getElements(Object inputElement) {
			return getChildren(null);
		}

		@Override
		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
			manager = (IContentTypeManager) newInput;
		}
	}

	protected IContentType contentType;
	protected ILaunchConfiguration launchConfig;
	protected Set<String> launchMode;

	//
	////

	protected NewContentTypeLSPLaunchDialog(Shell parentShell) {
		super(parentShell);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite res = (Composite)super.createDialogArea(parent);
		res.setLayout(new GridLayout(2, false));
		new Label(res, SWT.NONE).setText(Messages.NewContentTypeLSPLaunchDialog_associateContentType);
		new Label(res, SWT.NONE).setText(Messages.NewContentTypeLSPLaunchDialog_withLSPLaunch);
		// copied from ContentTypesPreferencePage
		FilteredTree contentTypesFilteredTree = new FilteredTree(res, SWT.BORDER, new PatternFilter(), true, false);
		TreeViewer contentTypesViewer = contentTypesFilteredTree.getViewer();
		contentTypesFilteredTree.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		contentTypesViewer.setContentProvider(new ContentTypesContentProvider());
		contentTypesViewer.setLabelProvider(new ContentTypesLabelProvider());
		contentTypesViewer.setComparator(new ViewerComparator());
		contentTypesViewer.setInput(Platform.getContentTypeManager());
		contentTypesViewer.addSelectionChangedListener(event -> {
			IContentType newContentType = null;
			if (event.getSelection() instanceof IStructuredSelection) {
				IStructuredSelection sel = (IStructuredSelection)event.getSelection();
				if (sel.size() == 1 && sel.getFirstElement() instanceof IContentType) {
					newContentType = (IContentType)sel.getFirstElement();
				}
			}
			contentType = newContentType;
			updateButtons();
		});
		// copied from LaunchConfigurationDialog : todo use LaunchConfigurationFilteredTree
		FilteredTree launchersFilteredTree = new FilteredTree(res, SWT.BORDER, new PatternFilter(), true, false);
		TreeViewer launchConfigViewer = launchersFilteredTree.getViewer();
		launchersFilteredTree.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		launchConfigViewer.setLabelProvider(new DecoratingLabelProvider(DebugUITools.newDebugModelPresentation(), PlatformUI.getWorkbench().getDecoratorManager().getLabelDecorator()));
		launchConfigViewer.setContentProvider(new LaunchConfigurationTreeContentProvider(null, getShell()));
		launchConfigViewer.setInput(DebugPlugin.getDefault().getLaunchManager());
		ComboViewer launchModeViewer = new ComboViewer(res);
		GridData comboGridData = new GridData(SWT.RIGHT, SWT.DEFAULT, true, false, 2, 1);
		comboGridData.widthHint = 100;
		launchModeViewer.getControl().setLayoutData(comboGridData);
		launchModeViewer.setContentProvider(new ArrayContentProvider());
		launchModeViewer.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object o) {
				StringBuilder res = new StringBuilder();
				for (String s : (Set<String>)o) {
					res.append(s);
					res.append(',');
				}
				if (res.length() > 0) {
					res.deleteCharAt(res.length() - 1);
				}
				return res.toString();
			}
		});
		launchConfigViewer.addSelectionChangedListener(event -> {
			ILaunchConfiguration newLaunchConfig = null;
			if (event.getSelection() instanceof IStructuredSelection) {
				IStructuredSelection sel = (IStructuredSelection)event.getSelection();
				if (sel.size() == 1 && sel.getFirstElement() instanceof ILaunchConfiguration) {
					newLaunchConfig = (ILaunchConfiguration)sel.getFirstElement();
				}
			}
			launchConfig = newLaunchConfig;
			updateLaunchModes(launchModeViewer);
			updateButtons();
		});
		launchModeViewer.addSelectionChangedListener(event -> {
			ISelection sel = event.getSelection();
			if (sel.isEmpty()) {
				launchMode = null;
			} else if (sel instanceof IStructuredSelection) {
				launchMode = (Set<String>) ((IStructuredSelection)sel).getFirstElement();
			}
			updateButtons();
		});
		return res;
	}

	@Override
	protected Control createContents(Composite parent) {
		Control res = super.createContents(parent);
		updateButtons();
		return res;
	}

	public IContentType getContentType() {
		return this.contentType;
	}

	public ILaunchConfiguration getLaunchConfiguration() {
		return this.launchConfig;
	}

	private void updateButtons() {
		getButton(OK).setEnabled(contentType != null && launchConfig != null && launchMode != null);
	}

	private void updateLaunchModes(ComboViewer launchModeViewer) {
		if (launchConfig == null) {
			launchModeViewer.setInput(Collections.emptyList());
		} else {
			Set<Set<String>> modes = null;
			try {
				modes = launchConfig.getType().getSupportedModeCombinations();
			} catch (CoreException e) {
				LanguageServerPlugin.getDefault().getLog().log(new Status(IStatus.ERROR, LanguageServerPlugin.getDefault().getBundle().getSymbolicName(), e.getMessage(), e));
			}
			if (modes == null) {
				modes = Collections.singleton(Collections.singleton(ILaunchManager.RUN_MODE));
			}
			launchModeViewer.setInput(modes);
			Object currentMode = null;
			if (!launchModeViewer.getStructuredSelection().isEmpty()) {
				currentMode = launchModeViewer.getStructuredSelection().getFirstElement();
			}
			if (currentMode == null || !modes.contains(currentMode)) {
				launchModeViewer.setSelection(new StructuredSelection());
				currentMode = null;
			}
			if (currentMode == null) {
				for (Set<String> mode : modes) {
					if (mode.size() == 1 && mode.iterator().next().equals(ILaunchManager.RUN_MODE)) {
						currentMode = mode;
						launchModeViewer.setSelection(new StructuredSelection(currentMode));
					}
				}
			}
			if (currentMode == null && !modes.isEmpty()) {
				launchModeViewer.setSelection(new StructuredSelection(modes.iterator().next()));
			}
		}
		updateButtons();
	}

	public @NonNull Set<String> getLaunchMode() {
		return this.launchMode;
	}

}
