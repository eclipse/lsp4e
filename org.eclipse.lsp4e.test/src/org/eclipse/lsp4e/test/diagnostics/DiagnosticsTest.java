/*******************************************************************************
 * Copyright (c) 2017, 2018 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *  Lucas Bullen (Red Hat Inc.) - [Bug 528333] Performance problem with diagnostics
 *******************************************************************************/
package org.eclipse.lsp4e.test.diagnostics;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntSupplier;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.tests.util.DisplayHelper;
import org.eclipse.lsp4e.operations.diagnostics.LSPDiagnosticsToMarkers;
import org.eclipse.lsp4e.test.AllCleanRule;
import org.eclipse.lsp4e.test.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.texteditor.MarkerUtilities;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class DiagnosticsTest {

	@Rule public AllCleanRule clear = new AllCleanRule();
	private IProject project;
	private LSPDiagnosticsToMarkers diagnosticsToMarkers;

	@Before
	public void setUp() throws CoreException {
		project = TestUtils.createProject("DiagnoticsTest" + System.currentTimeMillis());
		diagnosticsToMarkers = new LSPDiagnosticsToMarkers("dummy");
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
			assertEquals(marker.getAttribute(LSPDiagnosticsToMarkers.LSP_DIAGNOSTIC), diagnostic);
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
			assertEquals(marker.getAttribute(LSPDiagnosticsToMarkers.LSP_DIAGNOSTIC), diagnostic);
		}
	}

	@Test
	public void testDiagnosticsFromVariousLS() throws Exception {
		String content = "Diagnostic Other Text";
		IFile file = TestUtils.createUniqueTestFileMultiLS(project, content);
		Range range = new Range(new Position(1, 0), new Position(1, 0));
		MockLanguageServer.INSTANCE.setDiagnostics(Collections.singletonList(
				createDiagnostic("1", "message1", range, DiagnosticSeverity.Error, "source1")));
		IMarker[] markers = file.findMarkers(LSPDiagnosticsToMarkers.LS_DIAGNOSTIC_MARKER_TYPE, true, IResource.DEPTH_ZERO);
		assertEquals("no marker should be shown at file initialization", 0, markers.length);
		TestUtils.openEditor(file);
		Thread.sleep(300); //give some time to LSs to process
		markers = file.findMarkers(LSPDiagnosticsToMarkers.LS_DIAGNOSTIC_MARKER_TYPE, true, IResource.DEPTH_ZERO);
		assertEquals("there should be 1 marker for each language server", 2, markers.length);
	}

	@Test
	public void testDiagnosticRedrawingCalls() throws CoreException {
		IFile file = TestUtils.createUniqueTestFile(project, "Diagnostic Other Text\nDiagnostic Other Text");

		final Range range1 = new Range(new Position(0, 0), new Position(0, 10));
		final Range range2 = new Range(new Position(1, 0), new Position(1, 10));
		final Diagnostic pos1Info1 = createDiagnostic("1", "message1", range1, DiagnosticSeverity.Error, "source1");
		final Diagnostic pos1Info2 = createDiagnostic("2", "message2", range1, DiagnosticSeverity.Error, "source2");
		final Diagnostic pos2Info2 = createDiagnostic("2", "message2", range2, DiagnosticSeverity.Error, "source2");
		// Add first
		confirmResourceChanges(file, pos1Info1, 1);
		// Info changed
		confirmResourceChanges(file, pos1Info2, 1);
		// Location changed
		confirmResourceChanges(file, pos2Info2, 1);
		// Location and Info changed
		confirmResourceChanges(file, pos1Info1, 1);
		// Nothing has changed (0 resource changes)
		confirmResourceChanges(file, pos1Info1, 0);
	}

	private class MarkerRedrawCountListener implements IResourceChangeListener, IntSupplier {
		private int resourceChanges = 0;

		@Override
		public int getAsInt() {
			return resourceChanges;
		}

		@Override
		public void resourceChanged(IResourceChangeEvent event) {
			// Confirm that it is a marker change
			IResourceDelta delta = event.getDelta();
			if (delta == null)
				return;
			IResourceDelta[] childDeltas = delta.getAffectedChildren();
			if (childDeltas.length == 0)
				return;
			childDeltas = childDeltas[0].getAffectedChildren();
			if (childDeltas.length == 0 || childDeltas[0].getMarkerDeltas().length == 0)
				return;
			resourceChanges++;
		}

	}

	private void confirmResourceChanges(IFile file, Diagnostic diagnostic, int expectedChanges) {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		MarkerRedrawCountListener redrawCountListener = new MarkerRedrawCountListener();
		workspace.addResourceChangeListener(redrawCountListener);
		try {
			diagnosticsToMarkers.accept(new PublishDiagnosticsParams(file.getLocationURI().toString(),
					Collections.singletonList(diagnostic)));
			new DisplayHelper() {
				@Override
				protected boolean condition() {
					try {
						IMarker[] markers = file.findMarkers(LSPDiagnosticsToMarkers.LS_DIAGNOSTIC_MARKER_TYPE, false,
								IResource.DEPTH_INFINITE);
						for (IMarker marker : markers) {
							if (marker.getAttribute(LSPDiagnosticsToMarkers.LSP_DIAGNOSTIC).equals(diagnostic)) {
								return true;
							}
						}
					} catch (CoreException e) {
						// Caught with False return
					}
					return false;
				}
			}.waitForCondition(Display.getCurrent(), 5000);
			assertEquals(expectedChanges, redrawCountListener.getAsInt());
		} finally {
			workspace.removeResourceChangeListener(redrawCountListener);
		}

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
