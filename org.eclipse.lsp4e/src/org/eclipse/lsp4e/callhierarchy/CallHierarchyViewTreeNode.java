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

import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.Range;

/**
 * Class representing a node in the call hierarchy tree.
 */
public class CallHierarchyViewTreeNode {

	/**
	 * the {@link CallHierarchyItem} of the callable containing the call site that
	 * this node represents.
	 */
	private final CallHierarchyItem callContainer;

	/** the location of the call site that this node represents. */
	private final @Nullable Range callSite;

	private @Nullable CallHierarchyViewTreeNode parent;
	private CallHierarchyViewTreeNode @Nullable [] children;

	/**
	 * Creates a new instance of {@link CallHierarchyViewTreeNode}.
	 *
	 * @param callContainer
	 *            the {@link CallHierarchyItem} of the callable containing the call.
	 * @param callSite
	 *            the range in the callable of the call site.
	 */
	public CallHierarchyViewTreeNode(final CallHierarchyItem callContainer, final @Nullable Range callSite) {
		this.callContainer = callContainer;
		this.callSite = callSite;
	}

	/**
	 * Creates a new instance of {@link CallHierarchyViewTreeNode}.
	 *
	 * @param callContainer
	 *            the {@link CallHierarchyItem} of the callable containing the call.
	 */
	public CallHierarchyViewTreeNode(final CallHierarchyItem callContainer) {
		this.callContainer = callContainer;
		this.callSite = null;
	}

	/**
	 * Get the parent of this node.
	 *
	 * @return the parent node.
	 */
	public @Nullable CallHierarchyViewTreeNode getParent() {
		return parent;
	}

	/**
	 * Set the parent for this node.
	 *
	 * @param parent
	 *            The new parent.
	 */
	public void setParent(final CallHierarchyViewTreeNode parent) {
		this.parent = parent;
	}

	/**
	 * Get all the children of this node.
	 *
	 * @return this node's children.
	 */
	public CallHierarchyViewTreeNode @Nullable [] getChildren() {
		return children;
	}

	/**
	 * Set the children for this node.
	 *
	 * @param children
	 *            the new children.
	 */
	public void setChildren(final List<CallHierarchyViewTreeNode> children) {
		this.children = children.toArray(CallHierarchyViewTreeNode[]::new);
	}

	/**
	 * Get the {@link CallHierarchyItem} describing the object containing the call
	 * site that this node represents.
	 *
	 * @return the containing {@link CallHierarchyItem}.
	 */
	public CallHierarchyItem getCallContainer() {
		return callContainer;
	}

	/**
	 * Get the {@link Range} describing the call site within the call container.
	 *
	 * @return the call site {@link Range}
	 */
	public @Nullable Range getCallSite() {
		return callSite;
	}

	/**
	 * Get the {@link Range} to select when this node is double clicked.
	 *
	 * @return the selection range of this node.
	 */
	public Range getSelectionRange() {
		Range theCallSite = callSite;
		if (theCallSite != null) {
			return theCallSite;
		}
		return callContainer.getSelectionRange();
	}

	/**
	 * Determines if the container represents a recursion call (i.e. whether the
	 * call is already in the tree.)
	 *
	 * @return True if the call is part of a recursion
	 */
	public boolean isRecursive() {
		String uri = callContainer.getUri();
		for (CallHierarchyViewTreeNode ancestor = parent; ancestor != null; ancestor = ancestor.getParent()) {
			if (uri.equals(ancestor.getCallContainer().getUri())) {
				return true;
			}
		}
		return false;
	}

}
