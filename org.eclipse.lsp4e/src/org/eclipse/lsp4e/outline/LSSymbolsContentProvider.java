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
package org.eclipse.lsp4e.outline;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.navigator.ICommonContentExtensionSite;
import org.eclipse.ui.navigator.ICommonContentProvider;

public class LSSymbolsContentProvider implements ICommonContentProvider, ITreeContentProvider, IDocumentListener, IResourceChangeListener {

	public static final Object COMPUTING = new Object();

	private TreeViewer viewer;
	private Throwable lastError;
	private LSPDocumentInfo info;

	private SymbolsModel symbolsModel = new SymbolsModel();
	private CompletableFuture<List<? extends SymbolInformation>> symbols;

	private IResource resource;

	@Override
	public void init(ICommonContentExtensionSite aConfig) {
	}

	@Override
	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		this.viewer = (TreeViewer) viewer;
		this.info = (LSPDocumentInfo) newInput;
		info.getDocument().addDocumentListener(this);
		resource = LSPEclipseUtils.findResourceFor(info.getFileUri().toString());
		resource.getWorkspace().addResourceChangeListener(this, IResourceChangeEvent.POST_CHANGE);
		refreshTreeContentFromLS();
	}

	@Override
	public Object[] getElements(Object inputElement) {
		if (this.symbols != null && !this.symbols.isDone()) {
			return new Object[] { COMPUTING };
		}
		if (this.lastError != null) {
			return new Object[] { this.lastError };
		}
		return symbolsModel.getElements();
	}

	@Override
	public Object[] getChildren(Object parentElement) {
		return symbolsModel.getChildren(parentElement);
	}

	@Override
	public Object getParent(Object element) {
		return symbolsModel.getParent(element);
	}

	@Override
	public boolean hasChildren(Object parentElement) {
		Object[] children = symbolsModel.getChildren(parentElement);
		return children != null && children.length > 0;
	}

	@Override
	public void documentAboutToBeChanged(DocumentEvent event) {
	}

	@Override
	public void documentChanged(DocumentEvent event) {
		refreshTreeContentFromLS();
	}

	private void refreshTreeContentFromLS() {
		if (symbols != null && !symbols.isDone()) {
			symbols.cancel(true);
		}
		lastError = null;
		DocumentSymbolParams params = new DocumentSymbolParams(new TextDocumentIdentifier(info.getFileUri().toString()));
		symbols = info.getLanguageClient().getTextDocumentService().documentSymbol(params);

		symbols.thenAccept((List<? extends SymbolInformation> t) -> {
			symbolsModel.update(t);

			viewer.getControl().getDisplay().asyncExec(() -> {
				viewer.refresh();
			});
		});

		symbols.exceptionally(ex -> {
			lastError = ex;
			viewer.getControl().getDisplay().asyncExec(() -> {
				viewer.refresh();
			});
			return Collections.emptyList();
		});
	}

	@Override
	public void dispose() {
		info.getDocument().removeDocumentListener(this);
		resource.getWorkspace().removeResourceChangeListener(this);
		ICommonContentProvider.super.dispose();
	}

	@Override
	public void resourceChanged(IResourceChangeEvent event) {
		if ((event.getDelta().getFlags() ^ IResourceDelta.MARKERS) != 0) {
			try {
				event.getDelta().accept(delta -> {
					if (delta.getResource().equals(this.resource)) {
						viewer.getControl().getDisplay().asyncExec(() -> {
							if (viewer instanceof StructuredViewer) {
								viewer.refresh(true);
							}
						});
					}
					return delta.getResource().getFullPath().isPrefixOf(this.resource.getFullPath());
				});
			} catch (CoreException e) {
				LanguageServerPlugin.logError(e);
			}
		}
	}

	@Override
	public void restoreState(IMemento aMemento) {
	}

	@Override
	public void saveState(IMemento aMemento) {
	}
}
