/*******************************************************************************
 * Copyright (c) 2017 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.highlight;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.lsp4e.operations.highlight.HighlightReconcilingStrategy;
import org.eclipse.lsp4e.test.AllCleanRule;
import org.eclipse.lsp4e.test.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.tests.harness.util.DisplayHelper;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.Version;

public class HighlightTest {

	@Rule public AllCleanRule clear = new AllCleanRule();
	private IProject project;

	@Before
	public void setUp() throws CoreException {
		project = TestUtils.createProject("HighlightTest" + System.currentTimeMillis());
	}

	@Test
	public void testHighlight() throws CoreException {
		checkGenericEditorVersion();

		List<DocumentHighlight> highlights = new ArrayList<>();
		highlights.add(
				new DocumentHighlight(new Range(new Position(0, 2), new Position(0, 6)), DocumentHighlightKind.Read));
		highlights.add(
				new DocumentHighlight(new Range(new Position(0, 7), new Position(0, 12)), DocumentHighlightKind.Write));
		highlights.add(
				new DocumentHighlight(new Range(new Position(0, 13), new Position(0, 17)), DocumentHighlightKind.Text));
		MockLanguageServer.INSTANCE.setDocumentHighlights(highlights);

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
		assertEquals(HighlightReconcilingStrategy.READ_ANNOTATION_TYPE, annotation.getType());

		annotation = annotations.get(new org.eclipse.jface.text.Position(7, 5));
		Assert.assertNotNull(annotation);
		assertEquals(HighlightReconcilingStrategy.WRITE_ANNOTATION_TYPE, annotation.getType());

		annotation = annotations.get(new org.eclipse.jface.text.Position(13, 4));
		Assert.assertNotNull(annotation);
		assertEquals(HighlightReconcilingStrategy.TEXT_ANNOTATION_TYPE, annotation.getType());

		assertEquals(false, iterator.hasNext());
	}

	@Test
	public void testCheckIfOtherAnnotationsRemains() throws CoreException {
		checkGenericEditorVersion();

		IFile testFile = TestUtils.createUniqueTestFile(project, "  READ WRITE TEXT");
		ITextViewer viewer = TestUtils.openTextViewer(testFile);

		List<DocumentHighlight> highlights = Collections.singletonList(
				new DocumentHighlight(new Range(new Position(0, 2), new Position(0, 6)), DocumentHighlightKind.Read));
		MockLanguageServer.INSTANCE.setDocumentHighlights(highlights);

		if (!(viewer instanceof ISourceViewer)) {
			Assert.fail();
		}

		ISourceViewer sourceViewer = (ISourceViewer) viewer;
		IAnnotationModel model = sourceViewer.getAnnotationModel();

		String fakeAnnotationType = "FAKE_TYPE";
		Annotation fakeAnnotation = new Annotation(fakeAnnotationType, false, null);
		org.eclipse.jface.text.Position fakeAnnotationPosition = new org.eclipse.jface.text.Position(0, 10);
		model.addAnnotation(fakeAnnotation, fakeAnnotationPosition);

		// emulate cursor move
		viewer.getTextWidget().setCaretOffset(1);

		Assert.assertTrue(new DisplayHelper() {
			@Override
			protected boolean condition() {
				IAnnotationModel annotationModel = sourceViewer.getAnnotationModel();
				Map<org.eclipse.jface.text.Position, Annotation> annotations = new HashMap<>();
				annotationModel.getAnnotationIterator().forEachRemaining(ann -> annotations.put(model.getPosition(ann), ann));
				if (annotations.size() != 2) {
					return false;
				}
				{
					Annotation annotation = annotations.get(new org.eclipse.jface.text.Position(2, 4));
					if (annotation == null || !HighlightReconcilingStrategy.READ_ANNOTATION_TYPE.equals(annotation.getType())) {
						return false;
					}
				}
				{
					Annotation annotation = annotations.get(fakeAnnotationPosition);
					if (annotation == null || !fakeAnnotationType.equals(annotation.getType())) {
						return false;
					}
				}
				return true;
			}
		}.waitForCondition(Display.getCurrent(), 3000));
	}

	private void checkGenericEditorVersion() {
		// ignore tests for generic editor wihtout reconciler API
		Bundle bundle = Platform.getBundle("org.eclipse.ui.genericeditor");
		Assume.assumeTrue(bundle.getVersion().compareTo(new Version(1, 1, 0)) >= 0);
	}

}
