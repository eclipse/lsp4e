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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;

import org.eclipse.core.resources.IFile;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class SymbolsModel {

	private static final SymbolInformation ROOT_SYMBOL_INFORMATION = new SymbolInformation();
	private static final Object[] EMPTY = new Object[0];

	private Map<SymbolInformation, List<SymbolInformation>> childrenMap = new HashMap<>();
	private List<DocumentSymbol> rootSymbols = new ArrayList<>();
	private Map<DocumentSymbol, DocumentSymbol> parent = new HashMap<>();
	private IFile file;

	public static class DocumentSymbolWithFile {
		public final DocumentSymbol symbol;
		public final IFile file;

		public DocumentSymbolWithFile(DocumentSymbol symbol, IFile file) {
			this.symbol = symbol;
			this.file = file;
		}
	}

	public boolean update(List<Either<SymbolInformation, DocumentSymbol>> response) {
		// TODO update model only on real change
		childrenMap.clear();
		rootSymbols.clear();
		parent.clear();
		if (response != null && !response.isEmpty()) {
			Collections.sort(response, Comparator.comparing(
					either -> either.isLeft() ? either.getLeft().getLocation().getRange().getStart()
							: either.getRight().getRange().getStart(),
					// strange need to cast here, could be a JDT compiler issue
					Comparator.comparingInt(pos -> ((Position) pos).getLine())
							.thenComparingInt(pos -> ((Position) pos).getCharacter())));

			Stack<SymbolInformation> parentStack = new Stack<>();
			parentStack.push(ROOT_SYMBOL_INFORMATION);
			SymbolInformation previousSymbol = null;
			for (Either<SymbolInformation, DocumentSymbol> either : response) {
				if (either.isLeft()) {
					SymbolInformation symbol = either.getLeft();
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
				} else if (either.isRight()) {
					rootSymbols.add(either.getRight());
				}
			}
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

	private void addChild(SymbolInformation parent, SymbolInformation child) {
		List<SymbolInformation> children = childrenMap.computeIfAbsent(parent, key -> new ArrayList<>());
		children.add(child);
	}

	public Object[] getElements() {
		List<Object> res = new ArrayList<>();
		res.addAll(Arrays.asList(getChildren(ROOT_SYMBOL_INFORMATION)));
		rootSymbols.stream().map(symbol -> new DocumentSymbolWithFile(symbol, this.file)).forEach(res::add);
		return res.toArray(new Object[res.size()]);
	}

	public Object[] getChildren(Object parentElement) {
		if (parentElement != null) {
			if (parentElement instanceof SymbolInformation) {
				List<SymbolInformation> children = childrenMap.get(parentElement);
				if (children != null) {
					return children.toArray();
				}
			} else if (parentElement instanceof DocumentSymbolWithFile) {
				DocumentSymbolWithFile element = (DocumentSymbolWithFile) parentElement;
				return element.symbol.getChildren().stream()
						.map(symbol -> new DocumentSymbolWithFile(symbol, element.file)).toArray();
			}
		}
		return EMPTY;
	}

	public Object getParent(Object element) {
		if (element instanceof SymbolInformation) {
			Optional<SymbolInformation> result = childrenMap.keySet().stream().filter(parent -> {
				List<SymbolInformation> children = childrenMap.get(parent);
				return children == null ? false : children.contains(element);
			}).findFirst();
			return result.isPresent() ? result.get() : null;
		} else if (element instanceof DocumentSymbol) {
			return parent.get(element);
		}
		return null;
	}

	public void setFile(IFile file) {
		this.file = file;
	}

}
