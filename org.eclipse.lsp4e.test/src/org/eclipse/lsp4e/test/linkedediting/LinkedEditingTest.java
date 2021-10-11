/*******************************************************************************
 * Copyright (c) 2021 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Victor Rubezhny (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.linkedediting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.tests.util.DisplayHelper;
import org.eclipse.lsp4e.operations.linkedediting.LSPLinkedEditingReconcilingStrategy;
import org.eclipse.lsp4e.test.AllCleanRule;
import org.eclipse.lsp4e.test.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.LinkedEditingRanges;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.swt.widgets.Display;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class LinkedEditingTest {

	@Rule public AllCleanRule clear = new AllCleanRule();
	private IProject project;

	@Before
	public void setUp() throws CoreException {
		project = TestUtils.createProject("LinkedEditingTest" + System.currentTimeMillis());
	}

	@Test
	public void testLinkedEditingHighlight() throws CoreException, InvocationTargetException {
		List<Range> ranges = new ArrayList<>();
		ranges.add(new Range(new Position(1, 3), new Position(1, 7)));
		ranges.add(new Range(new Position(3, 4), new Position(3, 8)));
		
		LinkedEditingRanges linkkedEditingRanges = new LinkedEditingRanges(ranges);
		MockLanguageServer.INSTANCE.setLinkedEditingRanges(linkkedEditingRanges);

		IFile testFile = TestUtils.createUniqueTestFile(project, "<html>\n  <body>\n    a body text\n  </body>\n</html>");
		ITextViewer viewer = TestUtils.openTextViewer(testFile);

		viewer.getTextWidget().setCaretOffset(11); 

		if (!(viewer instanceof ISourceViewer)) {
			Assert.fail();
		}

		ISourceViewer sourceViewer = (ISourceViewer) viewer;

		viewer.getTextWidget().setSelection(11); // 10-14 <body|>
		
		Map<org.eclipse.jface.text.Position, Annotation> annotations = new HashMap<>();

		new DisplayHelper() {
			@Override
			protected boolean condition() {
				Iterator<Annotation> iterator = sourceViewer.getAnnotationModel().getAnnotationIterator();
				while (iterator.hasNext()) {
					Annotation annotation = iterator.next();
					if (LSPLinkedEditingReconcilingStrategy.LINKEDEDITING_ANNOTATION_TYPE.equals(annotation.getType())) {
						return true;
					}
				}
				return false;
			}
		}.waitForCondition(Display.getCurrent(), 3000);

		IAnnotationModel model = sourceViewer.getAnnotationModel();
		final Iterator<Annotation> iterator = model.getAnnotationIterator();
		while (iterator.hasNext()) {
			Annotation annotation = iterator.next();
			annotations.put(model.getPosition(annotation), annotation);
		}

		Annotation annotation = annotations.get(new org.eclipse.jface.text.Position(10, 4));
		Assert.assertNotNull(annotation);
		assertEquals(LSPLinkedEditingReconcilingStrategy.LINKEDEDITING_ANNOTATION_TYPE, annotation.getType());

		Assert.assertTrue(viewer.isEditable());

		try {
			// Test initial insert
			CompletableFuture<DidChangeTextDocumentParams> didChangeExpectation = new CompletableFuture<DidChangeTextDocumentParams>();
			MockLanguageServer.INSTANCE.setDidChangeCallback(didChangeExpectation);
			String text = "w";
			String expectedChangeText = viewer.getDocument().get().replace("body>", "wbody>");

			viewer.getTextWidget().replaceTextRange(10, 0, text);
			
			DidChangeTextDocumentParams lastChange = didChangeExpectation.get(1000, TimeUnit.MILLISECONDS);
			assertNotNull(lastChange.getContentChanges());
			assertEquals(1, lastChange.getContentChanges().size());
			TextDocumentContentChangeEvent change0 = lastChange.getContentChanges().get(0);
			assertEquals(expectedChangeText, change0.getText());
			assertEquals(expectedChangeText, viewer.getDocument().get());
			
			// Test additional insert
			didChangeExpectation = new CompletableFuture<DidChangeTextDocumentParams>();
			MockLanguageServer.INSTANCE.setDidChangeCallback(didChangeExpectation);
			expectedChangeText = viewer.getDocument().get().replace("body>", "wbody>");

			viewer.getTextWidget().replaceTextRange(11, 0, text);
			
			lastChange = didChangeExpectation.get(1000, TimeUnit.MILLISECONDS);
			assertNotNull(lastChange.getContentChanges());
			assertEquals(1, lastChange.getContentChanges().size());
			change0 = lastChange.getContentChanges().get(0);
			assertEquals(expectedChangeText, change0.getText());
			assertEquals(expectedChangeText, viewer.getDocument().get());
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
}
