/*******************************************************************************
 * Copyright (c) 2016, 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Christoph Kaser (ICONPARC GmbH) - initial implementation
 *******************************************************************************/

package org.eclipse.lsp4e.operations.completion;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.link.LinkedPosition;
import org.eclipse.jface.text.link.ProposalPosition;

import java.util.*;
import java.util.function.Function;


/**
 * A parser for the completion insert text in
 * <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#snippet_syntax">snippet syntax</a>
 */
class CompletionSnippetParser {
	private final IDocument document;
	private final String snippetText;
	private final int insertionOffset;
	private final LinkedHashMap<String, List<LinkedPosition>> linkedPositions = new LinkedHashMap<>();
	private final Function<String, String> getVariableValue;
	private int snippetOffset = 0;
	private int snippetToDocumentOffset = 0;
	private boolean dontAddLinkedPositions = false;


	/**
	 * Creates a new snippet parser
	 * @param document the document that this completion will be applied to
	 * @param snippetText the text to parse
	 * @param insertionOffset the document offset this completion will be applied to
	 * @param getVariableValue a function that resolves variable values
	 */
	public CompletionSnippetParser(IDocument document, String snippetText, int insertionOffset, Function<String, String> getVariableValue) {
		this.document = document;
		this.snippetText = snippetText;
		this.insertionOffset = insertionOffset;
		this.getVariableValue = getVariableValue;
	}

	/**
	 * Parses the insertText
	 * @return the text to apply to the editor
	 */
	public String parse() {
		StringBuilder insertTextBuilder = new StringBuilder(snippetText.length());
		while (hasRemaining()) {
			char current = readChar();
			switch (current) {
				default -> insertTextBuilder.append(current);
				case '\\' -> {
					if (!hasRemaining()) {
						insertTextBuilder.append(current);
					} else {
						insertTextBuilder.append(readChar());
						snippetToDocumentOffset++;
					}
				}
				case '$' -> {
					int _snippetOffset = snippetOffset;
					int _snippetToDocumentOffset = snippetToDocumentOffset;
					try {
						String value = parseDollarExpression();
						insertTextBuilder.append(value);
					} catch (DollarExpressionParseException e) {
						// unparseable expression is handled 'as-is'
						snippetOffset = _snippetOffset;
						snippetToDocumentOffset = _snippetToDocumentOffset;
						insertTextBuilder.append('$');
					}
				}
			}
		}
		return insertTextBuilder.toString();
	}

	/**
	 * @return a map of LinkedPositions that were defined by the snippet
	 */
	public Map<String, List<LinkedPosition>> getLinkedPositions() {
		return linkedPositions;
	}

	private String parseDollarExpression() throws DollarExpressionParseException {
		if (!hasRemaining()) {
			throw new DollarExpressionParseException();
		}
		char firstChar = peekChar();
		if (Character.isDigit(firstChar)) {
			// A tabstop position like $1
			String key = readNumberKey();
			snippetToDocumentOffset += key.length() + 1;
			LinkedPosition position = new LinkedPosition(document, insertionOffset + snippetOffset - snippetToDocumentOffset, 0);
			addLinkedPosition(key, position);
			return ""; //$NON-NLS-1$
		} else if (isCharacterForVariableName(firstChar)) {
			// A Variable like $TM_LINE_NUMBER
			String key = readVariableKey();
			String value = getVariableValue.apply(key);
			snippetToDocumentOffset += key.length() + 1 - value.length();
			return value;
		} else if (firstChar == '{') {
			return parseDollarExpressionInBrackets();
		} else {
			throw new DollarExpressionParseException();
		}
	}

	/**
	 * Parses an expression in brackets like ${1|value} or ${TM_SELECTED_TEXT:default}
	 */
	private String parseDollarExpressionInBrackets() throws DollarExpressionParseException {
		if (readChar() != '{') {
			// This method must be called on a bracket character
			throw new IllegalStateException();
		}
		if (!hasRemaining()) {
			throw new DollarExpressionParseException();
		}
		char firstKeyChar = peekChar();
		if (Character.isDigit(firstKeyChar)) {
			// tabstop, placeholder or choice
			return parseTabStopInBrackets();
		} else if (isCharacterForVariableName(firstKeyChar)) {
			// a variable
			return parseVariableExpressionInBrackets();
		} else {
			throw new DollarExpressionParseException();
		}
	}

	private String parseVariableExpressionInBrackets() throws DollarExpressionParseException {
		int dollarOffset = snippetOffset - 2;
		String key = readVariableKey();
		if (!hasRemaining()) {
			throw new DollarExpressionParseException();
		}
		char postKeyChar = readChar();
		String defaultValue = ""; //$NON-NLS-1$

		switch (postKeyChar) {
			case '}' -> {
			}
			case ':' -> {
				// default Value
				defaultValue = readTextValue();
			}
			case '/' -> {
				// TODO: Format strings are unsupported for now, simple read and ignore them
				readTextValue();
			}
			default -> {
				throw new DollarExpressionParseException();
			}
		}
		String value = getVariableValue.apply(key);
		if (value.isEmpty()) {
			value = defaultValue;
		}
		snippetToDocumentOffset += snippetOffset - dollarOffset - value.length();
		return value;
	}

