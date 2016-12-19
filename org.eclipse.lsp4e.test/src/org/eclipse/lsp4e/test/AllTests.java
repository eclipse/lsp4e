/*******************************************************************************
 * Copyright (c) 2016-2017 Rogue Wave Software Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *  Mickael Istria (Red Hat Inc.) - Added some suites
 *******************************************************************************/
package org.eclipse.lsp4e.test;

import org.eclipse.lsp4e.test.completion.CompletionTest;
import org.eclipse.lsp4e.test.definition.DefinitionTest;
import org.eclipse.lsp4e.test.diagnostics.DiagnosticsTest;
import org.eclipse.lsp4e.test.document.LSPEclipseUtilsTest;
import org.eclipse.lsp4e.test.edit.DocumentDidChangeTest;
import org.eclipse.lsp4e.test.hover.HoverTest;
import org.eclipse.lsp4e.test.references.FindReferencesTest;
import org.eclipse.lsp4e.test.symbols.SymbolsModelTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({
	LanguageServiceAccessorTest.class,
	CompletionTest.class,
	DocumentDidChangeTest.class,
	SymbolsModelTest.class,
	LSPEclipseUtilsTest.class,
	HoverTest.class,
	DefinitionTest.class,
	DiagnosticsTest.class,
	FindReferencesTest.class
})
public class AllTests {

}
