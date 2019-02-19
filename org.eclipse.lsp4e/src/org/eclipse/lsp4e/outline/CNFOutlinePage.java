/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
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
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.ITextViewerExtension5;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.IPostSelectionProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProviderChangedEvent;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.outline.SymbolsModel.DocumentSymbolWithFile;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.navigator.CommonViewer;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

public class CNFOutlinePage implements IContentOutlinePage, ILabelProviderListener {

	public static final String ID = "org.eclipse.lsp4e.outline"; //$NON-NLS-1$
	public static final String LINK_WITH_EDITOR_PREFERENCE = ID + ".linkWithEditor"; //$NON-NLS-1$
	public static final String SHOW_KIND_PREFERENCE = ID + ".showKind"; //$NON-NLS-1$

	private CommonViewer outlineViewer;
	private final IEclipsePreferences preferences;
	private final ITextEditor textEditor;
	private final ITextViewer textEditorViewer;
	private final IDocument document;
	private final LanguageServer languageServer;

	class OutlineInfo {

		public final ITextEditor textEditor;
		public final LanguageServer languageServer;
		public final IDocument document;

		public OutlineInfo(IDocument document, LanguageServer languageServer, @Nullable ITextEditor textEditor) {
			this.document = document;
			this.languageServer = languageServer;
			this.textEditor = textEditor;
		}
	}

	public CNFOutlinePage(IDocument document, LanguageServer languageServer, @Nullable ITextEditor textEditor) {
		this.preferences = InstanceScope.INSTANCE.getNode(LanguageServerPlugin.PLUGIN_ID);
		this.textEditor = textEditor;
		if (textEditor != null) {
			this.textEditorViewer = ((ITextViewer) textEditor.getAdapter(ITextOperationTarget.class));
		} else {
			this.textEditorViewer = null;
		}
		this.document = LSPEclipseUtils.getDocument(textEditor);
		this.languageServer = languageServer;
	}

	@Override
	public void createControl(Composite parent) {
		this.outlineViewer = new CommonViewer(ID, parent, SWT.NONE);
		this.outlineViewer.setInput(new OutlineInfo(this.document, this.languageServer, this.textEditor));
		this.outlineViewer.getLabelProvider().addListener(this);
		if (textEditor != null) {
			this.outlineViewer.addOpenListener(event -> {
				if (preferences.getBoolean(LINK_WITH_EDITOR_PREFERENCE, true))
					textEditor.setFocus();
			});
			this.outlineViewer.addSelectionChangedListener(event -> {
				if (preferences.getBoolean(LINK_WITH_EDITOR_PREFERENCE, true)
						&& outlineViewer.getTree().isFocusControl() && outlineViewer.getSelection() != null) {
					Object selection = ((TreeSelection) outlineViewer.getSelection()).getFirstElement();
					Range range = getRangeSelection(selection);
					if (range != null) {
						try {
							int startOffset = document.getLineOffset(range.getStart().getLine())
									+ range.getStart().getCharacter();
							int endOffset = document.getLineOffset(range.getEnd().getLine())
									+ range.getEnd().getCharacter();
							textEditor.selectAndReveal(startOffset,
									endOffset - startOffset);
						} catch (BadLocationException e) {
							return;
						}
					}
				}
			});
			if (textEditorViewer != null) {
				editorSelectionChangedListener = new EditorSelectionChangedListener();
				editorSelectionChangedListener.install(textEditorViewer.getSelectionProvider());
			}
		}
	}

	/**
	 * Returns the range of the given selection and null otherwise.
	 *
	 * @param selection
	 *            the selected symbol.
	 * @return the range of the given selection and null otherwise.
	 */
	private Range getRangeSelection(Object selection) {
		if (selection == null) {
			return null;
		}
		if (selection instanceof SymbolInformation) {
			return ((SymbolInformation) selection).getLocation().getRange();
		}
		if (selection instanceof DocumentSymbolWithFile) {
			return ((DocumentSymbolWithFile) selection).symbol.getSelectionRange();
		}
		return null;
	}

	class EditorSelectionChangedListener implements ISelectionChangedListener {

		public void install(ISelectionProvider selectionProvider) {
			if (selectionProvider == null)
				return;

			if (selectionProvider instanceof IPostSelectionProvider) {
				IPostSelectionProvider provider = (IPostSelectionProvider) selectionProvider;
				provider.addPostSelectionChangedListener(this);
			} else {
				selectionProvider.addSelectionChangedListener(this);
			}
		}

		public void uninstall(ISelectionProvider selectionProvider) {
			if (selectionProvider == null)
				return;

			if (selectionProvider instanceof IPostSelectionProvider) {
				IPostSelectionProvider provider = (IPostSelectionProvider) selectionProvider;
				provider.removePostSelectionChangedListener(this);
			} else {
				selectionProvider.removeSelectionChangedListener(this);
			}
		}

		@Override
		public void selectionChanged(SelectionChangedEvent event) {
			ISelection selection = event.getSelection();
			if (!(selection instanceof ITextSelection)) {
				return;
			}
			ITextSelection textSelection = (ITextSelection) selection;
			if (!preferences.getBoolean(LINK_WITH_EDITOR_PREFERENCE, true)) {
				return;
			}
			int offset = outlineViewer instanceof ITextViewerExtension5
					? ((ITextViewerExtension5) outlineViewer).widgetOffset2ModelOffset(textSelection.getOffset())
					: textSelection.getOffset();
			refreshTreeSelection(outlineViewer, offset, document);
		}
	}

	private EditorSelectionChangedListener editorSelectionChangedListener;

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
				if (symbol != null && isOffsetInSymbolRange(offset, symbol, document)) {
					nextChild = symbol;
					objects = contentProvider.getChildren(symbol);
					break;
				}
			}
			if (nextChild == null)
				break;
			level++;
			bestSymbol = nextChild;
		}
		if (bestSymbol != null) {
			if (bestSymbol.equals(viewer.getStructuredSelection().getFirstElement())) {
				// the symbol to select is the same than current selected symbol, don't select
				// it.
				return;
			}
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
			int startOffset = document.getLineOffset(range.getStart().getLine()) + range.getStart().getCharacter();
			int endOffset = document.getLineOffset(range.getEnd().getLine()) + range.getEnd().getCharacter();
			return startOffset <= offset && endOffset >= offset;
		} catch (BadLocationException e) {
			return false;
		}
	}

	@Override
	public void dispose() {
		this.outlineViewer.dispose();
		if (textEditorViewer != null) {
			editorSelectionChangedListener.uninstall(textEditorViewer.getSelectionProvider());
		}
	}

	@Override
	public Control getControl() {
		return this.outlineViewer.getControl();
	}

	@Override
	public void setActionBars(IActionBars actionBars) {
	}

	@Override
	public void setFocus() {
		this.outlineViewer.getTree().setFocus();
	}

	@Override
	public void addSelectionChangedListener(ISelectionChangedListener listener) {
		this.outlineViewer.addSelectionChangedListener(listener);
	}

	@Override
	public ISelection getSelection() {
		return this.outlineViewer.getSelection();
	}

	@Override
	public void removeSelectionChangedListener(ISelectionChangedListener listener) {
		this.outlineViewer.removeSelectionChangedListener(listener);
	}

	@Override
	public void setSelection(ISelection selection) {
		this.outlineViewer.setSelection(selection);
	}

	@Override
	public void labelProviderChanged(LabelProviderChangedEvent event) {
		this.outlineViewer.refresh(true);
	}

}
