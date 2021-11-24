/*******************************************************************************
 * Copyright (c) 2016 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.outline;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class SymbolsModel {

	private static final SymbolInformation ROOT_SYMBOL_INFORMATION = new SymbolInformation();
	private static final Object[] EMPTY = new Object[0];

	private volatile Map<SymbolInformation, List<SymbolInformation>> childrenMap = Collections.emptyMap();
	private volatile List<DocumentSymbol> rootSymbols = Collections.emptyList();
	private final Map<DocumentSymbol, DocumentSymbol> parent = new HashMap<>();

	private IFile file;

	public static class DocumentSymbolWithFile {
		public final DocumentSymbol symbol;
		public final @NonNull IFile file;

		public DocumentSymbolWithFile(DocumentSymbol symbol, @NonNull IFile file) {
			this.symbol = symbol;
			this.file = file;
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof DocumentSymbolWithFile)) {
				return false;
			}
			DocumentSymbolWithFile other = (DocumentSymbolWithFile) obj;
			return Objects.equals(this.symbol, other.symbol) && Objects.equals(this.file, other.file);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.file, this.symbol);
		}
	}

	public synchronized boolean update(List<Either<SymbolInformation, DocumentSymbol>> response) {
		// TODO update model only on real change
		parent.clear();
		if (response == null || response.isEmpty()) {
			childrenMap = Collections.emptyMap();
			rootSymbols = Collections.emptyList();
		} else {
			final Map<SymbolInformation, List<SymbolInformation>> newChildrenMap = new HashMap<>();
			final List<DocumentSymbol> newRootSymbols = new ArrayList<>();

			Collections.sort(response, Comparator.comparing(
					either -> either.isLeft() ? either.getLeft().getLocation().getRange().getStart()
							: either.getRight().getRange().getStart(),
					// strange need to cast here, could be a JDT compiler issue
					Comparator.comparingInt(pos -> ((Position) pos).getLine())
							.thenComparingInt(pos -> ((Position) pos).getCharacter())));

			Deque<SymbolInformation> parentStack = new ArrayDeque<>();
			parentStack.push(ROOT_SYMBOL_INFORMATION);
			SymbolInformation previousSymbol = null;
			for (Either<SymbolInformation, DocumentSymbol> either : response) {
				if (either.isLeft()) {
					SymbolInformation symbol = either.getLeft();
					if (isIncluded(previousSymbol, symbol)) {
						parentStack.push(previousSymbol);
						addChild(newChildrenMap, parentStack.peek(), symbol);
					} else if (isIncluded(parentStack.peek(), symbol)) {
						addChild(newChildrenMap, parentStack.peek(), symbol);
					} else {
						while (!isIncluded(parentStack.peek(), symbol)) {
							parentStack.pop();
						}
						addChild(newChildrenMap, parentStack.peek(), symbol);
						parentStack.push(symbol);
					}
					previousSymbol = symbol;
				} else if (either.isRight()) {
					newRootSymbols.add(either.getRight());
				}
			}

			childrenMap = newChildrenMap;
			rootSymbols = newRootSymbols;
		}
		return true;
	}

	private boolean isIncluded(SymbolInformation parent, SymbolInformation symbol) {
		if (parent == null || symbol == null) {
			return false;
		}
		if (parent == ROOT_SYMBOL_INFORMATION) {
			return true;
		}
		return isIncluded(parent.getLocation(), symbol.getLocation());
	}

	private boolean isIncluded(Location reference, Location included) {
		return reference.getUri().equals(included.getUri())
				&& !reference.equals(included)
				&& isAfter(reference.getRange().getStart(), included.getRange().getStart())
				&& isAfter(included.getRange().getEnd(), reference.getRange().getEnd());
	}

	private boolean isAfter(Position reference, Position included) {
		return included.getLine() > reference.getLine()
				|| (included.getLine() == reference.getLine() && included.getCharacter() >= reference.getCharacter());
	}

	private void addChild(Map<SymbolInformation, List<SymbolInformation>> newChildrenMap, SymbolInformation parent,
				SymbolInformation child) {
		List<SymbolInformation> children = newChildrenMap.computeIfAbsent(parent, key -> new ArrayList<>());
		children.add(child);
	}

	public Object[] getElements() {
		List<Object> res = new ArrayList<>(Arrays.asList(getChildren(ROOT_SYMBOL_INFORMATION)));
		final IFile current = this.file;
		Function<DocumentSymbol, Object> mapper = current != null ?
				symbol -> new DocumentSymbolWithFile(symbol, current) :
				symbol -> symbol;
		rootSymbols.stream().map(mapper).forEach(res::add);
		return res.toArray(new Object[res.size()]);
	}

	public Object[] getChildren(Object parentElement) {
		if (parentElement != null) {
			if (parentElement instanceof SymbolInformation) {
				List<SymbolInformation> children = childrenMap.get(parentElement);
				if (children != null && !children.isEmpty()) {
					return children.toArray();
				}
			} else if (parentElement instanceof DocumentSymbolWithFile) {
				DocumentSymbolWithFile element = (DocumentSymbolWithFile) parentElement;
				List<DocumentSymbol> children = element.symbol.getChildren();
				if (children != null && !children.isEmpty()) {
					return element.symbol.getChildren().stream()
						.map(symbol -> new DocumentSymbolWithFile(symbol, element.file)).toArray();
				}
			}
		}
		return EMPTY;
	}

	public boolean hasChildren(Object parentElement) {
		if (parentElement != null) {
			if (parentElement instanceof SymbolInformation) {
				List<SymbolInformation> children = childrenMap.get(parentElement);
				if (children != null) {
					return !children.isEmpty();
				}
			} else if (parentElement instanceof DocumentSymbolWithFile) {
				DocumentSymbolWithFile element = (DocumentSymbolWithFile) parentElement;
				List<DocumentSymbol> children = element.symbol.getChildren();
				if (children != null) {
					return !children.isEmpty();
				}
			}
		}
		return false;
	}

	public Object getParent(Object element) {
		if (element instanceof SymbolInformation) {
			for(Map.Entry<SymbolInformation, List<SymbolInformation>> entry: childrenMap.entrySet()) {
				if(entry.getValue().contains(element)) {
					return entry.getKey();
				}
			}
		} else if (element instanceof DocumentSymbol) {
			return parent.get(element);
		} else if (element instanceof DocumentSymbolWithFile) {
			DocumentSymbol parentSymbol = parent.get(element);
			final IFile theFile = this.file;
			if (parentSymbol != null && theFile != null) {
				return new DocumentSymbolWithFile(parentSymbol, theFile);
			}
		}
		return null;
	}

	public void setFile(IFile file) {
		this.file = file;
	}

	public TreePath toUpdatedSymbol(TreePath initialSymbol) {
		List<Object> res = new ArrayList<>(initialSymbol.getSegmentCount());
		Object currentSymbol = null;
		for (int i = 0; i < initialSymbol.getSegmentCount(); i++) {
			String name = getName(initialSymbol.getSegment(i));
			Object[] currentChildren = (currentSymbol == null ? getElements() : getChildren(currentSymbol));
			currentSymbol = Arrays.stream(currentChildren).filter(child -> Objects.equals(getName(child), name)).findAny().orElse(null);
			if (currentSymbol == null) {
				return null;
			}
			res.add(currentSymbol);
		}
		return new TreePath(res.toArray(Object[]::new));
	}

	private String getName(Object segment) {
		if (segment instanceof DocumentSymbolWithFile) {
			segment = ((DocumentSymbolWithFile)segment).symbol;
		}
		if (segment instanceof DocumentSymbol) {
			return ((DocumentSymbol)segment).getName();
		}
		return null;
	}

}
