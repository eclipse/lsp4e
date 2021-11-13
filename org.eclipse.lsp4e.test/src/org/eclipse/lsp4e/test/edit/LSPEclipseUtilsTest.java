/*******************************************************************************
 * Copyright (c) 2016, 2019 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Remy Suen <remy.suen@gmail.com> - Bug 520052 - Rename assumes that workspace edits are in reverse order
 *******************************************************************************/
package org.eclipse.lsp4e.test.edit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.test.AllCleanRule;
import org.eclipse.lsp4e.test.NoErrorLoggedRule;
import org.eclipse.lsp4e.test.TestUtils;
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
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;

public class LSPEclipseUtilsTest {

	@Rule public NoErrorLoggedRule rule = new NoErrorLoggedRule(LanguageServerPlugin.getDefault().getLog());
	@Rule public AllCleanRule clear = new AllCleanRule();

	@Test
	public void testOpenInEditorExternalFile() throws Exception {
		IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		File externalFile = File.createTempFile("externalFile", ".txt");
		try {
			Location location = new Location(LSPEclipseUtils.toUri(externalFile).toString(), new Range(new Position(0, 0), new Position(0, 0)));
			LSPEclipseUtils.openInEditor(location, page);
		} finally {
			page.closeEditor(page.getActiveEditor(), false);
			externalFile.delete();
		}
	}

	@Test
	public void testWorkspaceEdit() throws Exception {
		IProject p = TestUtils.createProject(getClass().getSimpleName() + System.currentTimeMillis());
		IFile f = TestUtils.createFile(p, "dummy", "Here");
		AbstractTextEditor editor = (AbstractTextEditor)TestUtils.openEditor(f);
		try {
			WorkspaceEdit workspaceEdit = new WorkspaceEdit(Collections.singletonMap(
				LSPEclipseUtils.toUri(f).toString(),
				Collections.singletonList(new TextEdit(new Range(new Position(0, 0), new Position(0, 0)), "insert"))));
			LSPEclipseUtils.applyWorkspaceEdit(workspaceEdit);
			Assert.assertEquals("insertHere", ((StyledText)editor.getAdapter(Control.class)).getText());
			Assert.assertEquals("insertHere", editor.getDocumentProvider().getDocument(editor.getEditorInput()).get());
		} finally {
			editor.close(false);
			p.delete(true, new NullProgressMonitor());
		}
	}

	@Test
	public void testWorkspaceEditMultipleChanges() throws Exception {
		IProject p = TestUtils.createProject(getClass().getSimpleName() + System.currentTimeMillis());
		IFile f = TestUtils.createFile(p, "dummy", "Here\nHere2");
		AbstractTextEditor editor = (AbstractTextEditor)TestUtils.openEditor(f);
		try {
			LinkedList<TextEdit> edits = new LinkedList<TextEdit>();
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
		} finally {
			editor.close(false);
			p.delete(true, new NullProgressMonitor());
		}
	}

	@Test
	public void testWorkspaceEdit_CreateAndPopulateFile() throws Exception {
		IProject p = TestUtils.createProject(getClass().getSimpleName() + System.currentTimeMillis());
		IFile file = p.getFile("test-file.test");
		try {
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
		} finally {
			p.delete(true, new NullProgressMonitor());
		}
	}

	@Test
	public void testURIToResourceMapping() throws CoreException { // bug 508841
		IProject project1 = null;
		IProject project2 = null;
		try {
			project1 = TestUtils.createProject(getClass().getSimpleName() + System.currentTimeMillis());
			IFile file = project1.getFile("res");
			file.create(new ByteArrayInputStream(new byte[0]), true, new NullProgressMonitor());
			Assert.assertEquals(file, LSPEclipseUtils.findResourceFor(file.getLocationURI().toString()));

			project1.getFile("suffix").create(new ByteArrayInputStream(new byte[0]), true, new NullProgressMonitor());
			project2 = TestUtils.createProject(project1.getName() + "suffix");
			Assert.assertEquals(project2, LSPEclipseUtils.findResourceFor(project2.getLocationURI().toString()));
		} finally {
			TestUtils.delete(project1, project2);
		}
	}

