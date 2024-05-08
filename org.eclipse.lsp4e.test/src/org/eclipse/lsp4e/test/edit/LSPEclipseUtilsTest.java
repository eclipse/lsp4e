/*******************************************************************************
 * Copyright (c) 2016, 2022 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Remy Suen <remy.suen@gmail.com> - Bug 520052 - Rename assumes that workspace edits are in reverse order
 *  Pierre-Yves Bigourdan <pyvesdev@gmail.com> - Issue 29
 *******************************************************************************/
package org.eclipse.lsp4e.test.edit;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Random;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.internal.utils.FileUtil;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.NoErrorLoggedRule;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.CompletionTriggerKind;
import org.eclipse.lsp4j.CreateFile;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RenameFile;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;

public class LSPEclipseUtilsTest extends AbstractTestWithProject {

	public final @Rule NoErrorLoggedRule noErrorLoggedRule = new NoErrorLoggedRule();

	@Test
	public void testOpenInEditorExternalFile() throws Exception {
		File externalFile = TestUtils.createTempFile("externalFile", ".txt");
		Location location = new Location(LSPEclipseUtils.toUri(externalFile).toString(), new Range(new Position(0, 0), new Position(0, 0)));
		LSPEclipseUtils.openInEditor(location, UI.getActivePage());
	}

	@Test
	public void testWorkspaceEdit_insertText() throws Exception {
		TextEdit textEdit = new TextEdit(new Range(new Position(0, 0), new Position(0, 0)), "insert");
		AbstractTextEditor editor = applyWorkspaceTextEdit(textEdit);
		Assert.assertEquals("insertHere", ((StyledText)editor.getAdapter(Control.class)).getText());
		Assert.assertEquals("insertHere", editor.getDocumentProvider().getDocument(editor.getEditorInput()).get());
	}

	@Test
	public void testWorkspaceEdit_WithExaggeratedRange() throws Exception {
		TextEdit textEdit = new TextEdit(new Range(new Position(0, 0), new Position(Integer.MAX_VALUE, Integer.MAX_VALUE)), "insert");
		AbstractTextEditor editor = applyWorkspaceTextEdit(textEdit);
		Assert.assertEquals("insert", ((StyledText)editor.getAdapter(Control.class)).getText());
		Assert.assertEquals("insert", editor.getDocumentProvider().getDocument(editor.getEditorInput()).get());
	}

	private AbstractTextEditor applyWorkspaceTextEdit(TextEdit textEdit) throws CoreException, PartInitException {
		IFile f = TestUtils.createFile(project, "dummy" + new Random().nextInt(), "Here");
		AbstractTextEditor editor = (AbstractTextEditor)TestUtils.openEditor(f);
		WorkspaceEdit workspaceEdit = new WorkspaceEdit(Collections.singletonMap(
			LSPEclipseUtils.toUri(f).toString(),
			Collections.singletonList(textEdit)));
		LSPEclipseUtils.applyWorkspaceEdit(workspaceEdit);
		return editor;
	}

	@Test
	public void testWorkspaceEditMultipleChanges() throws Exception {
		IFile f = TestUtils.createFile(project, "dummy", "Here\nHere2");
		AbstractTextEditor editor = (AbstractTextEditor)TestUtils.openEditor(f);
		final var edits = new LinkedList<TextEdit>();
		// order the TextEdits from the top of the document to the bottom
		edits.add(new TextEdit(new Range(new Position(0, 0), new Position(0, 0)), "abc"));
		edits.add(new TextEdit(new Range(new Position(1, 0), new Position(1, 0)), "abc"));
		WorkspaceEdit workspaceEdit = new WorkspaceEdit(Collections.singletonMap(
			LSPEclipseUtils.toUri(f).toString(), edits));
		// they should be applied from bottom to top
		LSPEclipseUtils.applyWorkspaceEdit(workspaceEdit);
		Assert.assertEquals("abcHere\nabcHere2", ((StyledText) editor.getAdapter(Control.class)).getText());
		Assert.assertEquals("abcHere\nabcHere2",
				editor.getDocumentProvider().getDocument(editor.getEditorInput()).get());
	}

