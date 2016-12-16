package org.eclipse.lsp4e.test.edit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayInputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.test.MockLanguageSever;
import org.eclipse.lsp4e.test.TestUtils;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.junit.Test;

public class DocumentDidChangeTest {

	@Test
	public void testIncrementalSync() throws Exception {
		IProject project = TestUtils.createProject("DocumentDidChangeTest"+System.currentTimeMillis());
		
		MockLanguageSever.INSTANCE.getInitializeResult().getCapabilities()
				.setTextDocumentSync(TextDocumentSyncKind.Incremental);

		IFile testFile = project.getFile("test01.lspt");
		testFile.create(new ByteArrayInputStream(new byte[0]), true, null);

		ITextViewer viewer = TestUtils.openTextViewer(testFile);
		LanguageServiceAccessor.getLanguageServer(testFile, new Predicate<ServerCapabilities>() {
			@Override
			public boolean test(ServerCapabilities t) {
				assertEquals(TextDocumentSyncKind.Incremental, t.getTextDocumentSync());
				return true;
			}
		});

		// Test initial insert
		CompletableFuture<DidChangeTextDocumentParams> didChangeExpectation = new CompletableFuture<DidChangeTextDocumentParams>();
		MockLanguageSever.INSTANCE.setDidChangeCallback(didChangeExpectation);
		viewer.getDocument().replace(0, 0, "Hello");
		DidChangeTextDocumentParams lastChange = didChangeExpectation.get(1000, TimeUnit.MILLISECONDS);
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
		didChangeExpectation = new CompletableFuture<DidChangeTextDocumentParams>();
		MockLanguageSever.INSTANCE.setDidChangeCallback(didChangeExpectation);
		viewer.getDocument().replace(5, 0, " ");
		lastChange = didChangeExpectation.get(1000, TimeUnit.MILLISECONDS);
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
		didChangeExpectation = new CompletableFuture<DidChangeTextDocumentParams>();
		MockLanguageSever.INSTANCE.setDidChangeCallback(didChangeExpectation);
		viewer.getDocument().replace(0, 5, "Hallo");
		lastChange = didChangeExpectation.get(1000, TimeUnit.MILLISECONDS);
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

		project.delete(true, true, new NullProgressMonitor());
	}

	@Test
	public void testFullSync() throws Exception {
		IProject project = TestUtils.createProject("DocumentDidChangeTest"+System.currentTimeMillis());
		
		MockLanguageSever.INSTANCE.getInitializeResult().getCapabilities()
				.setTextDocumentSync(TextDocumentSyncKind.Full);

		IFile testFile = project.getFile("test01.lspt");
		testFile.create(new ByteArrayInputStream(new byte[0]), true, null);

		ITextViewer viewer = TestUtils.openTextViewer(testFile);
		LanguageServiceAccessor.getLanguageServer(testFile, new Predicate<ServerCapabilities>() {
			@Override
			public boolean test(ServerCapabilities t) {
				assertEquals(TextDocumentSyncKind.Full, t.getTextDocumentSync());
				return true;
			}
		});
		// Test initial insert
		CompletableFuture<DidChangeTextDocumentParams> didChangeExpectation = new CompletableFuture<DidChangeTextDocumentParams>();
		MockLanguageSever.INSTANCE.setDidChangeCallback(didChangeExpectation);
		String text = "Hello";
		viewer.getDocument().replace(0, 0, text);
		DidChangeTextDocumentParams lastChange = didChangeExpectation.get(1000, TimeUnit.MILLISECONDS);
		assertNotNull(lastChange.getContentChanges());
		assertEquals(1, lastChange.getContentChanges().size());
		TextDocumentContentChangeEvent change0 = lastChange.getContentChanges().get(0);
		assertEquals(text, change0.getText());

		// Test additional insert
		didChangeExpectation = new CompletableFuture<DidChangeTextDocumentParams>();
		MockLanguageSever.INSTANCE.setDidChangeCallback(didChangeExpectation);
		viewer.getDocument().replace(5, 0, " World");
		lastChange = didChangeExpectation.get(1000, TimeUnit.MILLISECONDS);
		assertNotNull(lastChange.getContentChanges());
		assertEquals(1, lastChange.getContentChanges().size());
		change0 = lastChange.getContentChanges().get(0);
		assertEquals("Hello World", change0.getText());
		
		project.delete(true, true, new NullProgressMonitor());
	}

}