	@Test
	public void testLinkedResourceURIToResourceMapping() throws CoreException, IOException { // bug 577159
		IProject project1 = null;
		Path externalFile = null;
		Path externalFolder = null;
		try {
			externalFile = Files.createTempFile("tmp_file-", null);
			externalFolder = Files.createTempDirectory("tmp_dir-");

			project1 = TestUtils.createProject(getClass().getSimpleName() + System.currentTimeMillis());

			IFile linkedFile = project1.getFile("linked_file");
			linkedFile.createLink(externalFile.toUri(), 0, new NullProgressMonitor());
			Assert.assertTrue(linkedFile.isLinked());
			Assert.assertEquals(linkedFile, LSPEclipseUtils.findResourceFor(linkedFile.getLocationURI().toString()));

			IFolder linkedFolder = project1.getFolder("linked_folder");
			linkedFolder.createLink(externalFolder.toUri(), 0, new NullProgressMonitor());
			Assert.assertTrue(linkedFolder.isLinked());
			Assert.assertEquals(linkedFolder,
					LSPEclipseUtils.findResourceFor(linkedFolder.getLocationURI().toString()));

			Files.createFile(externalFolder.resolve("child"));
			IFile linkedFolderFile = linkedFolder.getFile("child");
			Assert.assertEquals(linkedFolderFile,
					LSPEclipseUtils.findResourceFor(linkedFolderFile.getLocationURI().toString()));
		} finally {
			TestUtils.delete(project1);
			TestUtils.delete(externalFile, externalFolder);
		}
	}

	@Test
	public void testVirtualResourceURIToResourceMapping() throws CoreException, IOException { // bug 577159
		IProject project1 = null;
		Path externalFile = null;
		Path externalFolder = null;
		try {
			externalFile = Files.createTempFile("tmp_file-", null);
			externalFolder = Files.createTempDirectory("tmp_dir-");

			project1 = TestUtils.createProject(getClass().getSimpleName() + System.currentTimeMillis());

			IFolder virtualFolder = project1.getFolder("virtual_folder");
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
		} finally {
			TestUtils.delete(project1);
			TestUtils.delete(externalFile, externalFolder);
		}
	}

	@Test
	public void testCustomURIToResourceMapping() throws CoreException { // bug 576425
	   IProject project = null;
	   try {
		    URI uri = URI.create("other://a/res.txt");
			project = TestUtils.createProject(getClass().getSimpleName() + uri.getScheme());
			IFile file = project.getFile("res.txt");
			file.createLink(uri, IResource.REPLACE | IResource.ALLOW_MISSING_LOCAL, new NullProgressMonitor());
			Assert.assertEquals(file, LSPEclipseUtils.findResourceFor(file.getLocationURI().toString()));
			Assert.assertEquals(file, LSPEclipseUtils.getFileHandle(file.getLocationURI().toString()));
		} finally {
			if (project != null) { project.delete(true, new NullProgressMonitor()); }
		}
	}

	@Test
	public void testCustomResourceToURIMapping() throws CoreException { // bug 576425
	   IProject project = null;
	   try {
		    URI uri = URI.create("other://res.txt");
			project = TestUtils.createProject(getClass().getSimpleName() + uri.getScheme());
			IFile file = project.getFile("res.txt");
			file.createLink(uri, IResource.REPLACE | IResource.ALLOW_MISSING_LOCAL, new NullProgressMonitor());
			Assert.assertEquals(LSPEclipseUtils.toUri(file).toString(), "other://a/res.txt");
		} finally {
			if (project != null) { project.delete(true, new NullProgressMonitor()); }
		}
	}

	@Test
	public void testApplyTextEditLongerThanOrigin() throws Exception {
		IProject project = null;
		IEditorPart editor = null;
		try {
			project = TestUtils.createProject(getClass().getSimpleName() + System.currentTimeMillis());
			IFile file = TestUtils.createUniqueTestFile(project, "line1\nlineInsertHere");
			editor = TestUtils.openEditor(file);
			ITextViewer viewer = TestUtils.getTextViewer(editor);
			TextEdit textEdit = new TextEdit(new Range(new Position(1, 4), new Position(1, 4 + "InsertHere".length())), "Inserted");
			IDocument document = viewer.getDocument();
			LSPEclipseUtils.applyEdit(textEdit, document);
			Assert.assertEquals("line1\nlineInserted", document.get());
		} finally {
			TestUtils.closeEditor(editor, false);
			if (project != null) { project.delete(true, new NullProgressMonitor()); }
		}
	}