	@Test
	public void testWorkspaceEdit_CreateAndPopulateFile() throws Exception {
		IFile file = project.getFile("test-file.test");
		LinkedList<Either<TextDocumentEdit, ResourceOperation>> edits = new LinkedList<>();
		// order the TextEdits from the top of the document to the bottom
		String uri = file.getLocation().toFile().toURI().toString();
		edits.add(Either.forRight(new CreateFile(uri)));
		edits.add(Either.forLeft(
				new TextDocumentEdit(new VersionedTextDocumentIdentifier(uri, null), Collections.singletonList(
						new TextEdit(new Range(new Position(0, 0), new Position(0, 0)), "abcHere\nabcHere2")))));
		WorkspaceEdit workspaceEdit = new WorkspaceEdit(edits);
		// they should be applied from bottom to top
		LSPEclipseUtils.applyWorkspaceEdit(workspaceEdit);
		assertTrue(file.exists());
		assertEquals("abcHere\nabcHere2", new String(Files.readAllBytes(file.getLocation().toFile().toPath())));
	}

	@Test
	public void testURIToResourceMapping() throws CoreException { // bug 508841
		IFile file = project.getFile("res");
		file.create(new ByteArrayInputStream(new byte[0]), true, new NullProgressMonitor());
		Assert.assertEquals(file, LSPEclipseUtils.findResourceFor(file.getLocationURI().toString()));

		project.getFile("suffix").create(new ByteArrayInputStream(new byte[0]), true, new NullProgressMonitor());
		IProject project2 = TestUtils.createProject(project.getName() + "suffix");
		Assert.assertEquals(project2, LSPEclipseUtils.findResourceFor(project2.getLocationURI().toString()));
	}

	@Test
	public void testReturnMostNestedFileRegardlessArrayOrder() throws CoreException { // like maven nested modules
		IFile mostNestedFile = project.getFile("res");
		mostNestedFile.create(new ByteArrayInputStream(new byte[0]), true, new NullProgressMonitor());

		IFolder folder = project.getFolder("folder");
		folder.create(true, true, new NullProgressMonitor());

		IFile someFile = project.getFile("folder/res");
		someFile.create(new ByteArrayInputStream(new byte[0]), true, new NullProgressMonitor());

		Assert.assertEquals(mostNestedFile, LSPEclipseUtils.findMostNested(new IFile[] {mostNestedFile, someFile}));
		Assert.assertEquals(mostNestedFile, LSPEclipseUtils.findMostNested(new IFile[] {someFile, mostNestedFile}));
	}

	@Test
	public void testLinkedResourceURIToResourceMapping() throws CoreException, IOException { // bug 577159
		Path externalFile = Files.createTempFile("tmp_file-", null);
		Path externalFolder = Files.createTempDirectory("tmp_dir-");

		IFile linkedFile = project.getFile("linked_file");
		linkedFile.createLink(externalFile.toUri(), 0, new NullProgressMonitor());
		Assert.assertTrue(linkedFile.isLinked());
		Assert.assertEquals(linkedFile, LSPEclipseUtils.findResourceFor(linkedFile.getLocationURI().toString()));

		IFolder linkedFolder = project.getFolder("linked_folder");
		linkedFolder.createLink(externalFolder.toUri(), 0, new NullProgressMonitor());
		Assert.assertTrue(linkedFolder.isLinked());
		Assert.assertEquals(linkedFolder,
				LSPEclipseUtils.findResourceFor(linkedFolder.getLocationURI().toString()));

		Files.createFile(externalFolder.resolve("child"));
		IFile linkedFolderFile = linkedFolder.getFile("child");
		Assert.assertEquals(linkedFolderFile,
				LSPEclipseUtils.findResourceFor(linkedFolderFile.getLocationURI().toString()));
	}

