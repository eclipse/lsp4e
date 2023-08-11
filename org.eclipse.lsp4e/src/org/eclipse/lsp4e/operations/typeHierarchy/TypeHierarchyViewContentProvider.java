/*******************************************************************************
 * Copyright (c) 2023 Bachmann electronic GmbH and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * Gesa Hentschke (Bachmann electronic GmbH) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.typeHierarchy;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.LanguageServers.LanguageServerDocumentExecutor;
import org.eclipse.lsp4e.internal.Pair;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4e.ui.views.HierarchyViewInput;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.lsp4j.TypeHierarchyPrepareParams;
import org.eclipse.lsp4j.TypeHierarchySubtypesParams;
import org.eclipse.lsp4j.TypeHierarchySupertypesParams;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.ui.PlatformUI;

public class TypeHierarchyViewContentProvider implements ITreeContentProvider {
	private TreeViewer treeViewer;
	private LanguageServerWrapper languageServerWrapper;
	private List<TypeHierarchyItem> hierarchyItems = Collections.emptyList();
	public boolean showSuperTypes = true;

	@Override
	public Object[] getElements(Object inputElement) {
		if (hierarchyItems.isEmpty()) {
			return new Object[] { Messages.TH_no_type_hierarchy };
		}
		return hierarchyItems.toArray();
	}

	@Override
	public Object[] getChildren(Object parentElement) {
		if (parentElement instanceof TypeHierarchyItem parentItem) {
			try {
				return languageServerWrapper.execute(ls -> {
					TextDocumentService textDocumentService = ls.getTextDocumentService();

					return showSuperTypes
							? textDocumentService.typeHierarchySupertypes(new TypeHierarchySupertypesParams(parentItem))
							: textDocumentService.typeHierarchySubtypes(new TypeHierarchySubtypesParams(parentItem));
				})
					.thenApply(list -> list == null ? new Object[0] : list.toArray(Object[]::new))
					.get(500, TimeUnit.MILLISECONDS);
			} catch (Exception e) {
				LanguageServerPlugin.logError(e);
			}
		}
		return new Object[0];
	}

	@Override
	public Object getParent(Object element) {
		return null;
	}

	@Override
	public boolean hasChildren(Object element) {
		return true;
	}

	@Override
	public void inputChanged(final Viewer viewer, final Object oldInput, final Object newInput) {
		ITreeContentProvider.super.inputChanged(viewer, oldInput, newInput);

		if (newInput instanceof HierarchyViewInput viewInput) {

			IDocument document = viewInput.getDocument();
			if (document != null) {
				try {
					initialise(document, viewInput.getOffset(), (TreeViewer) viewer);
				} catch (BadLocationException e) {
					handleRootError();
				}
			} else {
				handleRootError();
			}
		} else {
			handleRootError();
		}

	}

	private void initialise(final @NonNull IDocument document, final int offset, TreeViewer viewer) throws BadLocationException {
		LanguageServerDocumentExecutor executor = LanguageServers.forDocument(document)
				.withCapability(ServerCapabilities::getTypeHierarchyProvider);
		if (!executor.anyMatching()) {
			handleRootError();
			return;
		}
		TypeHierarchyPrepareParams prepareParams = toTypeHierarchyPrepareParams(offset, document);
		executor.computeFirst((w, ls) -> ls.getTextDocumentService().prepareTypeHierarchy(prepareParams)
				.thenApply(result -> new Pair<>(w, result))).thenAccept(o -> o.ifPresentOrElse(p -> {
					languageServerWrapper = p.getFirst();
					if (!p.getSecond().isEmpty()) {
						hierarchyItems = p.getSecond();
						treeViewer = viewer;
						PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
							if (treeViewer != null) {
								treeViewer.refresh();
								treeViewer.expandToLevel(2);
								if (!hierarchyItems.isEmpty()) { // if handleRootError() has been called in the meantime
									treeViewer.getControl().setEnabled(true);
									treeViewer.setSelection(new StructuredSelection(hierarchyItems.get(0)));
								}
							}
						});
					}
				}, this::handleRootError)).handle((result, error) -> {
					if (error != null) {
						handleRootError();
					}
					return result;
				});

	}

	private static TypeHierarchyPrepareParams toTypeHierarchyPrepareParams(int offset, final @NonNull IDocument document) throws BadLocationException {
		Position position =  LSPEclipseUtils.toPosition(offset, document);
		TextDocumentIdentifier documentIdentifier = LSPEclipseUtils.toTextDocumentIdentifier(document);
		return new TypeHierarchyPrepareParams(documentIdentifier, position);
	}

	private void handleRootError() {
		hierarchyItems = Collections.emptyList();
		PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
			if (treeViewer != null) {
				treeViewer.refresh();
			}
		});
	}

	@Override
	public void dispose() {
		if (treeViewer != null) {
			treeViewer.getControl().dispose();
			treeViewer = null;
		}
	}

}
