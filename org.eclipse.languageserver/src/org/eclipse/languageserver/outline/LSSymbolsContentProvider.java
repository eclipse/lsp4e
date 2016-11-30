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
import org.eclipse.languageserver.LSPEclipseUtils;
import org.eclipse.languageserver.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.navigator.ICommonContentExtensionSite;
import org.eclipse.ui.navigator.ICommonContentProvider;

public class LSSymbolsContentProvider implements ICommonContentProvider, ITreeContentProvider, IDocumentListener, IResourceChangeListener {
	
	public static final Object COMPUTING = new Object();
	
	private TreeViewer viewer;
	private List<? extends SymbolInformation> lastResponse;
	private Throwable lastError;
	private LSPDocumentInfo info;

	private CompletableFuture<List<? extends SymbolInformation>> symbols;

	private IResource resource;
	
	@Override
	public void restoreState(IMemento aMemento) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void saveState(IMemento aMemento) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void init(ICommonContentExtensionSite aConfig) {
	}
	
	@Override
	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		this.viewer = (TreeViewer)viewer;
		this.info = (LSPDocumentInfo)newInput;
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
		if (lastResponse != null) {
			return this.lastResponse.stream().filter(symbol -> getParent(symbol) == null).toArray();
		}
		return null;
	}

	@Override
	public Object[] getChildren(Object parentElement) {
		if (parentElement != null && parentElement instanceof SymbolInformation && this.lastResponse != null) {
			// TODO: this can be optimized by building the tree upon response (O(n) instead of O(n^2))
			return this.lastResponse.stream().filter(symbol -> getParent(symbol) == parentElement).toArray();
		}
		return null;
	}

	private boolean isIncluded(Location reference, Location included) {
		return reference.getUri().equals(included.getUri()) &&
			isAfter(reference.getRange().getStart(), included.getRange().getStart()) &&
			isAfter(included.getRange().getEnd(), reference.getRange().getEnd());
	}

	private boolean isAfter(Position reference, Position included) {
		return included.getLine() > reference.getLine() ||
			(included.getLine() == reference.getLine() && included.getLine() > reference.getLine());
	}

	@Override
	public Object getParent(Object element) {
		if (element instanceof SymbolInformation) {
			SymbolInformation child = (SymbolInformation)element;
			SymbolInformation res = null;
			for (SymbolInformation current : this.lastResponse) {
				if (current != null && isIncluded(current.getLocation(), child.getLocation()) && (res == null || isIncluded(res.getLocation(), current.getLocation()))) {
					res = current;
				}
			}
			return res;
		}
		return null;
	}

	@Override
	public boolean hasChildren(Object element) {
		Object[] children = getChildren(element);
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
		lastResponse = null;
		lastError = null;
		DocumentSymbolParams params = new DocumentSymbolParams(new TextDocumentIdentifier(info.getFileUri().toString()));
		symbols = info.getLanguageClient().getTextDocumentService().documentSymbol(params);

		symbols.thenAccept((List<? extends SymbolInformation> t) -> {
			lastResponse = t;
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
								((TreeViewer)viewer).refresh(true);
							}
						});
					}
					return delta.getResource().getFullPath().isPrefixOf(this.resource.getFullPath());
				});
			} catch (CoreException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}
