/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.languageserver.languages.csharp;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.rules.DefaultDamagerRepairer;
import org.eclipse.jface.text.rules.ICharacterScanner;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.IWordDetector;
import org.eclipse.jface.text.rules.MultiLineRule;
import org.eclipse.jface.text.rules.NumberRule;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.SingleLineRule;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.rules.WordRule;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;

public class CSharpKeywordsHighlighter extends PresentationReconciler {

	private Set<String> keywords = new HashSet<>(Arrays.asList(new String[] {
		"abstract", 
		"as",
		"base",
		"bool",
		"break",
		"byte",
		"case",
		"catch",
		"char",
		"checked",
		"class",
		"const",
		"continue",
		"decimal",
		"default",
		"delegate",
		"do",
		"double",
		"else",
		"enum",
		"event",
		"explicit",
		"extern",
		"false",
		"finally",
		"fixed",
		"float",
		"for",
		"foreach",
		"goto",
		"if",
		"implicit",
		"in",
		"int",
		"interface",
		"internal",
		"is",
		"lock",
		"long",
		"namespace",
		"new",
		"null",
		"object",
		"operator",
		"out",
		"override",
		"params",
		"private",
		"protected",
		"public",
		"readonly",
		"ref",
		"return",
		"sbyte",
		"sealed",
		"short",
		"sizeof",
		"stackalloc",
		"static",
		"string",
		"struct",
		"switch",
		"this",
		"throw",
		"true",
		"try",
		"typeof",
		"uint",
		"ulong",
		"unchecked",
		"unsafe",
		"ushort",
		"using",
		"virtual",
		"void",
		"volatile",
		"while",
	}));
	
	private Set<String> contextualKeywords = new HashSet<>(Arrays.asList(new String[] {
		"add",
		"alias",
		"ascending",
		"async",
		"await",
		"descending",
		"dynamic",
		"from",
		"get",
		"global",
		"group",
		"into",
		"join",
		"let",
		"orderby",
		"partial",
		"remove",
		"select",
		"set",
		"value",
		"var",
		"where",
		"yield",
	}));

	public CSharpKeywordsHighlighter() {
		RuleBasedScanner scanner= new RuleBasedScanner();
		Token keywordToken = new Token(new TextAttribute(Display.getDefault().getSystemColor(SWT.COLOR_DARK_BLUE), null, SWT.BOLD));
		Set<Character> allKeyWordChars = new HashSet<Character>();
		Set<Character> allKeyWord1stChar = new HashSet<Character>();
		for (String keyword : keywords) {
			if (!keyword.isEmpty()) {
				allKeyWord1stChar.add(keyword.charAt(0));
				for (char c : keyword.toCharArray()) {
					allKeyWordChars.add(Character.valueOf(c));
				}
			}
		}
		WordRule keywordRule = new WordRule(new IWordDetector() {
			@Override
			public boolean isWordStart(char c) {
				return allKeyWord1stChar.contains(c);
			}
			
			@Override
			public boolean isWordPart(char c) {
				return allKeyWordChars.contains(c);
			}
		});
		for (String keyword : keywords) {
			keywordRule.addWord(keyword, keywordToken);
		}
		Token identifierToken = new Token(new TextAttribute(Display.getDefault().getSystemColor(SWT.COLOR_BLACK), null, SWT.NONE));
		WordRule identifierRule = new WordRule(new IWordDetector() {
			@Override
			public boolean isWordStart(char c) {
				return Character.isJavaIdentifierStart(c);
			}
			
			@Override
			public boolean isWordPart(char c) {
				return Character.isJavaIdentifierPart(c);
			}
		}, identifierToken);
		Token literalToken = new Token(new TextAttribute(Display.getDefault().getSystemColor(SWT.COLOR_BLUE), null, SWT.NONE));
		Token commentsToken = new Token(new TextAttribute(Display.getDefault().getSystemColor(SWT.COLOR_DARK_GRAY), null, SWT.NONE));
		scanner.setRules(new IRule[] {
			new SingleLineRule("//", null, commentsToken),
			new MultiLineRule("/*", "*/", commentsToken),
			new SingleLineRule("\"", "\"", literalToken),
			new SingleLineRule("'", "'", literalToken),
			new MultiLineRule("@\"", "\"", literalToken, '"'),
			keywordRule,
			identifierRule,
			new NumberRule(literalToken),
		});
		DefaultDamagerRepairer dr= new DefaultDamagerRepairer(scanner);
		this.setDamager(dr, IDocument.DEFAULT_CONTENT_TYPE);
		this.setRepairer(dr, IDocument.DEFAULT_CONTENT_TYPE);
	}
}
