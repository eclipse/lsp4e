/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
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

import org.eclipse.core.runtime.Adapters;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.IOpenListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.OpenEvent;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.lsp4e.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CaretEvent;
import org.eclipse.swt.custom.CaretListener;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchCommandConstants;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.navigator.CommonViewer;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;
import org.osgi.service.prefs.BackingStoreException;

public class CNFOutlinePage implements IContentOutlinePage {

	public static final String ID = "org.eclipse.lsp4e.outline"; //$NON-NLS-1$
	public static final String linkWithEditorPreference = "linkWithEditor"; //$NON-NLS-1$
	private CommonViewer viewer;
	private IEclipsePreferences preferences;
	private LSPDocumentInfo info;
	private ITextEditor textEditor;

	public CNFOutlinePage(LSPDocumentInfo info, @Nullable ITextEditor textEditor) {
		this.preferences = InstanceScope.INSTANCE.getNode(ID + '.' + info.getFileUri());
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
			final StyledText styledText;
			if (textEditor instanceof AbstractTextEditor) {
				AbstractTextEditor editor = (AbstractTextEditor) textEditor;
				styledText = ((StyledText) editor.getAdapter(Control.class));
			} else {
				styledText = null;
			}
			this.viewer.addSelectionChangedListener(new ISelectionChangedListener() {
				@Override
				public void selectionChanged(SelectionChangedEvent event) {
					if(viewer.getTree().isFocusControl() && viewer.getSelection() != null) {
						Object selection = ((TreeSelection)viewer.getSelection()).getFirstElement();
						if (selection != null && selection instanceof SymbolInformation) {
							Range range = ((SymbolInformation)selection).getLocation().getRange();
							try {
								int startLineOffest = info.getDocument().getLineOffset(range.getStart().getLine());
								if (styledText != null) {
									styledText.removeCaretListener(selectByEditorCaretListener);
								}
								textEditor.selectAndReveal(startLineOffest + range.getStart().getCharacter(), 0);
								if (styledText != null) {
									styledText.addCaretListener(selectByEditorCaretListener);
								}
							} catch (BadLocationException e) {
								return;
							}
						}
					}
				}
			});

			if (styledText != null) {
				styledText.addCaretListener(selectByEditorCaretListener);
			}
		}
	}

	private CaretListener selectByEditorCaretListener = new CaretListener() {
		@Override
		public void caretMoved(CaretEvent event) {
			if (!preferences.getBoolean(linkWithEditorPreference, true)) {
				return;
			}
			refreshTreeSelection(viewer, event.caretOffset, info.getDocument());
		}
	};

	public static void refreshTreeSelection(TreeViewer viewer, int offset, IDocument document) {
		ITreeContentProvider contentProvider = (ITreeContentProvider) viewer.getContentProvider();
		Object[] objects = contentProvider.getElements(null);
		SymbolInformation bestSymbol = null;
		int level = 0;
		while (objects != null && objects.length > 0) {
			SymbolInformation nextChild = null;
			for (Object object : objects) {
				SymbolInformation symbol = object instanceof SymbolInformation ? (SymbolInformation) object
						: Adapters.adapt(object, SymbolInformation.class);
				if (symbol != null) {
					if (isOffsetInSymbolRange(offset, symbol, document)) {
						nextChild = symbol;
						objects = contentProvider.getChildren(symbol);
						break;
					}
				}
			}
			if (nextChild == null)
				break;
			level++;
			bestSymbol = nextChild;
		}
		if (bestSymbol != null) {
			final int finalLevel = level;
			final SymbolInformation finalBestSymbol = bestSymbol;
			Display.getDefault().asyncExec(() -> {
				viewer.expandToLevel(finalLevel);
				viewer.setSelection(new StructuredSelection(finalBestSymbol), true);
			});
		}
	}

	private static boolean isOffsetInSymbolRange(int offset, SymbolInformation symbol, IDocument document) {
		Range range = symbol.getLocation().getRange();
		try {
			int startOffset = document.getLineOffset(range.getStart().getLine())
					+ range.getStart().getCharacter();
			int endOffset = document.getLineOffset(range.getEnd().getLine()) + range.getEnd().getCharacter();
			return startOffset <= offset && endOffset >= offset;
		} catch (BadLocationException e) {
			return false;
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
		IEditorPart editorPart = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor();
		if(editorPart instanceof AbstractTextEditor) {
			IMenuManager viewMenuManager= actionBars.getMenuManager();
			ToggleLinkingAction toggleLinkingAction = new ToggleLinkingAction();
			toggleLinkingAction.setActionDefinitionId(IWorkbenchCommandConstants.NAVIGATE_TOGGLE_LINK_WITH_EDITOR);
			viewMenuManager.add(toggleLinkingAction);
		}
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

	public class ToggleLinkingAction extends Action{
		public ToggleLinkingAction() {
			super(Messages.linkWithEditor_label);
			setDescription(Messages.linkWithEditor_description);
			setToolTipText(Messages.linkWithEditor_tooltip);
			setChecked(preferences.getBoolean(linkWithEditorPreference, true));
		}

		@Override
		public void run() {
			if(isChecked()) {
				preferences.remove(linkWithEditorPreference);
			}else {
				preferences.putBoolean(linkWithEditorPreference, false);
			}
			try {
				preferences.flush();
			} catch (BackingStoreException e) {
				//Unable to update preferences right now, done periodically
			}
		}
	}
}