	@Test
	public void testVirtualResourceURIToResourceMapping() throws CoreException, IOException { // bug 577159
		Path externalFile = Files.createTempFile("tmp_file-", null);
		Path externalFolder = Files.createTempDirectory("tmp_dir-");

		IFolder virtualFolder = project.getFolder("virtual_folder");
		virtualFolder.create(IResource.VIRTUAL, true, new NullProgressMonitor());

		Assert.assertEquals(virtualFolder.isVirtual(), true);
		Assert.assertEquals(virtualFolder.getLocationURI().toString(), "virtual:/virtual");
		Assert.assertEquals(virtualFolder.getRawLocationURI().toString(), "virtual:/virtual");
		// getLocationURI()/getRawLocationURI() of virtual folders cannot be used to resolve a workspace resource
		// thus LSPEclipseUtils.findResourceFor() returns null
		Assert.assertEquals(null, LSPEclipseUtils.findResourceFor(virtualFolder.getLocationURI().toString()));

		IFile linkedFile = virtualFolder.getFile("linked_file");
		linkedFile.createLink(externalFile.toUri(), 0, new NullProgressMonitor());
		Assert.assertTrue(linkedFile.isLinked());
		Assert.assertEquals(linkedFile, LSPEclipseUtils.findResourceFor(linkedFile.getLocationURI().toString()));

		IFolder linkedFolder = virtualFolder.getFolder("linked_folder");
		linkedFolder.createLink(externalFolder.toUri(), 0, new NullProgressMonitor());
		Assert.assertTrue(linkedFolder.isLinked());
		Assert.assertEquals(linkedFolder,
				LSPEclipseUtils.findResourceFor(linkedFolder.getLocationURI().toString()));

		Files.createFile(externalFolder.resolve("child"));
		IFile linkedFolderFile = linkedFolder.getFile("child");
		Assert.assertEquals(linkedFolderFile,
				LSPEclipseUtils.findResourceFor(linkedFolderFile.getLocationURI().toString()));
	}

	@Test
	public void testCustomURIToResourceMapping() throws CoreException { // bug 576425
		URI uri = URI.create("other://a/res.txt");

		// this project name is magic, see UriToResourceAdapterFactory#getAdapter(Object, Class)
		project = TestUtils.createProject(LSPEclipseUtilsTest.class.getSimpleName() + uri.getScheme());

		IFile file = project.getFile("res.txt");
		file.createLink(uri, IResource.REPLACE | IResource.ALLOW_MISSING_LOCAL, new NullProgressMonitor());
		Assert.assertEquals(file, LSPEclipseUtils.findResourceFor(file.getLocationURI().toString()));
		Assert.assertEquals(file, LSPEclipseUtils.getFileHandle(file.getLocationURI()));
	}

	@Test
	public void testCustomResourceToURIMapping() throws CoreException { // bug 576425
		URI uri = URI.create("other://res.txt");
		IFile file = project.getFile("res.txt");
		file.createLink(uri, IResource.REPLACE | IResource.ALLOW_MISSING_LOCAL, new NullProgressMonitor());
		Assert.assertEquals(LSPEclipseUtils.toUri(file).toString(), "other://a/res.txt");
	}

	@Test
	public void testApplyTextEditLongerThanOrigin() throws Exception {
		IFile file = TestUtils.createUniqueTestFile(project, "line1\nlineInsertHere");
		ITextViewer viewer = TestUtils.openTextViewer(file);
		TextEdit textEdit = new TextEdit(new Range(new Position(1, 4), new Position(1, 4 + "InsertHere".length())), "Inserted");
		IDocument document = viewer.getDocument();
		LSPEclipseUtils.applyEdit(textEdit, document);
		Assert.assertEquals("line1\nlineInserted", document.get());
	}

	@Test
	public void testApplyTextEditShorterThanOrigin() throws Exception {
		IFile file = TestUtils.createUniqueTestFile(project, "line1\nlineHERE");
		ITextViewer viewer = TestUtils.openTextViewer(file);
		TextEdit textEdit = new TextEdit(new Range(new Position(1, 4), new Position(1, 4 + "HERE".length())), "Inserted");
		IDocument document = viewer.getDocument();
		LSPEclipseUtils.applyEdit(textEdit, document);
		Assert.assertEquals("line1\nlineInserted", document.get());
	}

	@Test
	public void testTextEditInsertSameOffset() throws Exception {
		IFile file = TestUtils.createUniqueTestFile(project, "");
		ITextViewer viewer = TestUtils.openTextViewer(file);
		TextEdit[] edits = new TextEdit[] {
				new TextEdit(new Range(new Position(0, 0), new Position(0, 0)), " throws "),
				new TextEdit(new Range(new Position(0, 0), new Position(0, 0)), "Exception") };
		IDocument document = viewer.getDocument();
		LSPEclipseUtils.applyEdits(document, Arrays.asList(edits));
		Assert.assertEquals(" throws Exception", document.get());
	}