	private String parseTabStopInBrackets() throws DollarExpressionParseException {
		int dollarOffset = snippetOffset - 2;
		String key = readNumberKey();
		if (!hasRemaining()) {
			throw new DollarExpressionParseException();
		}
		char postKeyChar = readChar();
		List<String> valueList;
		switch (postKeyChar) {
			case '}' -> {
				valueList = Collections.emptyList();
			}
			case ':' -> {
				valueList = List.of(readTextValue());
			}
			case '|' -> {
				valueList = readChoiceValues();
			}
			default -> {
				throw new DollarExpressionParseException();
			}
		}
		LinkedPosition position;
		String defaultProposal;
		if (!valueList.isEmpty()) {
			defaultProposal = valueList.get(0);
			int replacementOffset = insertionOffset + dollarOffset - snippetToDocumentOffset;
			ICompletionProposal[] proposals = valueList.stream().map(string ->
					new CompletionProposal(string, replacementOffset, defaultProposal.length(), replacementOffset + string.length())
			).toArray(ICompletionProposal[]::new);
			position = new ProposalPosition(document, insertionOffset + dollarOffset - snippetToDocumentOffset, defaultProposal.length(), proposals);
		} else {
			defaultProposal = ""; //$NON-NLS-1$
			position = new LinkedPosition(document, insertionOffset + dollarOffset - snippetToDocumentOffset, 0);
		}
		addLinkedPosition(key, position);
		snippetToDocumentOffset += snippetOffset - dollarOffset - defaultProposal.length();
		return defaultProposal;
	}

	private List<String> readChoiceValues() throws DollarExpressionParseException {
		List<String> valueList = new ArrayList<>();
		StringBuilder valueBuilder = new StringBuilder();
		while (true) {
			if (!hasRemaining()) {
				throw new DollarExpressionParseException();
			}
			char c = readChar();
			switch (c) {
				default -> valueBuilder.append(c);
				case ',' -> {
					valueList.add(valueBuilder.toString());
					valueBuilder.setLength(0);
				}
				case '\\' -> {
					if (!hasRemaining()) {
						throw new DollarExpressionParseException();
					}
					valueBuilder.append(readChar());
				}
				case '}' -> {
					// a choice needs to end on |}
					throw new DollarExpressionParseException();
				}
				case '|' -> {
					if (!hasRemaining() || readChar() != '}') {
						throw new DollarExpressionParseException();
					}
					valueList.add(valueBuilder.toString());
					return valueList;
				}
			}
		}
	}

	private String readTextValue() throws DollarExpressionParseException {
		StringBuilder valueBuilder = new StringBuilder();
		while (true) {
			if (!hasRemaining()) {
				throw new DollarExpressionParseException();
			}
			char c = readChar();
			switch (c) {
				default -> valueBuilder.append(c);
				case '\\' -> {
					if (!hasRemaining()) {
						throw new DollarExpressionParseException();
					}
					valueBuilder.append(readChar());
				}
				case '$' -> {
					int _snippetOffset = snippetOffset;
					int _snippetToDocumentOffset = snippetToDocumentOffset;
					boolean _dontAddLinkedPositions = dontAddLinkedPositions;
					// For now, we don't support nested linked positions
					dontAddLinkedPositions = true;
					try {
						String value = parseDollarExpression();
						valueBuilder.append(value);
					} catch (DollarExpressionParseException e) {
						// unparseable expression is handled 'as-is'
						snippetOffset = _snippetOffset;
						valueBuilder.append('$');
					} finally {
						dontAddLinkedPositions = _dontAddLinkedPositions;
						snippetToDocumentOffset = _snippetToDocumentOffset; // Reset the snippetToDocumentOffset, since the value was not added to the insertText yet
					}
				}
				case '}' -> {
					return valueBuilder.toString();
				}
			}
		}
	}

	private void addLinkedPosition(String key, LinkedPosition position) {
		if (!dontAddLinkedPositions) {
			linkedPositions.computeIfAbsent(key, whatever -> new ArrayList<>()).add(position);
		}
	}

	private boolean hasRemaining() {
		return snippetOffset < snippetText.length();
	}

	private char peekChar() {
		return snippetText.charAt(snippetOffset);
	}

	private char readChar() {
		char retval = peekChar();
		snippetOffset++;
		return retval;
	}

	private String readNumberKey() {
		StringBuilder keyBuilder = new StringBuilder();
		while (hasRemaining() && Character.isDigit(peekChar())) {
			keyBuilder.append(readChar());
		}
		return keyBuilder.toString();
	}

	private String readVariableKey() {
		StringBuilder keyBuilder = new StringBuilder();
		while (hasRemaining() && isCharacterForVariableName(peekChar())) {
			keyBuilder.append(readChar());
		}
		return keyBuilder.toString();
	}

	private boolean isCharacterForVariableName(char c) {
		return ('0' <= c && c <= '9') || ('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z') || c == '_';
	}

	private static class DollarExpressionParseException extends Exception {	}
}
