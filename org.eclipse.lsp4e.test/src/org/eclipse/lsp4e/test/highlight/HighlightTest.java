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
 *  Sebastian Thomschke (Vegard IT GmbH) - refactoring to fix erratic test failures
 *******************************************************************************/
package org.eclipse.lsp4e.test.highlight;

import static org.eclipse.lsp4e.test.TestUtils.waitForAndAssertCondition;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

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
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.Version;

public class HighlightTest {

	@Rule
	public AllCleanRule clear = new AllCleanRule();
	private IProject project;

	@Before
	public void setUp() throws CoreException {
		project = TestUtils.createProject("HighlightTest" + System.currentTimeMillis());
	}

	@Test
	public void testHighlight() throws CoreException {
		checkGenericEditorVersion();

		MockLanguageServer.INSTANCE.setDocumentHighlights(List.of( //
				new DocumentHighlight(new Range(new Position(0, 2), new Position(0, 6)), DocumentHighlightKind.Read),
				new DocumentHighlight(new Range(new Position(0, 7), new Position(0, 12)), DocumentHighlightKind.Write),
				new DocumentHighlight(new Range(new Position(0, 13), new Position(0, 17)), DocumentHighlightKind.Text) //
		));

		final IFile testFile = TestUtils.createUniqueTestFile(project, "  READ WRITE TEXT");
		final ITextViewer viewer = TestUtils.openTextViewer(testFile);

		if (viewer instanceof ISourceViewer sourceViewer) {
			final var annotationModel = sourceViewer.getAnnotationModel();

			sourceViewer.getTextWidget().setCaretOffset(1); // emulate cursor move

			waitForAndAssertCondition(3_000, () -> {
				assertHasAnnotion(annotationModel, HighlightReconcilingStrategy.READ_ANNOTATION_TYPE, 2, 4);
				assertHasAnnotion(annotationModel, HighlightReconcilingStrategy.WRITE_ANNOTATION_TYPE, 7, 5);
				assertHasAnnotion(annotationModel, HighlightReconcilingStrategy.TEXT_ANNOTATION_TYPE, 13, 4);
				return true;
			});
		} else {
			Assert.fail("ISourceViewer expected but got: " + viewer);
		}
	}

	@Test
	public void testCheckIfOtherAnnotationsRemains() throws CoreException {
		checkGenericEditorVersion();

		MockLanguageServer.INSTANCE.setDocumentHighlights(List.of( //
				new DocumentHighlight(new Range(new Position(0, 2), new Position(0, 6)), DocumentHighlightKind.Read) //
		));

		final IFile testFile = TestUtils.createUniqueTestFile(project, "  READ WRITE TEXT");
		final ITextViewer viewer = TestUtils.openTextViewer(testFile);

		if (viewer instanceof ISourceViewer sourceViewer) {
			final var annotationModel = sourceViewer.getAnnotationModel();

			final var fakeAnnotationType = "FAKE_TYPE";
			final var fakeAnnotation = new Annotation(fakeAnnotationType, false, null);
			final var fakeAnnotationPosition = new org.eclipse.jface.text.Position(0, 10);
			annotationModel.addAnnotation(fakeAnnotation, fakeAnnotationPosition);

			viewer.getTextWidget().setCaretOffset(1); // emulate cursor move

			waitForAndAssertCondition(3_000, () -> {
				assertHasAnnotion(annotationModel, HighlightReconcilingStrategy.READ_ANNOTATION_TYPE, 2, 4);
				assertHasAnnotion(annotationModel, fakeAnnotationType, fakeAnnotationPosition.offset,
						fakeAnnotationPosition.length);
				return true;
			});
		}
	}

	private void assertHasAnnotion(IAnnotationModel annotationModel, String annotationType, int posOffset, int posLen) {
		final var hasAnnotation = new boolean[] { false };
		final var annotations = new ArrayList<String>();
		annotationModel.getAnnotationIterator().forEachRemaining(anno -> {
			final var annoPos = annotationModel.getPosition(anno);
			if (anno.getType().equals(annotationType) && annoPos.offset == posOffset && annoPos.length == posLen) {
				hasAnnotation[0] = true;
			}
			annotations.add("Annotation[" + //
					"type=" + anno.getType() + //
					", text=" + anno.getText() + //
					", offset=" + annoPos.offset + //
					", length=" + annoPos.length + //
					"]");
		});

		if (!hasAnnotation[0]) {
			fail("Annotation of type [" + annotationType + "] not found at position {offset=" + posOffset + " length="
					+ posLen + "}. Annotations found: " + annotations);
		}
	}

	private void checkGenericEditorVersion() {
		// ignore tests for generic editor without reconciler API
		Bundle bundle = Platform.getBundle("org.eclipse.ui.genericeditor");
		Assume.assumeTrue(bundle.getVersion().compareTo(new Version(1, 1, 0)) >= 0);
	}
}
