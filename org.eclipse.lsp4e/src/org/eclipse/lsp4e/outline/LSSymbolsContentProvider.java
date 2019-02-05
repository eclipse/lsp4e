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
 *  Lucas Bullen (Red Hat Inc.) - Bug 508472 - Outline to provide "Link with Editor"
 *                              - Bug 517428 - Requests sent before initialization
 *******************************************************************************/
package org.eclipse.lsp4e.outline;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.reconciler.AbstractReconciler;
import org.eclipse.jface.text.reconciler.DirtyRegion;
import org.eclipse.jface.text.reconciler.IReconcilingStrategy;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.outline.CNFOutlinePage.OutlineInfo;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.navigator.ICommonContentExtensionSite;
import org.eclipse.ui.navigator.ICommonContentProvider;
import org.eclipse.ui.texteditor.AbstractTextEditor;

public class LSSymbolsContentProvider implements ICommonContentProvider, ITreeContentProvider {

	public static final Object COMPUTING = new Object();

	interface IOutlineUpdater {
		void install();

		void uninstall();
	}

	class DocumentChangedOutlineUpdater implements IDocumentListener, IOutlineUpdater {

		private final IDocument document;

		@Override
		public void install() {
			document.addDocumentListener(this);
			refreshTreeContentFromLS();
		}

		@Override
		public void uninstall() {
			document.removeDocumentListener(this);
		}

		DocumentChangedOutlineUpdater(IDocument document) {
			this.document = document;
		}

		@Override
		public void documentAboutToBeChanged(DocumentEvent event) {
			// Do nothing
		}

		@Override
		public void documentChanged(DocumentEvent event) {
			refreshTreeContentFromLS();
		}
	}

	class ReconcilerOutlineUpdater extends AbstractReconciler implements IOutlineUpdater {

		private final ITextViewer textViewer;

		ReconcilerOutlineUpdater(ITextViewer textViewer) {
			this.textViewer = textViewer;
			super.setIsIncrementalReconciler(false);
			super.setIsAllowedToModifyDocument(false);
		}

		@Override
		public void install() {
			super.install(textViewer);
		}

		@Override
		protected void initialProcess() {
			refreshTreeContentFromLS();
		}

		@Override
		protected void process(DirtyRegion dirtyRegion) {
			refreshTreeContentFromLS();
		}

		@Override
		protected void reconcilerDocumentChanged(IDocument newDocument) {
			// Do nothing
		}

		@Override
		public IReconcilingStrategy getReconcilingStrategy(String contentType) {
			return null;
		}
	}

	class ResourceChangeOutlineUpdater implements IResourceChangeListener, IOutlineUpdater {

		private final IResource resource;

		public ResourceChangeOutlineUpdater(IResource resource) {
			this.resource = resource;
		}

		@Override
		public void install() {
			resource.getWorkspace().addResourceChangeListener(this, IResourceChangeEvent.POST_CHANGE);
		}

		@Override
		public void uninstall() {
			resource.getWorkspace().removeResourceChangeListener(this);
		}

		@Override
		public void resourceChanged(IResourceChangeEvent event) {
			if ((event.getDelta().getFlags() ^ IResourceDelta.MARKERS) != 0) {
				try {
					event.getDelta().accept(delta -> {
						if (delta.getResource().equals(this.resource)) {
							viewer.getControl().getDisplay().asyncExec(() -> {
								if (!viewer.getControl().isDisposed() && viewer instanceof StructuredViewer) {
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
	}

	private TreeViewer viewer;
	private Throwable lastError;
	private OutlineInfo outlineInfo;

	private SymbolsModel symbolsModel = new SymbolsModel();
	private CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> symbols;
	private final boolean refreshOnResourceChanged;
	private IOutlineUpdater outlineUpdater;

	public LSSymbolsContentProvider() {
		this(false);
	}

	public LSSymbolsContentProvider(boolean refreshOnResourceChanged) {
		this.refreshOnResourceChanged = refreshOnResourceChanged;
	}

	@Override
	public void init(ICommonContentExtensionSite aConfig) {
	}

	@Override
	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		this.viewer = (TreeViewer) viewer;
		this.outlineInfo = (OutlineInfo) newInput;
		this.symbolsModel
				.setFile((IFile) LSPEclipseUtils
						.findResourceFor(LSPEclipseUtils.toUri(this.outlineInfo.document).toString()));
		if (outlineUpdater != null) {
			outlineUpdater.uninstall();
		}
		outlineUpdater = createOutlineUpdater();
		outlineUpdater.install();
	}

	private IOutlineUpdater createOutlineUpdater() {
		if (refreshOnResourceChanged) {
			IResource resource = LSPEclipseUtils
					.findResourceFor(LSPEclipseUtils.toUri(this.outlineInfo.document).toString());
			return new ResourceChangeOutlineUpdater(resource);
		}
		ITextViewer textViewer = outlineInfo.textEditor == null ? null
				: ((ITextViewer) outlineInfo.textEditor.getAdapter(ITextOperationTarget.class));
		return textViewer == null
				? new DocumentChangedOutlineUpdater(outlineInfo.document)
				: new ReconcilerOutlineUpdater(textViewer);
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

	private void refreshTreeContentFromLS() {
		if (symbols != null && !symbols.isDone()) {
			symbols.cancel(true);
		}
		lastError = null;
		DocumentSymbolParams params = new DocumentSymbolParams(
				new TextDocumentIdentifier(LSPEclipseUtils.toUri(outlineInfo.document).toString()));
		symbols = outlineInfo.languageServer.getTextDocumentService().documentSymbol(params);
		symbols.thenAccept(t -> {
			symbolsModel.update(t);

			viewer.getControl().getDisplay().asyncExec(() -> {
				viewer.refresh();
			});
			if (!InstanceScope.INSTANCE.getNode(LanguageServerPlugin.PLUGIN_ID)
					.getBoolean(CNFOutlinePage.LINK_WITH_EDITOR_PREFERENCE, true)) {
				return;
			}
			Display.getDefault().asyncExec(() -> {
				IEditorPart editorPart = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
						.getActiveEditor();
				if (editorPart instanceof AbstractTextEditor) {
					ITextSelection selection = (ITextSelection) ((AbstractTextEditor) editorPart).getSelectionProvider()
							.getSelection();
					CNFOutlinePage.refreshTreeSelection(viewer, selection.getOffset(), outlineInfo.document);
				}
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
		outlineUpdater.uninstall();
		ICommonContentProvider.super.dispose();
	}

	@Override
	public void restoreState(IMemento aMemento) {
	}

	@Override
	public void saveState(IMemento aMemento) {
	}
}
