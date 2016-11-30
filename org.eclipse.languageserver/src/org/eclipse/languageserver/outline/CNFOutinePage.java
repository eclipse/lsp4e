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
package org.eclipse.languageserver.outline;

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.languageserver.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.navigator.CommonViewer;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

public class CNFOutinePage implements IContentOutlinePage {
	
	private static final String ID = "org.eclipse.languageserver.outline"; //$NON-NLS-1$
	private CommonViewer viewer;
	private LSPDocumentInfo info;
	
	public CNFOutinePage(LSPDocumentInfo info) {
		this.info = info;
	}

	@Override
	public void createControl(Composite parent) {
		this.viewer = new CommonViewer(ID, parent, SWT.NONE);
		this.viewer.setInput(info);
	}

	@Override
	public void dispose() {
		this.viewer.dispose();
	}

	@Override
	public Control getControl() {
		return this.viewer.getControl();
	}

	@Override
	public void setActionBars(IActionBars actionBars) {
//		IMenuManager viewMenuManager= actionBars.getMenuManager();
//		fToggleLinkingAction= new ToggleLinkingAction();
//		fToggleLinkingAction.setActionDefinitionId(IWorkbenchCommandConstants.NAVIGATE_TOGGLE_LINK_WITH_EDITOR);
//		viewMenuManager.add(fToggleLinkingAction);
	}

	@Override
	public void setFocus() {
		this.viewer.getTree().setFocus();
	}

	@Override
	public void addSelectionChangedListener(ISelectionChangedListener listener) {
		this.viewer.addSelectionChangedListener(listener);
	}

	@Override
	public ISelection getSelection() {
		return this.viewer.getSelection();
	}

	@Override
	public void removeSelectionChangedListener(ISelectionChangedListener listener) {
		this.viewer.removeSelectionChangedListener(listener);
	}

	@Override
	public void setSelection(ISelection selection) {
		this.viewer.setSelection(selection);
	}

}
