/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Lucas Bullen (Red Hat Inc.) - Bug 520053 - Clicking nodes in the 'Outline' should navigate
 *******************************************************************************/
package org.eclipse.lsp4e.outline;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.viewers.IOpenListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.OpenEvent;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.lsp4e.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.navigator.CommonViewer;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

public class CNFOutlinePage implements IContentOutlinePage {

	private static final String ID = "org.eclipse.lsp4e.outline"; //$NON-NLS-1$
	private CommonViewer viewer;
	private LSPDocumentInfo info;
	private ITextEditor textEditor;

	public CNFOutlinePage(LSPDocumentInfo info, @Nullable ITextEditor textEditor) {
		this.textEditor = textEditor;
		this.info = info;
	}

	@Override
	public void createControl(Composite parent) {
		this.viewer = new CommonViewer(ID, parent, SWT.NONE);
		this.viewer.setInput(info);
		if(textEditor != null) {
			this.viewer.addOpenListener(new IOpenListener() {
				@Override
				public void open(OpenEvent event) {
					textEditor.setFocus();
				}
			});
			this.viewer.addSelectionChangedListener(new ISelectionChangedListener() {
				@Override
				public void selectionChanged(SelectionChangedEvent event) {
					if(viewer.getTree().isFocusControl() && viewer.getSelection() != null) {
						Object selection = ((TreeSelection)viewer.getSelection()).getFirstElement();
						if (selection != null && selection instanceof SymbolInformation) {
							Range range = ((SymbolInformation)selection).getLocation().getRange();
							try {
								int startLineOffest = info.getDocument().getLineOffset(range.getStart().getLine());
								textEditor.selectAndReveal(startLineOffest + range.getStart().getCharacter(), 0);
							} catch (BadLocationException e) {
								return;
							}
						}
					}
				}
			});
		}
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