	@Test
	public void testTextEditSplittedLineEndings() throws Exception {
		IFile file = TestUtils.createUniqueTestFile(project, "line1\r\nline2\r\nline3\r\n");
		ITextViewer viewer = TestUtils.openTextViewer(file);
		// GIVEN a TextEdit which splits the '\r\n' line ending in the third line:
		TextEdit[] edits = new TextEdit[] { new TextEdit(new Range(new Position(0, 0), new Position(2, 6)), "line3\r\nline2\r\nline1\r") };
		IDocument document = viewer.getDocument();
		int linesBeforeApplyEdits = document.getNumberOfLines();
		// WHEN the TextEdit gets applied to the document:
		LSPEclipseUtils.applyEdits(document, Arrays.asList(edits));
		// THEN line1 has been swapped with line 3:
		Assert.assertEquals("line3\r\nline2\r\nline1\r\n", document.get());
		// AND the number of lines is still the same, because we have not appended a line:
		Assert.assertEquals(linesBeforeApplyEdits, document.getNumberOfLines());
	}

	@Test
	public void testURICreationUnix() {
		Assume.assumeFalse(Platform.OS_WIN32.equals(Platform.getOS()));
		Assert.assertEquals("file:///test%20with%20space", LSPEclipseUtils.toUri(new File("/test with space")).toString());
	}

	@Test
	public void testUNCwindowsURI() {
		Assume.assumeTrue(Platform.OS_WIN32.equals(Platform.getOS()));
		URI preferredURI = URI.create("file://localhost/c$/Windows");
		URI javaURI = URI.create("file:////localhost/c$/Windows");

		File file1 = LSPEclipseUtils.fromUri(preferredURI);
		File file2 = LSPEclipseUtils.fromUri(javaURI);
		Assert.assertEquals(file1, file2);
	}

	@Test
	public void testToWorkspaceFolder() throws Exception {
		WorkspaceFolder folder = LSPEclipseUtils.toWorkspaceFolder(project);
		Assert.assertEquals(project.getName(), folder.getName());
		Assert.assertEquals("file://", folder.getUri().substring(0, "file://".length()));
	}

	@Test
	public void testResourceOperations() throws Exception {
		IFile targetFile = project.getFile("some/folder/file.txt");
		LSPEclipseUtils.applyWorkspaceEdit(new WorkspaceEdit(
				Collections.singletonList(Either.forRight(new CreateFile(targetFile.getLocationURI().toString())))));
		assertTrue(targetFile.exists());
		LSPEclipseUtils.applyWorkspaceEdit(new WorkspaceEdit(Collections.singletonList(Either.forLeft(
				new TextDocumentEdit(new VersionedTextDocumentIdentifier(targetFile.getLocationURI().toString(), 1),
						Collections.singletonList(
								new TextEdit(new Range(new Position(0, 0), new Position(0, 0)), "hello")))))));
		assertEquals("hello", readContent(targetFile));
		IFile otherFile = project.getFile("another/folder/file.lol");
		LSPEclipseUtils.applyWorkspaceEdit(new WorkspaceEdit(Collections.singletonList(Either.forRight(
				new RenameFile(targetFile.getLocationURI().toString(), otherFile.getLocationURI().toString())))));
		assertFalse(targetFile.exists());
		assertTrue(otherFile.exists());
		assertEquals("hello", readContent(otherFile));
	}

	@Test
	public void createExternalFile() throws Exception {
		File file = TestUtils.createTempFile(getClass() + "editExternalFile", ".whatever");
		file.delete();
		assertFalse(file.exists());
		WorkspaceEdit we = new WorkspaceEdit(
				Collections.singletonList(Either.forRight(new CreateFile(file.toURI().toString()))));
		LSPEclipseUtils.applyWorkspaceEdit(we);
		assertTrue(file.isFile());
	}

	@Test
	public void editExternalFile() throws Exception {
		File file = TestUtils.createTempFile(getClass() + "editExternalFile", ".whatever");
		TextEdit te = new TextEdit();
		te.setRange(new Range(new Position(0, 0), new Position(0, 0)));
		te.setNewText("abc\ndef");
		TextDocumentEdit docEdit = new TextDocumentEdit(
				new VersionedTextDocumentIdentifier(file.toURI().toString(), null),
				Collections.singletonList(te));
		WorkspaceEdit we = new WorkspaceEdit(Collections.singletonList(Either.forLeft(docEdit)));
		LSPEclipseUtils.applyWorkspaceEdit(we);
		assertTrue(file.isFile());
		assertEquals("abc\ndef", new String(Files.readAllBytes(file.toPath())));
	}

