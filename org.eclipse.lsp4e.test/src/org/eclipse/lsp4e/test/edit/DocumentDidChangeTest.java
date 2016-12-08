package org.eclipse.lsp4e.test.edit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayInputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings("restriction")
public class DocumentDidChangeTest {
	
	private IProject project;

	@Before
	public void setUp() throws CoreException {
		project = TestUtils.createProject("DocumentDidChangeTest");
	}

	@After
	public void tearDown() throws CoreException {
		project.delete(true, true, new NullProgressMonitor());
	}
	
	@Test
	public void test() throws Exception {
		IFile testFile = project.getFile("test01.lspt");
		testFile.create(new ByteArrayInputStream(new byte[0]), true, null);

		ITextViewer viewer = TestUtils.createTextViewer(testFile);
		LanguageServiceAccessor.getLanguageServer(testFile, viewer.getDocument(), new Predicate<ServerCapabilities>() {

			@Override
			public boolean test(ServerCapabilities t) {
				assertEquals(TextDocumentSyncKind.Incremental, t.getTextDocumentSync());
				return true;
			}
			
		});
		
		//Test initial insert
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
	}

}
