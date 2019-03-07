/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - Added some suites
 *******************************************************************************/
package org.eclipse.lsp4e.test.rename;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.commands.Command;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.operations.rename.LSPRenameHandler;
import org.eclipse.lsp4e.operations.rename.LSPRenameProcessor;
import org.eclipse.lsp4e.test.AllCleanRule;
import org.eclipse.lsp4e.test.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.ltk.core.refactoring.participants.ProcessorBasedRefactoring;
import org.eclipse.ui.IWorkbenchCommandConstants;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.texteditor.ITextEditor;
import org.junit.Rule;
import org.junit.Test;

public class RenameTest {

	@Rule public AllCleanRule clear = new AllCleanRule();

	@Test
	public void testRenameHandled() throws Exception {
		IProject project = TestUtils.createProject("blah");
		IFile file = TestUtils.createUniqueTestFile(project, "old");
		ITextEditor editor = (ITextEditor) TestUtils.openEditor(file);
		editor.selectAndReveal(1, 0);
		ICommandService commandService = PlatformUI.getWorkbench().getService(ICommandService.class);
		Command command = commandService.getCommand(IWorkbenchCommandConstants.FILE_RENAME);
		assertTrue(command.isEnabled() && command.isHandled());
	}

	@Test
	public void testRenameRefactoring() throws Exception {
		IProject project = TestUtils.createProject("blah");
		IFile file = TestUtils.createUniqueTestFile(project, "old");
		MockLanguageServer.INSTANCE.getTextDocumentService().setRenameEdit(createSimpleMockRenameEdit(LSPEclipseUtils.toUri(file)));
		IDocument document = LSPEclipseUtils.getDocument(file);
		LanguageServiceAccessor.getLanguageServers(document, LSPRenameHandler::isRenameProvider).thenAccept(languageServers -> {
			LSPRenameProcessor processor = new LSPRenameProcessor(LSPEclipseUtils.getDocument(file), languageServers.get(0), 0);
			processor.setNewName("new");
			try {
				ProcessorBasedRefactoring processorBasedRefactoring = new ProcessorBasedRefactoring(processor);
				processorBasedRefactoring.checkAllConditions(new NullProgressMonitor());
				processorBasedRefactoring.createChange(new NullProgressMonitor()).perform(new NullProgressMonitor());
			} catch (CoreException e) {
				e.printStackTrace();
			}
		}).join();
		assertEquals("new", document.get());
	}

	@Test
	public void testRenameRefactoringExternalFile() throws Exception {
		File file = File.createTempFile("testPerformOperationExternalFile", ".lspt");
		MockLanguageServer.INSTANCE.getTextDocumentService().setRenameEdit(createSimpleMockRenameEdit(file.toURI()));
		IFileStore store = EFS.getStore(file.toURI());
		ITextFileBufferManager manager = ITextFileBufferManager.DEFAULT;
		try {
			manager.connectFileStore(store, new NullProgressMonitor());
			IDocument document = ((ITextFileBuffer)manager.getFileStoreFileBuffer(store)).getDocument();
			document.set("old");
			LanguageServiceAccessor.getLanguageServers(document, LSPRenameHandler::isRenameProvider).thenAccept(languageServers -> {
				LSPRenameProcessor processor = new LSPRenameProcessor(document, languageServers.get(0), 0);
				processor.setNewName("new");
				try {
					ProcessorBasedRefactoring processorBasedRefactoring = new ProcessorBasedRefactoring(processor);
					processorBasedRefactoring.checkAllConditions(new NullProgressMonitor());
					processorBasedRefactoring.createChange(new NullProgressMonitor()).perform(new NullProgressMonitor());
				} catch (CoreException e) {
					e.printStackTrace();
				}
			}).join();
			assertEquals("new", document.get());
		} finally {
			manager.disconnectFileStore(store, new NullProgressMonitor());
			Files.deleteIfExists(file.toPath());
		}
	}

	@Test
	public void testRenameChangeAlsoExternalFile() throws Exception {
		IProject project = TestUtils.createProject("blah");
		IFile workspaceFile = TestUtils.createUniqueTestFile(project, "old");
		File externalFile = File.createTempFile("testRenameChangeAlsoExternalFile", ".lspt");
		try {
			Files.write(externalFile.toPath(), "old".getBytes());
			Map<String, List<TextEdit>> edits = new HashMap<>(2, 1.f);
			edits.put(LSPEclipseUtils.toUri(workspaceFile).toString(), Collections.singletonList(new TextEdit(new Range(new Position(0, 0), new Position(0, 3)), "new")));
			edits.put(LSPEclipseUtils.toUri(externalFile).toString(), Collections.singletonList(new TextEdit(new Range(new Position(0, 0), new Position(0, 3)), "new")));
			MockLanguageServer.INSTANCE.getTextDocumentService().setRenameEdit(new WorkspaceEdit(edits));
			IDocument document = LSPEclipseUtils.getDocument(workspaceFile);
			LanguageServiceAccessor.getLanguageServers(document, LSPRenameHandler::isRenameProvider).thenAccept(languageServers -> {
				LSPRenameProcessor processor = new LSPRenameProcessor(LSPEclipseUtils.getDocument(workspaceFile), languageServers.get(0), 0);
				processor.setNewName("new");
				try {
					ProcessorBasedRefactoring processorBasedRefactoring = new ProcessorBasedRefactoring(processor);
					processorBasedRefactoring.checkAllConditions(new NullProgressMonitor());
					processorBasedRefactoring.createChange(new NullProgressMonitor()).perform(new NullProgressMonitor());
				} catch (CoreException e) {
					e.printStackTrace();
				}
			}).join();
			assertEquals("new", document.get());
			assertEquals("new", new String(Files.readAllBytes(externalFile.toPath())));
		} finally {
			Files.deleteIfExists(externalFile.toPath());
	}
	}

	private static WorkspaceEdit createSimpleMockRenameEdit(URI fileUri) {
		WorkspaceEdit res = new WorkspaceEdit();
		File f = new File(fileUri);
		res.setChanges(Collections.singletonMap(LSPEclipseUtils.toUri(f).toString(),
				Collections.singletonList(new TextEdit(new Range(new Position(0, 0), new Position(0, 3)), "new"))));
		return res;
	}
}