	@Test
	public void renameExternalFile() throws Exception {
		File oldFile = TestUtils.createTempFile(getClass() + "editExternalFile", ".whatever");
		File newFile = new File(oldFile.getAbsolutePath() + "_renamed");
		TestUtils.addManagedTempFile(newFile);
		WorkspaceEdit we = new WorkspaceEdit(Collections.singletonList(
				Either.forRight(new RenameFile(oldFile.toURI().toString(), newFile.toURI().toString()))));
		LSPEclipseUtils.applyWorkspaceEdit(we);
		assertFalse(oldFile.isFile());
		assertTrue(newFile.isFile());
	}

	private String readContent(IFile targetFile) throws IOException, CoreException {
		try (ByteArrayOutputStream stream = new ByteArrayOutputStream(
				(int) targetFile.getLocation().toFile().length());
				InputStream contentStream = targetFile.getContents();) {
			FileUtil.transferStreams(contentStream, stream, targetFile.getFullPath().toString(),
					new NullProgressMonitor());
			// targetFile.getContents().transferTo(stream);
			return new String(stream.toByteArray());
		}
	}

	@Test
	public void testTextEditDoesntAutomaticallySaveOpenResourceFiles() throws Exception {
		IFile targetFile = project.getFile("blah.txt");
		targetFile.create(new ByteArrayInputStream("".getBytes()), true, null);
		IEditorPart editor = IDE.openEditor(UI.getActivePage(),
				targetFile,
				"org.eclipse.ui.genericeditor.GenericEditor");
		TextEdit te = new TextEdit();
		te.setRange(new Range(new Position(0, 0), new Position(0, 0)));
		te.setNewText("abc\ndef");
		TextDocumentEdit docEdit = new TextDocumentEdit(
				new VersionedTextDocumentIdentifier(LSPEclipseUtils.toUri(targetFile).toString(), null),
				Collections.singletonList(te));
		WorkspaceEdit we = new WorkspaceEdit(Collections.singletonList(Either.forLeft(docEdit)));
		LSPEclipseUtils.applyWorkspaceEdit(we);
		assertEquals("abc\ndef", ((StyledText) ((AbstractTextEditor) editor).getAdapter(Control.class)).getText());
		assertTrue(editor.isDirty());
	}

	@Test
	public void testTextEditDoesntAutomaticallySaveOpenExternalFiles() throws Exception {
		File file = TestUtils.createTempFile("testTextEditDoesntAutomaticallySaveOpenExternalFiles", ".whatever");
		IEditorPart editor = IDE.openInternalEditorOnFileStore(UI.getActivePage(), EFS.getStore(file.toURI()));
		TextEdit te = new TextEdit();
		te.setRange(new Range(new Position(0, 0), new Position(0, 0)));
		te.setNewText("abc\ndef");
		TextDocumentEdit docEdit = new TextDocumentEdit(
				new VersionedTextDocumentIdentifier(file.toURI().toString(), null),
				Collections.singletonList(te));
		WorkspaceEdit we = new WorkspaceEdit(Collections.singletonList(Either.forLeft(docEdit)));
		LSPEclipseUtils.applyWorkspaceEdit(we);
		assertEquals("abc\ndef", ((StyledText) ((AbstractTextEditor) editor).getAdapter(Control.class)).getText());
		assertTrue(editor.isDirty());
	}

	@Test
	public void testGetOpenEditorExternalFile() throws Exception {
		File file = TestUtils.createTempFile("testDiagnosticsOnExternalFile", ".lspt");
		try (FileOutputStream out = new FileOutputStream(file)) {
			out.write('a');
		}
		IDE.openEditorOnFileStore(UI.getActivePage(), EFS.getStore(file.toURI()));
		Assert.assertNotEquals(Collections.emptySet(), LSPEclipseUtils.findOpenEditorsFor(file.toURI()));
	}

	@Test
	public void testToCompletionParams_EmptyDocument() throws Exception {
		// Given an empty file/document
		var file = TestUtils.createFile(project, "dummy" + new Random().nextInt(), "");
		var triggerChars = new  char[] {':', '>'};
		// When toCompletionParams get called with offset == 0 and document.getLength() == 0:
		var param = LSPEclipseUtils.toCompletionParams(file.getLocationURI(), 0, LSPEclipseUtils.getDocument(file), triggerChars);
		// Then no context has been added to param:
		Assert.assertNull(param.getContext());
	}

	@Test
	public void testToCompletionParams_ZeroOffset() throws Exception {
		// Given a non empty file/document containing a non trigger character at position 3:
		var file = TestUtils.createFile(project, "dummy" + new Random().nextInt(), "std");
		var triggerChars = new  char[] {':', '>'};
		// When toCompletionParams get called with offset == 0 and document.getLength() > 0:
		var param = LSPEclipseUtils.toCompletionParams(file.getLocationURI(), 0, LSPEclipseUtils.getDocument(file), triggerChars);
		// Then the trigger kind is Invoked:
		Assert.assertEquals(param.getContext().getTriggerKind(), CompletionTriggerKind.Invoked);
	}

