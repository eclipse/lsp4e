/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
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
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class CodeActionTests {
	
	@Rule public NoErrorLoggedRule rule = new NoErrorLoggedRule(LanguageServerPlugin.getDefault().getLog());

	@Before
	public void setUp() throws CoreException {
		MockLanguageSever.reset();
	}

	@Test
	public void testCodeActionsClientCommandForTextEdit() throws CoreException {
		IProject p = TestUtils.createProject(getClass().getSimpleName() + System.currentTimeMillis());
		IFile f = TestUtils.createUniqueTestFile(p, "error");
		MockLanguageSever.INSTANCE.setCodeActions(Collections.singletonList(Either.forLeft(new Command(
				"fixme",
				"edit",
				Collections.singletonList(
					new TextEdit(
							new Range(new Position(0, 0), new Position(0, 5)),
							"fixed"))
				)
			)
		));
		MockLanguageSever.INSTANCE.setDiagnostics(Collections.singletonList(
				new Diagnostic(new Range(new Position(0, 0), new Position(0, 5)), "error", DiagnosticSeverity.Error, null)));
		AbstractTextEditor editor = (AbstractTextEditor)TestUtils.openEditor(f);
		try {
			IMarker m = assertDiagnostics(f, "error", "fixme");
			assertResolution(editor, m, "fixed");
		} finally {
			editor.close(false);
			p.delete(true, new NullProgressMonitor());
		}
	}

	@Test
	public void testCodeActionsClientCommandForWorkspaceEdit() throws CoreException {
		IProject p = TestUtils.createProject(getClass().getSimpleName() + System.currentTimeMillis());
		IFile f = TestUtils.createUniqueTestFile(p, "error");

		TextEdit tEdit = new TextEdit(new Range(new Position(0, 0), new Position(0, 5)), "fixed");
		WorkspaceEdit wEdit = new WorkspaceEdit(Collections.singletonMap(f.getLocationURI().toString(), Collections.singletonList(tEdit)));
		MockLanguageSever.INSTANCE.setCodeActions(Collections
				.singletonList(Either.forLeft(new Command(
				"fixme",
				"edit",
				Collections.singletonList(wEdit))
			)
		));
		MockLanguageSever.INSTANCE.setDiagnostics(Collections.singletonList(
				new Diagnostic(new Range(new Position(0, 0), new Position(0, 5)), "error", DiagnosticSeverity.Error, null)));
		AbstractTextEditor editor = (AbstractTextEditor)TestUtils.openEditor(f);
		try {
			IMarker m = assertDiagnostics(f, "error", "fixme");
			assertResolution(editor, m, "fixed");
		} finally {
			editor.close(false);
			p.delete(true, new NullProgressMonitor());
		}
	}

	@Test
	public void testCodeActionLiteralWorkspaceEdit() throws CoreException {
		IProject p = TestUtils.createProject(getClass().getSimpleName() + System.currentTimeMillis());
		IFile f = TestUtils.createUniqueTestFile(p, "error");

		TextEdit tEdit = new TextEdit(new Range(new Position(0, 0), new Position(0, 5)), "fixed");
		WorkspaceEdit wEdit = new WorkspaceEdit(Collections.singletonMap(f.getLocationURI().toString(), Collections.singletonList(tEdit)));
		MockLanguageSever.INSTANCE.setCodeActions(Collections.singletonList(Either.forRight(new CodeAction(
				"fixme", "fix", Collections.emptyList(), wEdit, null))));
		MockLanguageSever.INSTANCE.setDiagnostics(Collections.singletonList(
				new Diagnostic(new Range(new Position(0, 0), new Position(0, 5)), "error", DiagnosticSeverity.Error, null)));
		AbstractTextEditor editor = (AbstractTextEditor)TestUtils.openEditor(f);
		try {
			IMarker m = assertDiagnostics(f, "error", "fixme");
			assertResolution(editor, m, "fixed");
		} finally {
			editor.close(false);
			p.delete(true, new NullProgressMonitor());
		}
	}

	@Test
	public void testCodeActionLiteralWithClientCommand() throws CoreException {
		IProject p = TestUtils.createProject(getClass().getSimpleName() + System.currentTimeMillis());
		IFile f = TestUtils.createUniqueTestFile(p, "error");

		TextEdit tEdit = new TextEdit(new Range(new Position(0, 0), new Position(0, 5)), "fixed");
		WorkspaceEdit wEdit = new WorkspaceEdit(Collections.singletonMap(f.getLocationURI().toString(), Collections.singletonList(tEdit)));
		MockLanguageSever.INSTANCE.setCodeActions(Collections.singletonList(Either.forRight(new CodeAction(
				"fixme", "fix", Collections.emptyList(), null,
				new Command("editCommand", "mockEditCommand", Collections.singletonList(wEdit))
		))));
		MockLanguageSever.INSTANCE.setDiagnostics(Collections.singletonList(
				new Diagnostic(new Range(new Position(0, 0), new Position(0, 5)), "error", DiagnosticSeverity.Error, null)));
		AbstractTextEditor editor = (AbstractTextEditor)TestUtils.openEditor(f);
		try {
			IMarker m = assertDiagnostics(f, "error", "fixme");
			assertResolution(editor, m, "fixed");
		} finally {
			editor.close(false);
			p.delete(true, new NullProgressMonitor());
		}
	}

	public static IMarker assertDiagnostics(IFile f, String markerMessage, String resolutionLabel) throws CoreException {
		new DisplayHelper() {
			@Override
			protected boolean condition() {
				try {
					IMarker [] markers = f.findMarkers(LSPDiagnosticsToMarkers.LS_DIAGNOSTIC_MARKER_TYPE, true, IResource.DEPTH_ZERO);
					// seems we need the second condition as the attributes aren't loaded immediately
					return markers.length > 0 && markers[0].getAttribute(IMarker.MESSAGE) != null;
				} catch (CoreException e) {
					return false;
				}
			}
		}.waitForCondition(Display.getCurrent(), 2000);
		final IMarker m = f.findMarkers(LSPDiagnosticsToMarkers.LS_DIAGNOSTIC_MARKER_TYPE, true, IResource.DEPTH_ZERO)[0];
		Assert.assertEquals(markerMessage, m.getAttribute(IMarker.MESSAGE));
		new DisplayHelper() {
			@Override
			protected boolean condition() {
				return
					IDE.getMarkerHelpRegistry().hasResolutions(m) &&
					// need this 2nd condition because async introduces a dummy resolution that's not the one we want
					IDE.getMarkerHelpRegistry().getResolutions(m)[0].getLabel().equals(resolutionLabel);
			}
		}.waitForCondition(Display.getCurrent(), 2000);
		return m;
	}

	public static void assertResolution(AbstractTextEditor editor, IMarker m, String newText) {
		IDE.getMarkerHelpRegistry().getResolutions(m)[0].run(m);
		new DisplayHelper() {
			@Override
			protected boolean condition() {
				return newText.equals(editor.getDocumentProvider().getDocument(editor.getEditorInput()).get());
			}
		}.waitForCondition(Display.getCurrent(), 1000);
		Assert.assertEquals(newText, editor.getDocumentProvider().getDocument(editor.getEditorInput()).get());
		new DisplayHelper() {
			@Override
			protected boolean condition() {
				return newText.equals(((StyledText) editor.getAdapter(Control.class)).getText());
			}
		}.waitForCondition(Display.getCurrent(), 1000);
		Assert.assertEquals(newText, ((StyledText) editor.getAdapter(Control.class)).getText());
	}

}
