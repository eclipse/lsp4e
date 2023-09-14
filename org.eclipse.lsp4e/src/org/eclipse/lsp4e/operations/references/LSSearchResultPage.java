/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.operations.references;

import java.net.URI;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.OpenEvent;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.operations.references.FileAndURIMatchLabelProvider.FileAndURIMatchBaseLabelProvider;
import org.eclipse.search.internal.ui.text.DecoratingFileSearchLabelProvider;
import org.eclipse.search.internal.ui.text.FileMatch;
import org.eclipse.search.internal.ui.text.FileSearchPage;
import org.eclipse.search.internal.ui.text.FileTreeContentProvider;
import org.eclipse.search.internal.ui.text.LineElement;
import org.eclipse.search.ui.text.Match;

/**
 * Overrides some behavior of FileSearchPage to allow working with URIs/without IFile
 */
public class LSSearchResultPage extends FileSearchPage {

	private ITreeContentProvider contentProvider;

	@Override
	public void configureTreeViewer(TreeViewer viewer) {
		super.configureTreeViewer(viewer);
		FileTreeContentProvider fileMatchContentProvider = (FileTreeContentProvider)viewer.getContentProvider();
		this.contentProvider = new FileAndURIMatchContentProvider(fileMatchContentProvider);
		viewer.setContentProvider(this.contentProvider);
		DecoratingFileSearchLabelProvider fileMatchDecoratingLabelProvider = (DecoratingFileSearchLabelProvider)viewer.getLabelProvider();
		FileAndURIMatchBaseLabelProvider baseLabelProvider = new FileAndURIMatchBaseLabelProvider(fileMatchDecoratingLabelProvider.getStyledStringProvider());
		viewer.setLabelProvider(new FileAndURIMatchLabelProvider(baseLabelProvider, fileMatchDecoratingLabelProvider));
		viewer.setComparator(new ViewerComparator() {
			@Override
			public int category(Object element) {
				if (element instanceof IContainer) {
					return 1;
				} else if (element instanceof URI uri) {
					if ("file".equals(uri.getScheme())) { //$NON-NLS-1$
						return 2;
					} else {
						return 3;
					}
				}
				return 4;
			}

			@Override
			public int compare(Viewer viewer, Object e1, Object e2) {
				int cat1 = category(e1);
				int cat2 = category(e2);
				if (cat1 != cat2) {
					return cat1 - cat2;
				}
				if (e1 instanceof LineElement m1 && e2 instanceof LineElement m2) {
					return m1.getOffset() - m2.getOffset();
				}
				if (e1 instanceof URIMatch m1 && e2 instanceof URIMatch m2) {
					return m1.getOffset() - m2.getOffset();
				}
				return super.compare(viewer, e1, e2);
			}
		});
	}

	@Override
	protected void elementsChanged(Object[] objects) {
		getViewer().setInput(getInput());
	}

	@Override
	protected void clear() {
		getViewer().setInput(new Object[0]);
	}

	@Override
	protected void evaluateChangedElements(Match[] matches, Set<Object> changedElements) {
		for (Match match : matches) {
			if (match instanceof FileMatch fileMatch && fileMatch.getLineElement() != null) {
				changedElements.add(fileMatch.getLineElement());
			} else {
				changedElements.add(match.getElement());
			}
		}
	}

	@Override
	protected void handleOpen(OpenEvent event) {
		Object firstElement = ((IStructuredSelection) event.getSelection()).getFirstElement();
		if (firstElement instanceof URI uri) {
			LSPEclipseUtils.open(uri.toString(), getViewPart().getSite().getPage(), null);
			return;
		} else if (firstElement instanceof URIMatch uriMatch) {
			LSPEclipseUtils.openInEditor(uriMatch.location, getViewPart().getSite().getPage());
			return;
		}
		super.handleOpen(event);
	}
}
