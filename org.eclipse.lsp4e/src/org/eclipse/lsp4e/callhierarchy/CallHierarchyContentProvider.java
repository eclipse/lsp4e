/*******************************************************************************
 * Copyright (c) 2022 Avaloq Group AG (http://www.avaloq.com).
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Andrew Lamb (Avaloq Group AG) - Initial implementation
 *******************************************************************************/

package org.eclipse.lsp4e.callhierarchy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.LanguageServers.LanguageServerDocumentExecutor;
import org.eclipse.lsp4e.internal.Pair;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4e.ui.views.HierarchyViewInput;
import org.eclipse.lsp4j.CallHierarchyIncomingCall;
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyPrepareParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.ui.PlatformUI;

/**
 * Content provider for the call hierarchy tree view.
 */
public class CallHierarchyContentProvider implements ITreeContentProvider {
	private TreeViewer treeViewer;
	private LanguageServerWrapper languageServerWrapper;
	private List<CallHierarchyViewTreeNode> rootItems;
	private String rootMessage = Messages.CH_finding_callers;

	@Override
	public Object[] getElements(final Object inputElement) {
		if (rootItems != null) {
			return rootItems.toArray();
		}
		return new Object[] { rootMessage };
	}

	@Override
	public Object[] getChildren(final Object parentElement) {
		if (parentElement instanceof CallHierarchyViewTreeNode treeNode) {
			return findCallers(treeNode);
		} else {
			return new Object[0];
		}
	}

	@Override
	public Object getParent(final Object element) {
		if (element instanceof CallHierarchyViewTreeNode treeNode) {
			return treeNode.getParent();
		}
		return null;
	}

	@Override
	public boolean hasChildren(final Object element) {
		return element instanceof CallHierarchyViewTreeNode;
	}

	@Override
	public void inputChanged(final Viewer viewer, final Object oldInput, final Object newInput) {
		ITreeContentProvider.super.inputChanged(viewer, oldInput, newInput);

		treeViewer = (TreeViewer) viewer;
		if (newInput instanceof HierarchyViewInput viewInput) {
			rootMessage = Messages.CH_finding_callers;
			rootItems = null;

			IDocument document = viewInput.getDocument();
			if (document != null) {
				try {
					initialise(document, viewInput.getOffset());
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

	private void initialise(final @NonNull IDocument document, final int offset) throws BadLocationException {
		LanguageServerDocumentExecutor executor = LanguageServers.forDocument(document)
				.withCapability(ServerCapabilities::getCallHierarchyProvider);
		if (!executor.anyMatching()) {
			handleRootError();
			return;
		}
		CallHierarchyPrepareParams prepareParams = LSPEclipseUtils.toCallHierarchyPrepareParams(offset, document);
		executor.computeFirst((w, ls) -> ls.getTextDocumentService().prepareCallHierarchy(prepareParams)
				.thenApply(result -> new Pair<>(w, result))).thenAccept(o -> o.ifPresentOrElse(p -> {
					languageServerWrapper = p.getFirst();
					List<CallHierarchyItem> hierarchyItems = p.getSecond();
					if (!hierarchyItems.isEmpty()) {
						rootItems = new ArrayList<>(hierarchyItems.size());
						for (CallHierarchyItem item : hierarchyItems) {
							rootItems.add(new CallHierarchyViewTreeNode(item));
						}
					} else {
						rootMessage = Messages.CH_no_call_hierarchy;
					}
					PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
						if (treeViewer != null) {
							treeViewer.refresh();
							treeViewer.expandToLevel(2);
						}
					});
				}, this::handleRootError)).handle((result, error) -> {
					if (error != null) {
						handleRootError();
					}
					return result;
				});

	}

	private void handleRootError() {
		rootItems = Collections.emptyList();
		PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
			if (treeViewer != null) {
				treeViewer.refresh();
			}
		});
	}

	private Object[] findCallers(final CallHierarchyViewTreeNode callee) {
		if (callee.getChildren() == null) {
			treeViewer.getControl().setEnabled(false);
			updateCallers(callee);
			return new Object[] { Messages.CH_finding_callers };
		}
		return callee.getChildren();
	}

	private void updateCallers(final CallHierarchyViewTreeNode callee) {
		CallHierarchyIncomingCallsParams incomingCallParams = new CallHierarchyIncomingCallsParams(
				callee.getCallContainer());
		languageServerWrapper.execute(languageServer -> languageServer.getTextDocumentService()
				.callHierarchyIncomingCalls(incomingCallParams)).thenApply(incomingCalls -> {
					List<CallHierarchyViewTreeNode> children = new ArrayList<>(incomingCalls.size());
					for (CallHierarchyIncomingCall call : incomingCalls) {
						CallHierarchyItem callContainer = call.getFrom();
						List<Range> callSites = call.getFromRanges();
						for (Range callSite : callSites) {
							CallHierarchyViewTreeNode child = new CallHierarchyViewTreeNode(callContainer, callSite);
							child.setParent(callee);
							children.add(child);
						}
						if (callSites.isEmpty()) {
							CallHierarchyViewTreeNode child = new CallHierarchyViewTreeNode(callContainer);
							child.setParent(callee);
							children.add(child);
						}
					}
					return children;
				}).handle((result, error) -> updateChildrenInView(callee, result, error));
	}

	private List<CallHierarchyViewTreeNode> updateChildrenInView(final CallHierarchyViewTreeNode callee,
			final List<CallHierarchyViewTreeNode> children, final Throwable error) {
		if (error != null || children == null) {
			callee.setChildren(Collections.emptyList());
		} else {
			callee.setChildren(children);
		}
		PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
			if (treeViewer != null) {
				treeViewer.refresh();
				treeViewer.getControl().setEnabled(true);
			}
		});
		return children;
	}

	@Override
	public void dispose() {
		if (treeViewer != null) {
			treeViewer.getControl().dispose();
			treeViewer = null;
		}
	}

}
