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
package org.eclipse.lsp4e.test.diagnostics;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.lsp4e.operations.diagnostics.LSPDiagnosticsToMarkers;
import org.eclipse.lsp4e.test.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageSever;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.ui.texteditor.MarkerUtilities;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DiagnosticsTest {

	private IProject project;
	private LSPDiagnosticsToMarkers diagnosticsToMarkers;

	@Before
	public void setUp() throws CoreException {
		project = TestUtils.createProject("DiagnoticsTest" + System.currentTimeMillis());
		diagnosticsToMarkers = new LSPDiagnosticsToMarkers(project, "dummy");
	}

	@After
	public void tearDown() throws CoreException {
		project.delete(true, true, new NullProgressMonitor());
		MockLanguageSever.INSTANCE.shutdown();
	}

	@Test
	public void testDiagnostics() throws CoreException {
		IFile file = TestUtils.createUniqueTestFile(project, "Diagnostic Other Text");

		Range range = new Range(new Position(0, 0), new Position(0, 10));
		List<Diagnostic> diagnostics = new ArrayList<>();
		diagnostics.add(createDiagnostic("1", "message1", range, DiagnosticSeverity.Error, "source1"));
		diagnostics.add(createDiagnostic("2", "message2", range, DiagnosticSeverity.Warning, "source2"));
		diagnostics.add(createDiagnostic("3", "message3", range, DiagnosticSeverity.Information, "source3"));
		diagnostics.add(createDiagnostic("4", "message4", range, DiagnosticSeverity.Hint, "source4"));

		diagnosticsToMarkers.accept(new PublishDiagnosticsParams(file.getLocationURI().toString(), diagnostics));

		IMarker[] markers = file.findMarkers(LSPDiagnosticsToMarkers.LS_DIAGNOSTIC_MARKER_TYPE, false,
				IResource.DEPTH_INFINITE);
		assertEquals(diagnostics.size(), markers.length);

		for (int i = 0; i < diagnostics.size(); i++) {
			Diagnostic diagnostic = diagnostics.get(i);
			IMarker marker = markers[i];

			assertEquals(diagnostic.getMessage(), MarkerUtilities.getMessage(marker));
			assertEquals(0, MarkerUtilities.getCharStart(marker));
			assertEquals(10, MarkerUtilities.getCharEnd(marker));
			assertEquals(1, MarkerUtilities.getLineNumber(marker));
			// TODO compare code, severity, source
		}

		diagnosticsToMarkers.accept(new PublishDiagnosticsParams(file.getLocationURI().toString(), Collections.emptyList()));

		markers = file.findMarkers(LSPDiagnosticsToMarkers.LS_DIAGNOSTIC_MARKER_TYPE, false, IResource.DEPTH_INFINITE);
		assertEquals(0, markers.length);
	}

	@Test
	public void testDiagnosticsRangeAfterDocument() throws CoreException {
		String content = "Diagnostic Other Text";
		IFile file = TestUtils.createUniqueTestFile(project, content);

		Range range = new Range(new Position(1, 0), new Position(1, 5));
		List<Diagnostic> diagnostics = Collections
				.singletonList(createDiagnostic("1", "message1", range, DiagnosticSeverity.Error, "source1"));

		diagnosticsToMarkers.accept(new PublishDiagnosticsParams(file.getLocationURI().toString(), diagnostics));

		IMarker[] markers = file.findMarkers(LSPDiagnosticsToMarkers.LS_DIAGNOSTIC_MARKER_TYPE, false,
				IResource.DEPTH_INFINITE);
		assertEquals(diagnostics.size(), markers.length);

		for (int i = 0; i < diagnostics.size(); i++) {
			Diagnostic diagnostic = diagnostics.get(i);
			IMarker marker = markers[i];

			assertEquals(diagnostic.getMessage(), MarkerUtilities.getMessage(marker));
			assertEquals(content.length(), MarkerUtilities.getCharStart(marker));
			assertEquals(content.length(), MarkerUtilities.getCharEnd(marker));
			assertEquals(1, MarkerUtilities.getLineNumber(marker));

			// TODO compare code, severity, source
		}
	}

	@Test
	public void testDiagnosticsFromVariousLS() throws Exception {
		String content = "Diagnostic Other Text";
		IFile file = TestUtils.createUniqueTestFileMultiLS(project, content);
		Range range = new Range(new Position(1, 0), new Position(1, 0));
		MockLanguageSever.INSTANCE.setDiagnostics(Collections.singletonList(
				createDiagnostic("1", "message1", range, DiagnosticSeverity.Error, "source1")));
		IMarker[] markers = file.findMarkers(LSPDiagnosticsToMarkers.LS_DIAGNOSTIC_MARKER_TYPE, true, IResource.DEPTH_ZERO);
		assertEquals("no marker should be shown at file initialization", 0, markers.length);
		TestUtils.openEditor(file);
		Thread.sleep(300); //give some time to LSs to process
		markers = file.findMarkers(LSPDiagnosticsToMarkers.LS_DIAGNOSTIC_MARKER_TYPE, true, IResource.DEPTH_ZERO);
		assertEquals("there should be 1 marker for each language server", 2, markers.length);
	}

	private Diagnostic createDiagnostic(String code, String message, Range range, DiagnosticSeverity severity,
			String source) {
		Diagnostic diagnostic = new Diagnostic();
		diagnostic.setCode(code);
		diagnostic.setMessage(message);
		diagnostic.setRange(range);
		diagnostic.setSeverity(severity);
		diagnostic.setSource(source);
		return diagnostic;
	}

}
