/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Victor Rubezhny (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.edit;

import static org.junit.Assert.assertTrue;

import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.text.TextViewer;
import org.eclipse.jface.text.tests.util.DisplayHelper;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.test.AllCleanRule;
import org.eclipse.lsp4e.test.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.LinkedEditingRanges;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class DocumentEditAndUndoTest {

	@Rule public AllCleanRule clear = new AllCleanRule();
	private IProject project;
	private Shell shell;

	@Before
	public void setUp() throws CoreException {
		project =  TestUtils.createProject(getClass().getName() + System.currentTimeMillis());
		shell= new Shell();
	}

	@Test
	public void testDocumentEditAndUndo() throws Exception {
		MockLanguageServer.INSTANCE.setLinkedEditingRanges(new LinkedEditingRanges(List.of(
				new Range(new Position(0, 1), new Position(0, 2)),
				new Range(new Position(0, 5), new Position(0, 6))),
				"[:A-Z_a-z\\u00C0-\\u00D6\\u00D8-\\u00F6\\u00F8-\\u02ff\\u0370-\\u037d\\u037f-\\u1fff\\u200c\\u200d\\u2070-\\u218f\\u2c00-\\u2fef\\u3001-\\udfff\\uf900-\\ufdcf\\ufdf0-\\ufffd\\u10000-\\uEFFFF][:A-Z_a-z\\u00C0-\\u00D6\\u00D8-\\u00F6\\u00F8-\\u02ff\\u0370-\\u037d\\u037f-\\u1fff\\u200c\\u200d\\u2070-\\u218f\\u2c00-\\u2fef\\u3001-\\udfff\\uf900-\\ufdcf\\ufdf0-\\ufffd\\u10000-\\uEFFFF\\-\\.0-9\\u00b7\\u0300-\\u036f\\u203f-\\u2040]*"));

		IFile testFile = TestUtils.createUniqueTestFile(project, "<a></a>");
		IEditorPart editor = TestUtils.openEditor(testFile);
		ITextViewer viewer = LSPEclipseUtils.getTextViewer(editor);
		// Force LS to initialize and open file
		LanguageServiceAccessor.getLanguageServers(LSPEclipseUtils.getDocument(testFile), capabilites -> Boolean.TRUE);
		
		System.out.println("Document initial:\t[" + viewer.getDocument().get() + "]");
	
		// Initialize Linked Editing by setting up the text cursor position
		viewer.getTextWidget().setCaretOffset(2); 
		((TextViewer)viewer).setSelection(new TextSelection(viewer.getDocument(), 2, 0), true); 
		System.out.println("Document viewer.setSelection(2, 2)");

		Display display= shell.getDisplay();
		Control control= viewer.getTextWidget();
		DisplayHelper.sleep(display, 2000); // Give some time to the editor to update 
		display.asyncExec(new Runnable() {
			
			@Override
			public void run() {
				type(control, 'b');
				type(control, 'c');
			}

			private void type(Control control, char c) {
				control.forceFocus();
				Event keyEvent= new Event();
				keyEvent.widget= control;
				keyEvent.type= SWT.KeyDown;
				keyEvent.character= c;
				keyEvent.keyCode= c;
				display.post(keyEvent);
				keyEvent.type= SWT.KeyUp;
				display.post(keyEvent);
			}
		});
		assertTrue("Document isn't correctly changed", new DisplayHelper() {
			@Override
			protected boolean condition() {
				return viewer.getDocument().get().equals("<abc></abc>");
			}
		}.waitForCondition(display, 3000));
		ITextOperationTarget fOperationTarget= editor.getAdapter(ITextOperationTarget.class);
		BusyIndicator.showWhile(viewer.getTextWidget().getDisplay(),
				() -> fOperationTarget.doOperation(ITextOperationTarget.UNDO));
		
		System.out.println("Document restored:\t[" + viewer.getDocument().get() + "]");
		assertTrue("Document isn't correctly restored", new DisplayHelper() {
			@Override
			protected boolean condition() {
				return viewer.getDocument().get().equals("<a></a>");
			}
		}.waitForCondition(display, 3000));
	}
}