	@Test
	public void testToCompletionParams_MatchingTriggerCharacter() throws Exception {
		// Given a non empty file/document containing a trigger character at position 4:
		var file = TestUtils.createFile(project, "dummy" + new Random().nextInt(), "std:");
		var triggerChars = new  char[] {':', '>'};
		// When toCompletionParams get called with offset > 0 and document.getLength() > 0:
		var param = LSPEclipseUtils.toCompletionParams(file.getLocationURI(), 4, LSPEclipseUtils.getDocument(file), triggerChars);
		// Then the context has been added with a colon as trigger character:
		Assert.assertEquals(param.getContext().getTriggerCharacter(), ":");
		// And the trigger kind is TriggerCharacter:
		Assert.assertEquals(param.getContext().getTriggerKind(), CompletionTriggerKind.TriggerCharacter);
	}

	@Test
	public void testToCompletionParams_NonMatchingTriggerCharacter() throws Exception {
		// Given a non empty file/document containing a non trigger character at position 3:
		var file = TestUtils.createFile(project, "dummy" + new Random().nextInt(), "std");
		var triggerChars = new  char[] {':', '>'};
		// When toCompletionParams get called with offset > 0 and document.getLength() > 0:
		var param = LSPEclipseUtils.toCompletionParams(file.getLocationURI(), 3, LSPEclipseUtils.getDocument(file), triggerChars);
		// Then the trigger kind is Invoked:
		Assert.assertEquals(param.getContext().getTriggerKind(), CompletionTriggerKind.Invoked);
	}

	@Test
	public void parseRange_shouldReturnRange_UriWithStartLineNo() {
		Range actual = LSPEclipseUtils.parseRange("file:///a/b#L35");
		assertEquals(34, actual.getStart().getLine());
	}

	@Test
	public void parseRange_shouldReturnRange_UriWithStartLineNoEndLineNo() {
		Range actual = LSPEclipseUtils.parseRange("file:///a/b#L35-L36");
		assertEquals(34, actual.getStart().getLine());
		assertEquals(35, actual.getEnd().getLine());
	}

	@Test
	public void parseRange_shouldReturnRange_UriWithStartLineStartCharNoEndLineNo() {
		Range actual = LSPEclipseUtils.parseRange("file:///a/b#L35,10");
		assertEquals(34, actual.getStart().getLine());
		assertEquals(9, actual.getStart().getCharacter());
	}

	@Test
	public void parseRange_shouldReturnRange_UriWithStartLineStartCharWithEndLineNo() {
		Range actual = LSPEclipseUtils.parseRange("file:///a/b#L35,10-L37");
		assertEquals(34, actual.getStart().getLine());
		assertEquals(9, actual.getStart().getCharacter());
		assertEquals(36, actual.getEnd().getLine());
		assertEquals(9, actual.getEnd().getCharacter());
	}

	@Test
	public void parseRange_shouldReturnRange_UriWithStartLineStartCharWithEndLineNoWithEndChar() {
		Range actual = LSPEclipseUtils.parseRange("file:///a/b#L35,10-L37,34");
		assertEquals(34, actual.getStart().getLine());
		assertEquals(9, actual.getStart().getCharacter());
		assertEquals(36, actual.getEnd().getLine());
		assertEquals(33, actual.getEnd().getCharacter());
	}

	@Test
	public void parseRange_shouldReturnRange_UriWithoutLCharacter() {
		Range actual = LSPEclipseUtils.parseRange("file:///a/b#35,10-37,34");
		assertEquals(34, actual.getStart().getLine());
		assertEquals(9, actual.getStart().getCharacter());
		assertEquals(36, actual.getEnd().getLine());
		assertEquals(33, actual.getEnd().getCharacter());
	}

	@Test
	public void parseRange_shouldReturnNullRange_BlankFragment() {
		Range actual = LSPEclipseUtils.parseRange("file:///a/b#");
		assertNull(actual);
	}

	@Test
	public void parseRange_shouldReturnNullRange_NoFragment() {
		Range actual = LSPEclipseUtils.parseRange("file:///a/b");
		assertNull(actual);
	}
}
