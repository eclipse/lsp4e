/*******************************************************************************
 * Copyright (c) 2016-2017 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *  Mickael Istria (Red Hat Inc.)
 *******************************************************************************/
package org.eclipse.lsp4e.test.edit;

import static org.eclipse.lsp4e.test.TestUtils.numberOfChangesIs;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.test.AllCleanRule;
import org.eclipse.lsp4e.test.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4e.tests.mock.MockTextDocumentService;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class DocumentDidChangeTest {

	@Rule public AllCleanRule clear = new AllCleanRule();
	private IProject project;

	@Before
	public void setUp() throws CoreException {
		project = TestUtils.createProject("DocumentDidChangeTest"+System.currentTimeMillis());
	}

	@Test
	public void testIncrementalSync() throws Exception {
		MockLanguageServer.INSTANCE.getInitializeResult().getCapabilities()
				.setTextDocumentSync(TextDocumentSyncKind.Incremental);

		IFile testFile = TestUtils.createUniqueTestFile(project, "");
		IEditorPart editor = TestUtils.openEditor(testFile);
		ITextViewer viewer = LSPEclipseUtils.getTextViewer(editor);
		LanguageServiceAccessor.getLanguageServers(viewer.getDocument(), new Predicate<ServerCapabilities>() {
			@Override
			public boolean test(ServerCapabilities t) {
				TextDocumentSyncKind syncKind = getDocumentSyncKind(t);
				assertEquals(TextDocumentSyncKind.Incremental, syncKind);
				return true;
			}
		});

		// Test initial insert
		viewer.getDocument().replace(0, 0, "Hello");
		TestUtils.waitForCondition(1000,  numberOfChangesIs(1));
		DidChangeTextDocumentParams lastChange = MockLanguageServer.INSTANCE.getDidChangeEvents().get(0);
		assertNotNull(lastChange.getContentChanges());
		assertEquals(1, lastChange.getContentChanges().size());
		TextDocumentContentChangeEvent change0 = lastChange.getContentChanges().get(0);
		Range range = change0.getRange();
		assertNotNull(range);
		assertEquals(0, range.getStart().getLine());
		assertEquals(0, range.getStart().getCharacter());
		assertEquals(0, range.getEnd().getLine());
		assertEquals(0, range.getEnd().getCharacter());
		assertEquals(Integer.valueOf(0), change0.getRangeLength());
		assertEquals("Hello", change0.getText());

		// Test additional insert
		viewer.getDocument().replace(5, 0, " ");
		TestUtils.waitForCondition(1000,  numberOfChangesIs(2));
		lastChange = MockLanguageServer.INSTANCE.getDidChangeEvents().get(1);
		assertNotNull(lastChange.getContentChanges());
		assertEquals(1, lastChange.getContentChanges().size());
		change0 = lastChange.getContentChanges().get(0);
		range = change0.getRange();
		assertNotNull(range);
		assertEquals(0, range.getStart().getLine());
		assertEquals(5, range.getStart().getCharacter());
		assertEquals(0, range.getEnd().getLine());
		assertEquals(5, range.getEnd().getCharacter());
		assertEquals(Integer.valueOf(0), change0.getRangeLength());
		assertEquals(" ", change0.getText());

		// test replace
		viewer.getDocument().replace(0, 5, "Hallo");
		TestUtils.waitForCondition(1000,  numberOfChangesIs(3));
		lastChange = MockLanguageServer.INSTANCE.getDidChangeEvents().get(2);
		assertNotNull(lastChange.getContentChanges());
		assertEquals(1, lastChange.getContentChanges().size());
		change0 = lastChange.getContentChanges().get(0);
		range = change0.getRange();
		assertNotNull(range);
		assertEquals(0, range.getStart().getLine());
		assertEquals(0, range.getStart().getCharacter());
		assertEquals(0, range.getEnd().getLine());
		assertEquals(5, range.getEnd().getCharacter());
		assertEquals(Integer.valueOf(5), change0.getRangeLength());
		assertEquals("Hallo", change0.getText());
	}

	@Test
	public void testIncrementalSync_deleteLastLine() throws Exception {
		MockLanguageServer.INSTANCE.getInitializeResult().getCapabilities()
				.setTextDocumentSync(TextDocumentSyncKind.Incremental);

		String multiLineText = "line1\nline2\nline3\n";
		IFile testFile = TestUtils.createUniqueTestFile(project, multiLineText);
		IEditorPart editor = TestUtils.openEditor(testFile);
		ITextViewer viewer = LSPEclipseUtils.getTextViewer(editor);
		LanguageServiceAccessor.getLanguageServers(viewer.getDocument(), new Predicate<ServerCapabilities>() {
			@Override
			public boolean test(ServerCapabilities t) {
				assertEquals(TextDocumentSyncKind.Incremental, getDocumentSyncKind(t));
				return true;
			}
		});

		// Test initial insert
		viewer.getDocument().replace("line1\nline2\n".length(), "line3\n".length(), "");
		TestUtils.waitForCondition(1000,  numberOfChangesIs(1));
		DidChangeTextDocumentParams lastChange = MockLanguageServer.INSTANCE.getDidChangeEvents().get(0);
		assertNotNull(lastChange.getContentChanges());
		assertEquals(1, lastChange.getContentChanges().size());
		TextDocumentContentChangeEvent change0 = lastChange.getContentChanges().get(0);
		Range range = change0.getRange();
		assertNotNull(range);
		assertEquals(2, range.getStart().getLine());
		assertEquals(0, range.getStart().getCharacter());
		assertEquals(3, range.getEnd().getLine());
		assertEquals(0, range.getEnd().getCharacter());
		assertEquals(Integer.valueOf(6), change0.getRangeLength());
		assertEquals("", change0.getText());
	}

	@Test
	public void testIncrementalEditOrdering() throws Exception {
		MockLanguageServer.INSTANCE.getInitializeResult().getCapabilities()
		.setTextDocumentSync(TextDocumentSyncKind.Incremental);
		IFile testFile = TestUtils.createUniqueTestFile(project, "");
		IEditorPart editor = TestUtils.openEditor(testFile);
		ITextViewer viewer = LSPEclipseUtils.getTextViewer(editor);
		StyledText text = viewer.getTextWidget();
		for (int i = 0; i < 500; i++) {
			text.append(i + "\n");
		}
		TestUtils.waitForCondition(10000,  numberOfChangesIs(500));
		List<DidChangeTextDocumentParams> changes = MockLanguageServer.INSTANCE.getDidChangeEvents();
		for (int i = 0; i < 500; i++) {
			String delta = changes.get(i).getContentChanges().get(0).getText();
			assertEquals(i + "\n", delta);
		}
		

	}
	
	@Test
	public void editInterleavingTortureTest() throws Exception {
		
		final AtomicInteger orderedCount = new AtomicInteger();
		final AtomicInteger unorderedCount = new AtomicInteger();
		final AtomicInteger updateCount = new AtomicInteger();
		final Vector<Integer> outOfOrder = new Vector<>();

		
		MockLanguageServer.INSTANCE.getInitializeResult().getCapabilities()
		.setTextDocumentSync(TextDocumentSyncKind.Incremental);
		MockLanguageServer.INSTANCE.setTextDocumentService(new MockTextDocumentService(MockLanguageServer.INSTANCE::buildMaybeDelayedFuture) {
			@Override
			public synchronized void didChange(DidChangeTextDocumentParams params) {
				super.didChange(params);
				updateCount.incrementAndGet();
			}
			
			@Override
			public synchronized CompletableFuture<Hover> hover(HoverParams position) {
				final int current = position.getPosition().getCharacter();
				if (current == updateCount.get()) {
					orderedCount.incrementAndGet();
				} else {
					unorderedCount.incrementAndGet();
					outOfOrder.add(current);
				}
				
				return super.hover(position);
			}
		});
		
		Hover hoverResponse = new Hover(Collections.singletonList(Either.forLeft("HoverContent")), new Range(new Position(0,  0), new Position(0, 10)));
		MockLanguageServer.INSTANCE.setHover(hoverResponse);
		CompletableFuture<?> initial = CompletableFuture.completedFuture(null);
		
		IFile testFile = TestUtils.createUniqueTestFile(project, "");
		IEditorPart editor = TestUtils.openEditor(testFile);
		ITextViewer viewer = LSPEclipseUtils.getTextViewer(editor);
		final IDocument document = viewer.getDocument();
		final URI uri = LSPEclipseUtils.toUri(document);
		StyledText text = viewer.getTextWidget();
		text.append("Startup\n");
		Thread.sleep(1000);
		
		updateCount.set(0);
		
		for (int i = 0; i < 500; i++) {
			final int current = i + 1;
			text.append(i + "\n");
			final HoverParams params = new HoverParams();
			final Position position = new Position();
			position.setCharacter(current);
			position.setLine(0);
			params.setPosition(position);
			

			CompletableFuture<?> hoverFuture = LanguageServiceAccessor.computeOnServers(document, capabilities -> LSPEclipseUtils.hasCapability(capabilities.getHoverProvider()), languageServer -> {
					try {
						return languageServer.getTextDocumentService().hover(params);
					} catch (Exception e) {
						
					}
					return CompletableFuture.completedFuture(null);
				});
			initial = CompletableFuture.allOf(initial, hoverFuture);
		}
		
		initial.join();
		System.out.println("Completed: " + updateCount.get());
		System.out.println("Ordered: " + orderedCount.get());
		System.out.println("Unordered: " + unorderedCount.get());
		
		for (int i : outOfOrder) {
			System.out.println("Out of order:" + i);
		}
		
		assertEquals(updateCount.get(), orderedCount.get());
	}
	
	@Test
	public void testBlockingServerBlocksUIThread() throws Exception {

		final Vector<Integer> tooEarlyHover = new Vector<>();
		final Vector<Integer> tooLateHover = new Vector<>();
		final AtomicInteger uiDispatchCount = new AtomicInteger();
		
		MockLanguageServer.INSTANCE.getInitializeResult().getCapabilities()
		.setTextDocumentSync(TextDocumentSyncKind.Incremental);
		MockLanguageServer.INSTANCE.setTextDocumentService(new MockTextDocumentService(MockLanguageServer.INSTANCE::buildMaybeDelayedFuture) {
			int changeVersion = 0;
			@Override
			public synchronized void didChange(DidChangeTextDocumentParams params) {
				super.didChange(params);
				changeVersion++;
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			
			@Override
			public synchronized CompletableFuture<Hover> hover(HoverParams position) {
				final int targetVersionForRequest = position.getPosition().getCharacter();
				if (targetVersionForRequest < changeVersion) {
					tooLateHover.add(targetVersionForRequest);
				} else if (targetVersionForRequest > changeVersion){
					tooEarlyHover.add(targetVersionForRequest);
				}
				return super.hover(position);
			}
		});
		
		Hover hoverResponse = new Hover(Collections.singletonList(Either.forLeft("HoverContent")), new Range(new Position(0,  0), new Position(0, 10)));
		MockLanguageServer.INSTANCE.setHover(hoverResponse);
		CompletableFuture<?> initial = CompletableFuture.completedFuture(null);
		
		IFile testFile = TestUtils.createUniqueTestFile(project, "");
		IEditorPart editor = TestUtils.openEditor(testFile);
		ITextViewer viewer = LSPEclipseUtils.getTextViewer(editor);
		final IDocument document = viewer.getDocument();
		final URI uri = LSPEclipseUtils.toUri(document);
		StyledText text = viewer.getTextWidget();
		Thread.sleep(1000);
		
		final long startTime = System.currentTimeMillis();
		
		final StringBuilder bulkyText = new StringBuilder();
		
		// Construct a reasonably bulky payload for the document updates: if the
		// payload is small then buffering will mitigate any back-pressure from the server
		// (typically 8k for a unix pipe)
		for (int i = 0; i < 1000; i++) {
			bulkyText.append("Some Text; ");
		}
		
		final String content = bulkyText.toString();
		
		for (int i = 0; i < 20; i++) {
			final int current = i + 1;
			text.append(content + "\n");
			final HoverParams params = new HoverParams();
			final Position position = new Position();
			position.setCharacter(current);
			position.setLine(0);
			params.setPosition(position);
			
//			CompletableFuture<?> hoverFuture = LanguageServiceAccessor.getLanguageServers(document, capabilities -> LSPEclipseUtils.hasCapability(capabilities.getHoverProvider()))
//			.thenApply(languageServers -> // Async is very important here, otherwise the LS Client thread is in
//												// deadlock and doesn't read bytes from LS
//			languageServers.stream()
//				.map(languageServer -> {
//					if (Display.getCurrent() != null) {
//						uiDispatchCount.incrementAndGet();
//					}
//					try {
//						return languageServer.getTextDocumentService().hover(params);
//					} catch (Exception e) {
//						
//					}
//					return CompletableFuture.completedFuture(null);
//				}).collect(Collectors.toList()));
//			initial = CompletableFuture.allOf(initial, hoverFuture);
			CompletableFuture<?> hoverFuture = LanguageServiceAccessor.computeOnServers(document, capabilities -> LSPEclipseUtils.hasCapability(capabilities.getHoverProvider()), languageServer -> {
				try {
					if (Display.getCurrent() != null) {
					uiDispatchCount.incrementAndGet();
				}
					return languageServer.getTextDocumentService().hover(params);
				} catch (Exception e) {
					
				}
				return CompletableFuture.completedFuture(null);
			});
		initial = CompletableFuture.allOf(initial, hoverFuture);
		}
		
		final long dispatchTime = System.currentTimeMillis();
		
		initial.join();
		
		final long finishTime = System.currentTimeMillis();
		
		System.err.println("Dispatch time = " + (dispatchTime - startTime)/ 1000.0);
		System.err.println("Test time = " + (finishTime - startTime)/ 1000.0);
		
		System.err.println("UI dispatch count = " + uiDispatchCount.get());
		
	}

	@Test
	public void testFullSync() throws Exception {
		MockLanguageServer.INSTANCE.getInitializeResult().getCapabilities()
				.setTextDocumentSync(TextDocumentSyncKind.Full);

		IFile testFile = TestUtils.createUniqueTestFile(project, "");
		IEditorPart editor = TestUtils.openEditor(testFile);
		ITextViewer viewer = LSPEclipseUtils.getTextViewer(editor);
		LanguageServiceAccessor.getLanguageServers(viewer.getDocument(), new Predicate<ServerCapabilities>() {
			@Override
			public boolean test(ServerCapabilities t) {
				assertEquals(TextDocumentSyncKind.Full, getDocumentSyncKind(t));
				return true;
			}
		});
		// Test initial insert
		String text = "Hello";
		viewer.getDocument().replace(0, 0, text);
		TestUtils.waitForCondition(1000,  numberOfChangesIs(1));
		DidChangeTextDocumentParams lastChange = MockLanguageServer.INSTANCE.getDidChangeEvents().get(0);
		assertNotNull(lastChange.getContentChanges());
		assertEquals(1, lastChange.getContentChanges().size());
		TextDocumentContentChangeEvent change0 = lastChange.getContentChanges().get(0);
		assertEquals(text, change0.getText());

		// Test additional insert

		viewer.getDocument().replace(5, 0, " World");
		TestUtils.waitForCondition(1000,  numberOfChangesIs(2));
		lastChange = MockLanguageServer.INSTANCE.getDidChangeEvents().get(1);
		assertNotNull(lastChange.getContentChanges());
		assertEquals(1, lastChange.getContentChanges().size());
		change0 = lastChange.getContentChanges().get(0);
		assertEquals("Hello World", change0.getText());
	}

	@Test
	public void testFullSyncExternalFile() throws Exception {
		MockLanguageServer.INSTANCE.getInitializeResult().getCapabilities()
				.setTextDocumentSync(TextDocumentSyncKind.Full);

		File file = TestUtils.createTempFile("testFullSyncExternalFile", ".lspt");
		IEditorPart editor = IDE.openEditorOnFileStore(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(), EFS.getStore(file.toURI()));
		ITextViewer viewer = LSPEclipseUtils.getTextViewer(editor);
		LanguageServiceAccessor.getLanguageServers(viewer.getDocument(), new Predicate<ServerCapabilities>() {
			@Override
			public boolean test(ServerCapabilities t) {
				assertEquals(TextDocumentSyncKind.Full, getDocumentSyncKind(t));
				return true;
			}
		});
        // Test initial insert
        String text = "Hello";
        viewer.getDocument().replace(0, 0, text);
        TestUtils.waitForCondition(1000,  numberOfChangesIs(1));
        DidChangeTextDocumentParams lastChange = MockLanguageServer.INSTANCE.getDidChangeEvents().get(0);
		assertNotNull(lastChange.getContentChanges());
		assertEquals(1, lastChange.getContentChanges().size());
		TextDocumentContentChangeEvent change0 = lastChange.getContentChanges().get(0);
		assertEquals(text, change0.getText());

        // Test additional insert
        viewer.getDocument().replace(5, 0, " World");
        TestUtils.waitForCondition(1000,  numberOfChangesIs(2));
        lastChange = MockLanguageServer.INSTANCE.getDidChangeEvents().get(1);
		assertNotNull(lastChange.getContentChanges());
		assertEquals(1, lastChange.getContentChanges().size());
		change0 = lastChange.getContentChanges().get(0);
		assertEquals("Hello World", change0.getText());
	}


	private TextDocumentSyncKind getDocumentSyncKind(ServerCapabilities t) {
		TextDocumentSyncKind syncKind = null;
		if (t.getTextDocumentSync().isLeft()) {
			syncKind = t.getTextDocumentSync().getLeft();
		} else if (t.getTextDocumentSync().isRight()) {
			syncKind = t.getTextDocumentSync().getRight().getChange();
		}
		return syncKind;
	}

}
