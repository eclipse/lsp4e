/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.codeactions;

import java.util.Collections;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.tests.util.DisplayHelper;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.operations.diagnostics.LSPDiagnosticsToMarkers;
import org.eclipse.lsp4e.test.NoErrorLoggedRule;
import org.eclipse.lsp4e.test.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageSever;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class CodeActionTests {
	
	@Rule public NoErrorLoggedRule rule = new NoErrorLoggedRule(LanguageServerPlugin.getDefault().getLog());

	@Test
	public void testCodeActions() throws CoreException {
		IProject p = TestUtils.createProject(getClass().getSimpleName() + System.currentTimeMillis());
		IFile f = TestUtils.createUniqueTestFile(p, "error");
		MockLanguageSever.INSTANCE.setCodeActions(Collections.singletonList(new Command(
				"fixme",
				"edit",
				Collections.singletonList(
					new TextEdit(
							new Range(new Position(0, 0), new Position(0, 5)),
							"fixed"))
				)
			)
		);
		MockLanguageSever.INSTANCE.setDiagnostics(Collections.singletonList(
				new Diagnostic(new Range(new Position(0, 0), new Position(0, 5)), "error", DiagnosticSeverity.Error, null)));
		AbstractTextEditor editor = (AbstractTextEditor)TestUtils.openEditor(f);
		try {
			new DisplayHelper() {
				@Override
				protected boolean condition() {
					try {
						return f.findMarkers(LSPDiagnosticsToMarkers.LS_DIAGNOSTIC_MARKER_TYPE, true, IResource.DEPTH_ZERO).length > 0;
					} catch (CoreException e) {
						return false;
					}
				}
			}.waitForCondition(Display.getCurrent(), 2000);
			final IMarker m = f.findMarkers(LSPDiagnosticsToMarkers.LS_DIAGNOSTIC_MARKER_TYPE, true, IResource.DEPTH_ZERO)[0];
			Assert.assertEquals("error", m.getAttribute(IMarker.MESSAGE));
			new DisplayHelper() {
				@Override
				protected boolean condition() {
					return IDE.getMarkerHelpRegistry().hasResolutions(m);
				}
			}.waitForCondition(Display.getCurrent(), 20000);
			IDE.getMarkerHelpRegistry().getResolutions(m)[0].run(m);
			new DisplayHelper() {
				@Override
				protected boolean condition() {
					return "fixed".equals(editor.getDocumentProvider().getDocument(editor.getEditorInput()).get());
				}
			}.waitForCondition(Display.getCurrent(), 1000);
			Assert.assertEquals("fixed", ((StyledText)editor.getAdapter(Control.class)).getText());
			Assert.assertEquals("fixed", editor.getDocumentProvider().getDocument(editor.getEditorInput()).get());
		} finally {
			editor.close(false);
			p.delete(true, new NullProgressMonitor());
		}
	}
}
