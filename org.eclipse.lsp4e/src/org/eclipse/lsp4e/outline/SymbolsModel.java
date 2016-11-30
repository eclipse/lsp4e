/*******************************************************************************
 * Copyright (c) 2016 Rogue Wave Software Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.outline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;

class SymbolsModel {

	private static final SymbolInformation ROOT = new SymbolInformation();

	private List<? extends SymbolInformation> response;
	private Map<SymbolInformation, List<SymbolInformation>> childrenMap = new HashMap<>();

	public boolean update(List<? extends SymbolInformation> response) {
		// TODO update model only on real change
		childrenMap.clear();
		this.response = response;
		return true;
	}

	public Object[] getElements() {
		if (response != null && !response.isEmpty()) {
			Collections.sort(response, new Comparator<SymbolInformation>() {

				@Override
				public int compare(SymbolInformation o1, SymbolInformation o2) {
					Range r1 = o1.getLocation().getRange();
					Range r2 = o2.getLocation().getRange();

					if (r1.getStart().getLine() == r2.getStart().getLine()) {
						return Integer.compare(r1.getStart().getCharacter(), r2.getStart().getCharacter());
					}

					return Integer.compare(r1.getStart().getLine(), r2.getStart().getLine());
				}
			});

			Stack<SymbolInformation> parentStack = new Stack<>();
			parentStack.push(ROOT);
			SymbolInformation previousSymbol = null;
			for (int i = 0; i < response.size(); i++) {
				SymbolInformation symbol = response.get(i);

				if (isIncluded(previousSymbol, symbol)) {
					parentStack.push(previousSymbol);
					addChild(parentStack.peek(), symbol);
				} else if (isIncluded(parentStack.peek(), symbol)) {
					addChild(parentStack.peek(), symbol);
				} else {
					while (!isIncluded(parentStack.peek(), symbol)) {
						parentStack.pop();
					}
					addChild(parentStack.peek(), symbol);
					parentStack.push(symbol);
				}

				previousSymbol = symbol;
			}
		}

		return getChildren(ROOT);
	}

	private void addChild(SymbolInformation parent, SymbolInformation child) {
		List<SymbolInformation> children = childrenMap.get(parent);
		if (children == null) {
			children = new ArrayList<>();
			childrenMap.put(parent, children);
		}
		children.add(child);
	}

	public Object[] getChildren(Object parentElement) {
		if (parentElement != null && parentElement instanceof SymbolInformation && response != null) {
			List<SymbolInformation> children = childrenMap.get(parentElement);
			if (children != null) {
				return children.toArray();
			}
		}
		return null;
	}

	private boolean isIncluded(SymbolInformation parent, SymbolInformation symbol) {
		if (parent == null || symbol == null) {
			return false;
		}
		if (parent == ROOT) {
			return true;
		}
		return isIncluded(parent.getLocation(), symbol.getLocation());
	}

	private boolean isIncluded(Location reference, Location included) {
		return reference.getUri().equals(included.getUri()) && isAfter(reference.getRange().getStart(), included.getRange().getStart())
				&& isAfter(included.getRange().getEnd(), reference.getRange().getEnd());
	}

	private boolean isAfter(Position reference, Position included) {
		return included.getLine() > reference.getLine() || (included.getLine() == reference.getLine() && included.getLine() > reference.getLine());
	}

	public Object getParent(Object element) {
		return childrenMap.keySet().stream().filter(parent -> {
			List<SymbolInformation> children = childrenMap.get(parent);
			return children == null ? false : children.contains(element);
		}).findFirst();
	}

}
