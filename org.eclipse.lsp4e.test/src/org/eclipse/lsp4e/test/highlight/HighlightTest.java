/*******************************************************************************
 * Copyright (c) 2017 Rogue Wave Software Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.highlight;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.tests.util.DisplayHelper;
import org.eclipse.lsp4e.operations.highlight.HighlightReconciler;
import org.eclipse.lsp4e.test.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageSever;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.swt.widgets.Display;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.Version;

public class HighlightTest {

	private IProject project;

	@Before
	public void setUp() throws CoreException {
		project = TestUtils.createProject("HighlightTest" + System.currentTimeMillis());
	}

	@After
	public void tearDown() throws CoreException {
		project.delete(true, true, new NullProgressMonitor());
		MockLanguageSever.INSTANCE.shutdown();
	}

	@Test
	public void testHighlight() throws CoreException, InvocationTargetException {
		Bundle bundle = Platform.getBundle("org.eclipse.ui.genericeditor");
		Assume.assumeTrue(bundle.getVersion().compareTo(new Version(1, 1, 0)) >= 0);

		List<DocumentHighlight> highlights = new ArrayList<>();
		highlights.add(
				new DocumentHighlight(new Range(new Position(0, 2), new Position(0, 6)), DocumentHighlightKind.Read));
		highlights.add(
				new DocumentHighlight(new Range(new Position(0, 7), new Position(0, 12)), DocumentHighlightKind.Write));
		highlights.add(
				new DocumentHighlight(new Range(new Position(0, 13), new Position(0, 17)), DocumentHighlightKind.Text));
		MockLanguageSever.INSTANCE.setDocumentHighlights(highlights);

		IFile testFile = TestUtils.createUniqueTestFile(project, "  READ WRITE TEXT");
		ITextViewer viewer = TestUtils.openTextViewer(testFile);

		viewer.getTextWidget().setCaretOffset(1);

		if (!(viewer instanceof ISourceViewer)) {
			Assert.fail();
		}

		ISourceViewer sourceViewer = (ISourceViewer) viewer;

		Map<org.eclipse.jface.text.Position, Annotation> annotations = new HashMap<>();

		new DisplayHelper() {
			@Override
			protected boolean condition() {
				return sourceViewer.getAnnotationModel().getAnnotationIterator().hasNext();
			}
		}.waitForCondition(Display.getCurrent(), 3000);

		IAnnotationModel model = sourceViewer.getAnnotationModel();
		final Iterator<Annotation> iterator = model.getAnnotationIterator();
		while (iterator.hasNext()) {
			Annotation annotation = iterator.next();
			annotations.put(model.getPosition(annotation), annotation);
		}

		Annotation annotation = annotations.get(new org.eclipse.jface.text.Position(2, 4));
		Assert.assertNotNull(annotation);
		assertEquals(HighlightReconciler.READ_ANNOTATION_TYPE, annotation.getType());

		annotation = annotations.get(new org.eclipse.jface.text.Position(7, 5));
		Assert.assertNotNull(annotation);
		assertEquals(HighlightReconciler.WRITE_ANNOTATION_TYPE, annotation.getType());

		annotation = annotations.get(new org.eclipse.jface.text.Position(13, 4));
		Assert.assertNotNull(annotation);
		assertEquals(HighlightReconciler.TEXT_ANNOTATION_TYPE, annotation.getType());

		assertEquals(false, iterator.hasNext());
	}
}