	@Test
	public void testApplyTextEditShorterThanOrigin() throws Exception {
		IProject project = null;
		IEditorPart editor = null;
		try {
			project = TestUtils.createProject(getClass().getSimpleName() + System.currentTimeMillis());
			IFile file = TestUtils.createUniqueTestFile(project, "line1\nlineHERE");
			editor = TestUtils.openEditor(file);
			ITextViewer viewer = TestUtils.getTextViewer(editor);
			TextEdit textEdit = new TextEdit(new Range(new Position(1, 4), new Position(1, 4 + "HERE".length())), "Inserted");
			IDocument document = viewer.getDocument();
			LSPEclipseUtils.applyEdit(textEdit, document);
			Assert.assertEquals("line1\nlineInserted", document.get());
		} finally {
			TestUtils.closeEditor(editor, false);
			if (project != null) { project.delete(true, new NullProgressMonitor()); }
		}
	}

	@Test
	public void testTextEditInsertSameOffset() throws Exception {
		IProject project = null;
		IEditorPart editor = null;
		try {
			project = TestUtils.createProject(getClass().getSimpleName() + System.currentTimeMillis());
			IFile file = TestUtils.createUniqueTestFile(project, "");
			editor = TestUtils.openEditor(file);
			ITextViewer viewer = TestUtils.getTextViewer(editor);
			TextEdit[] edits = new TextEdit[] {
					new TextEdit(new Range(new Position(0, 0), new Position(0, 0)), " throws "),
					new TextEdit(new Range(new Position(0, 0), new Position(0, 0)), "Exception") };
			IDocument document = viewer.getDocument();
			LSPEclipseUtils.applyEdits(document, Arrays.asList(edits));
			Assert.assertEquals(" throws Exception", document.get());
		} finally {
			TestUtils.closeEditor(editor, false);
			if (project != null) {
				project.delete(true, new NullProgressMonitor());
			}
		}

	}

	@Test
	public void testURICreationUnix() {
		Assume.assumeFalse(Platform.OS_WIN32.equals(Platform.getOS()));
		Assert.assertEquals("file:///test%20with%20space", LSPEclipseUtils.toUri(new File("/test with space")).toString());
	}

	@Test
	public void testResourceOperations() throws Exception {
		IProject project = TestUtils.createProject("testResourceOperations");
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
		File file = File.createTempFile(getClass() + "editExternalFile", ".whatever");
		file.delete();
		try {
			assertFalse(file.exists());
			WorkspaceEdit we = new WorkspaceEdit(
					Collections.singletonList(Either.forRight(new CreateFile(file.toURI().toString()))));
			LSPEclipseUtils.applyWorkspaceEdit(we);
			assertTrue(file.isFile());
		} finally {
			if (file.isFile()) {
				Files.delete(file.toPath());
			}
		}
	}

	@Test
	public void editExternalFile() throws Exception {
		File file = File.createTempFile(getClass() + "editExternalFile", ".whatever");
		try {
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
		} finally {
			Files.deleteIfExists(file.toPath());
		}
	}

	@Test
	public void renameExternalFile() throws Exception {
		File oldFile = File.createTempFile(getClass() + "editExternalFile", ".whatever");
		File newFile = new File(oldFile.getAbsolutePath() + "_renamed");
		try {
			WorkspaceEdit we = new WorkspaceEdit(Collections.singletonList(
					Either.forRight(new RenameFile(oldFile.toURI().toString(), newFile.toURI().toString()))));
			LSPEclipseUtils.applyWorkspaceEdit(we);
			assertFalse(oldFile.isFile());
			assertTrue(newFile.isFile());
		} finally {
			Files.deleteIfExists(oldFile.toPath());
			Files.deleteIfExists(newFile.toPath());
		}
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
		IProject project = TestUtils.createProject("testTextEditDoesntAutomaticallySaveOpenFiles");
		IFile targetFile = project.getFile("blah.txt");
		targetFile.create(new ByteArrayInputStream("".getBytes()), true, null);
		IEditorPart editor = IDE.openEditor(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(),
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
		File file = File.createTempFile("testTextEditDoesntAutomaticallySaveOpenExternalFiles", ".whatever");
		IEditorPart editor = IDE.openInternalEditorOnFileStore(
				PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(), EFS.getStore(file.toURI()));
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
		File file = File.createTempFile("testDiagnosticsOnExternalFile", ".lspt");
		try {
			try (
				FileOutputStream out = new FileOutputStream(file);
			) {
				out.write('a');
			}
			IDE.openEditorOnFileStore(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(), EFS.getStore(file.toURI()));
			Assert.assertNotEquals(Collections.emptySet(), LSPEclipseUtils.findOpenEditorsFor(file.toURI()));
		} finally {
			Files.deleteIfExists(file.toPath());
		}
	}
}
