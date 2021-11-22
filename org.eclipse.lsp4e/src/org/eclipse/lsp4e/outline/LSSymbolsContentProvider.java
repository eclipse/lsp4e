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

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.reconciler.AbstractReconciler;
import org.eclipse.jface.text.reconciler.DirtyRegion;
import org.eclipse.jface.text.reconciler.IReconcilingStrategy;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.navigator.ICommonContentExtensionSite;
import org.eclipse.ui.navigator.ICommonContentProvider;
import org.eclipse.ui.texteditor.ITextEditor;

public class LSSymbolsContentProvider implements ICommonContentProvider, ITreeContentProvider {

	public static final Object COMPUTING = new Object();

	@NonNullByDefault
	public static final class OutlineViewerInput {

		public final IDocument document;
		public final LanguageServer languageServer;

		@Nullable
		public final ITextEditor textEditor;

		@Nullable
		private final IPath documentPath;

		@Nullable
		public final IFile documentFile;

		@Nullable
		private final URI documentURI;

		public OutlineViewerInput(IDocument document, LanguageServer languageServer, @Nullable ITextEditor textEditor) {
			this.document = document;
			documentPath = LSPEclipseUtils.toPath(document);
			documentFile = documentPath == null ? null : LSPEclipseUtils.getFile(documentPath);
			documentURI = documentFile == null ? null : LSPEclipseUtils.toUri(documentFile);
			this.languageServer = languageServer;
			this.textEditor = textEditor;
		}
	}

	interface IOutlineUpdater {
		void install();

		void uninstall();
	}

	private final class DocumentChangedOutlineUpdater implements IDocumentListener, IOutlineUpdater {

		private final IDocument document;

		DocumentChangedOutlineUpdater(IDocument document) {
			this.document = document;
		}

		@Override
		public void install() {
			document.addDocumentListener(this);
			refreshTreeContentFromLS();
		}

		@Override
		public void uninstall() {
			document.removeDocumentListener(this);
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

	private final class ReconcilerOutlineUpdater extends AbstractReconciler implements IOutlineUpdater {

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

	private final class ResourceChangeOutlineUpdater implements IResourceChangeListener, IOutlineUpdater {

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
	private volatile Throwable lastError;
	private OutlineViewerInput outlineViewerInput;

	private final SymbolsModel symbolsModel = new SymbolsModel();
	private volatile CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> symbols;
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
		if (outlineUpdater != null) {
			outlineUpdater.uninstall();
		}

		if (newInput == null) {
			// happens during org.eclipse.jface.viewers.ContentViewer#handleDispose when
			// Quick Outline is closed
			return;
		}

		this.viewer = (TreeViewer) viewer;
		outlineViewerInput = (OutlineViewerInput) newInput;
		symbolsModel.setFile(outlineViewerInput.documentFile);

		// eagerly refresh the content tree, esp. important for the Quick Outline
		// because otherwise the outline will be blank for 1-2 seconds initially
		refreshTreeContentFromLS();

		outlineUpdater = createOutlineUpdater();
		outlineUpdater.install();
	}

	private IOutlineUpdater createOutlineUpdater() {
		if (refreshOnResourceChanged) {
			return new ResourceChangeOutlineUpdater(outlineViewerInput.documentFile);
		}
		final ITextViewer textViewer = LSPEclipseUtils.getTextViewer(outlineViewerInput.textEditor);
		return textViewer == null ? new DocumentChangedOutlineUpdater(outlineViewerInput.document)
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
		return symbolsModel.hasChildren(parentElement);
	}

	protected void refreshTreeContentFromLS() {
		final URI documentURI = outlineViewerInput.documentURI;
		if (documentURI == null) {
			lastError = new IllegalStateException("documentURI == null"); //$NON-NLS-1$
			viewer.getControl().getDisplay().asyncExec(viewer::refresh);
			return;
		}

		if (symbols != null) {
			symbols.cancel(true);
		}

		DocumentSymbolParams params = new DocumentSymbolParams(new TextDocumentIdentifier(documentURI.toString()));
		symbols = outlineViewerInput.languageServer.getTextDocumentService().documentSymbol(params);
		symbols.thenAcceptAsync(response -> {
			symbolsModel.update(response);
			lastError = null;

			final var linkWithEditor = InstanceScope.INSTANCE.getNode(LanguageServerPlugin.PLUGIN_ID)
					.getBoolean(CNFOutlinePage.LINK_WITH_EDITOR_PREFERENCE, true);

			viewer.getControl().getDisplay().asyncExec(() -> {
				TreePath[] expandedElements = viewer.getExpandedTreePaths();
				TreePath[] initialSelection = ((ITreeSelection) viewer.getSelection()).getPaths();
				viewer.refresh();
				viewer.setExpandedTreePaths(Arrays.stream(expandedElements).map(symbolsModel::toUpdatedSymbol)
						.filter(Objects::nonNull).toArray(TreePath[]::new));
				viewer.setSelection(new TreeSelection(Arrays.stream(initialSelection).map(symbolsModel::toUpdatedSymbol)
						.filter(Objects::nonNull).toArray(TreePath[]::new)));

				if (linkWithEditor) {
					ITextEditor editor = UI.getActiveTextEditor();
					if (editor != null) {
						ITextSelection selection = (ITextSelection) editor.getSelectionProvider().getSelection();
						CNFOutlinePage.refreshTreeSelection(viewer, selection.getOffset(), outlineViewerInput.document);
					}
				}
			});
		});

		symbols.exceptionally(ex -> {
			lastError = ex;
			viewer.getControl().getDisplay().asyncExec(viewer::refresh);
			return Collections.emptyList();
		});
	}

	@Override
	public void dispose() {
		if (outlineUpdater != null) {
			outlineUpdater.uninstall();
		}
	}

	@Override
	public void restoreState(IMemento aMemento) {
	}

	@Override
	public void saveState(IMemento aMemento) {
	}
}
