/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.operations.typeHierarchy;

import java.util.AbstractMap.SimpleEntry;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.LanguageServersRegistry.LanguageServerDefinition;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.lsp4j.TypeHierarchyPrepareParams;
import org.eclipse.lsp4j.TypeHierarchySubtypesParams;
import org.eclipse.lsp4j.TypeHierarchySupertypesParams;
import org.eclipse.lsp4j.services.TextDocumentService;

public class TypeHierarchyContentProvider implements ITreeContentProvider {

	private final LanguageServerDefinition lsDefinition;
	private final IDocument document;
	private boolean showSuperTypes;
	private LanguageServerWrapper wrapper;

	public TypeHierarchyContentProvider(LanguageServerDefinition lsDefinition, IDocument document, boolean showSuperTypes) {
		this.lsDefinition = lsDefinition;
		this.document = document;
		this.showSuperTypes = showSuperTypes;
	}

	@Override
	public Object[] getElements(Object inputElement) {
		if (inputElement instanceof ITextSelection textSelection) {
			try {
				Position position = LSPEclipseUtils.toPosition(textSelection.getOffset(), document);
				TypeHierarchyPrepareParams prepare = new TypeHierarchyPrepareParams(LSPEclipseUtils.toTextDocumentIdentifier(document), position);
				return LanguageServers.forDocument(document).withPreferredServer(lsDefinition)
					.computeFirst((wrapper, ls) -> ls.getTextDocumentService().prepareTypeHierarchy(prepare).thenApply(items -> new SimpleEntry<>(wrapper, items)))
					.thenApply(entry -> {
						wrapper = entry.map(Entry::getKey).orElse(null);
						return entry.map(Entry::getValue).map(list -> list.toArray(Object[]::new)).orElse(new Object[0]);
					}).get(500, TimeUnit.MILLISECONDS);
			} catch (Exception e) {
				LanguageServerPlugin.logError(e);
			}
		}
		return new Object[0];
	}

	@Override
	public Object[] getChildren(Object parentElement) {
		if (parentElement instanceof TypeHierarchyItem parentItem) {
			try {
				return wrapper.execute(ls -> {
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

}
