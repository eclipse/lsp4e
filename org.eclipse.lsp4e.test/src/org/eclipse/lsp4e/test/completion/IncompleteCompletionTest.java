/*******************************************************************************
 * Copyright (c) 2016, 2019 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *  Max Bureck (Fraunhofer FOKUS) - Adjustments to variable replacement test
 *******************************************************************************/
package org.eclipse.lsp4e.test.completion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.BoldStylerProvider;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.tests.util.DisplayHelper;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServersRegistry;
import org.eclipse.lsp4e.LanguageServersRegistry.LanguageServerDefinition;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.lsp4e.operations.completion.LSIncompleteCompletionProposal;
import org.eclipse.lsp4e.test.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.ITextEditor;
import org.junit.Test;

public class IncompleteCompletionTest extends AbstractCompletionTest {
	/*
	 * This tests the not-so-official way to associate a LS to a file programmatically, and then to retrieve the LS
	 * for the file independently of the content-types. Although doing it programatically isn't recommended, consuming
	 * file-specific LS already associated is something we want to support.
	 */
	@Test
	public void testAssistForUnknownButConnectedType() throws CoreException, InvocationTargetException, IOException, InterruptedException {
		List<CompletionItem> items = new ArrayList<>();
		items.add(createCompletionItem("FirstClass", CompletionItemKind.Class));
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(true, items));

		IFile testFile = TestUtils.createUniqueTestFileOfUnknownType(project, "");
		ITextViewer viewer = TestUtils.openTextViewer(testFile);

		LanguageServerDefinition serverDefinition = LanguageServersRegistry.getInstance().getDefinition("org.eclipse.lsp4e.test.server");
		assertNotNull(serverDefinition);
		LanguageServerWrapper lsWrapperForConnection = LanguageServiceAccessor
				.getLSWrapperForConnection(testFile.getProject(), serverDefinition);
		IPath fileLocation = testFile.getLocation();
		// force connection (that's what LSP4E should be designed to prevent 3rd party from having to use it).
		lsWrapperForConnection.connect(testFile, null);

		new DisplayHelper() {
			@Override
			protected boolean condition() {
				return lsWrapperForConnection.isConnectedTo(fileLocation);
			}
		}.waitForCondition(Display.getCurrent(), 3000);

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, 0);
		assertEquals(items.size(), proposals.length);
		// TODO compare both structures
		LSIncompleteCompletionProposal LSIncompleteCompletionProposal = (LSIncompleteCompletionProposal) proposals[0];
		LSIncompleteCompletionProposal.apply(viewer.getDocument());
		assertEquals(new Point("FirstClass".length(), 0),
				LSIncompleteCompletionProposal.getSelection(viewer.getDocument()));
	}

	@Test
	public void testNoPrefix() throws CoreException, InvocationTargetException {
		List<CompletionItem> items = new ArrayList<>();
		items.add(createCompletionItem("FirstClass", CompletionItemKind.Class));
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(true, items));

		IFile testFile = TestUtils.createUniqueTestFile(project, "");
		ITextViewer viewer = TestUtils.openTextViewer(testFile);

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, 0);
		assertEquals(items.size(), proposals.length);
		// TODO compare both structures
		LSIncompleteCompletionProposal LSIncompleteCompletionProposal = (LSIncompleteCompletionProposal) proposals[0];
		LSIncompleteCompletionProposal.apply(viewer.getDocument());
		assertEquals(new Point("FirstClass".length(), 0),
				LSIncompleteCompletionProposal.getSelection(viewer.getDocument()));
	}

	@Test
	public void testDeprecatedCompletion() throws Exception {
		BoldStylerProvider boldStyleProvider = null;
		try {
			List<CompletionItem> items = new ArrayList<>();
			CompletionItem completionItem = createCompletionItem("FirstClassDeprecated", CompletionItemKind.Class);
			completionItem.setDeprecated(true);
			items.add(completionItem);
			MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(true, items));

			String content = "First";
			ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, content));

			ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer,
					content.length());
			assertEquals(1, proposals.length);
			LSIncompleteCompletionProposal proposal = (LSIncompleteCompletionProposal) proposals[0];

			StyledString simpleStyledStr = proposal.getStyledDisplayString();
			assertEquals("FirstClassDeprecated", simpleStyledStr.getString());
			assertEquals(1, simpleStyledStr.getStyleRanges().length);
			StyleRange styleRange = simpleStyledStr.getStyleRanges()[0];
			assertTrue(styleRange.strikeout);

			boldStyleProvider = new BoldStylerProvider(
					Display.getDefault().getActiveShell().getFont());
			StyledString styledStr = proposal.getStyledDisplayString(viewer.getDocument(), 4, boldStyleProvider);
			assertTrue(styledStr.getStyleRanges().length > 1);
			for (StyleRange sr : styledStr.getStyleRanges()) {
				assertTrue(sr.strikeout);
			}
		} finally {
			if (boldStyleProvider != null) {
				boldStyleProvider.dispose();
			}
		}
	}

	@Test
	public void testPrefix() throws CoreException, InvocationTargetException {
		List<CompletionItem> items = new ArrayList<>();
		items.add(createCompletionItem("FirstClass", CompletionItemKind.Class));
		items.add(createCompletionItem("SecondClass", CompletionItemKind.Class));
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(true, items));

		String content = "First";
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, content));

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, content.length());
		assertEquals(2, proposals.length);
		// TODO compare items
		LSIncompleteCompletionProposal LSIncompleteCompletionProposal = (LSIncompleteCompletionProposal) proposals[0];
		LSIncompleteCompletionProposal.apply(viewer.getDocument());
		assertEquals(new Point("FirstClass".length(), 0),
				LSIncompleteCompletionProposal.getSelection(viewer.getDocument()));
	}

	@Test
	public void testPrefixCaseSensitivity() throws CoreException, InvocationTargetException {
		List<CompletionItem> items = new ArrayList<>();
		items.add(createCompletionItem("FirstClass", CompletionItemKind.Class));
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(true, items));

		String content = "FIRST";
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, content));

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, content.length());
		assertEquals(1, proposals.length);
		// TODO compare items
		LSIncompleteCompletionProposal LSIncompleteCompletionProposal = (LSIncompleteCompletionProposal) proposals[0];
		LSIncompleteCompletionProposal.apply(viewer.getDocument());
		assertEquals(new Point("FirstClass".length(), 0),
				LSIncompleteCompletionProposal.getSelection(viewer.getDocument()));
	}

	@Test
	public void testCompleteOnFileEnd() throws CoreException, InvocationTargetException { // bug 508842
		CompletionItem item = new CompletionItem();
		item.setLabel("1024M");
		item.setKind(CompletionItemKind.Value);
		item.setTextEdit(new TextEdit(new Range(new Position(2, 10), new Position(2, 10)), "1024M"));
		CompletionList completionList = new CompletionList(true, Collections.singletonList(item));
		MockLanguageServer.INSTANCE.setCompletionList(completionList);

		String content = "applications:\n" + "- name: hello\n" + "  memory: ";
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, content));

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, content.length());
		assertEquals(1, proposals.length);

		LSIncompleteCompletionProposal LSIncompleteCompletionProposal = (LSIncompleteCompletionProposal) proposals[0];
		LSIncompleteCompletionProposal.apply(viewer.getDocument());
		assertEquals(content + "1024M", viewer.getDocument().get());
		assertEquals(new Point(viewer.getDocument().getLength(), 0),
				LSIncompleteCompletionProposal.getSelection(viewer.getDocument()));
	}

	@Test
	public void testCompletionWithAdditionalEdits() throws CoreException, InvocationTargetException {
		List<CompletionItem> items = new ArrayList<>();
		CompletionItem item = new CompletionItem("additionaEditsCompletion");
		item.setKind(CompletionItemKind.Function);
		item.setInsertText("MainInsertText");

		List<TextEdit> additionalTextEdits = new ArrayList<>();

		TextEdit additionaEdit1 = new TextEdit(new Range(new Position(0, 6), new Position(0, 6)), "addOnText1");
		TextEdit additionaEdit2 = new TextEdit(new Range(new Position(0, 12), new Position(0, 12)), "addOnText2");
		additionalTextEdits.add(additionaEdit1);
		additionalTextEdits.add(additionaEdit2);

		item.setAdditionalTextEdits(additionalTextEdits);
		items.add(item);
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(true, items));

		String content = "this <> is <> the main <> content of the file";
		IFile testFile = TestUtils.createUniqueTestFile(project, content);
		ITextViewer viewer = TestUtils.openTextViewer(testFile);

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, 24);
		assertEquals(items.size(), proposals.length);
		// TODO compare both structures
		LSIncompleteCompletionProposal LSIncompleteCompletionProposal = (LSIncompleteCompletionProposal) proposals[0];
		LSIncompleteCompletionProposal.apply(viewer.getDocument());

		String newContent = viewer.getDocument().get();
		assertEquals("this <addOnText1> is <addOnText2> the main <MainInsertText> content of the file", newContent);
	}

	@Test
	public void testSnippetCompletionWithAdditionalEdits()
			throws PartInitException, InvocationTargetException, CoreException {
		CompletionItem item = new CompletionItem("snippet item");
		item.setInsertText("$1 and ${2:foo}");
		item.setKind(CompletionItemKind.Class);
		item.setInsertTextFormat(InsertTextFormat.Snippet);
		List<TextEdit> additionalTextEdits = new ArrayList<>();

		TextEdit additionaEdit1 = new TextEdit(new Range(new Position(0, 6), new Position(0, 6)), "addOnText1");
		TextEdit additionaEdit2 = new TextEdit(new Range(new Position(0, 12), new Position(0, 12)), "addOnText2");
		additionalTextEdits.add(additionaEdit1);
		additionalTextEdits.add(additionaEdit2);

		item.setAdditionalTextEdits(additionalTextEdits);

		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(true, Collections.singletonList(item)));

		String content = "this <> is <> the main <> content of the file";
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, content));

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, 24);
		assertEquals(1, proposals.length);
		((LSIncompleteCompletionProposal) proposals[0]).apply(viewer.getDocument());

		String newContent = viewer.getDocument().get();
		assertEquals("this <addOnText1> is <addOnText2> the main < and foo> content of the file", newContent);
		// TODO check link edit groups
	}

	@Test
	public void testApplyCompletionWithPrefix() throws CoreException, InvocationTargetException {
		Range range = new Range(new Position(0, 0), new Position(0, 5));
		List<CompletionItem> items = Collections
				.singletonList(createCompletionItem("FirstClass", CompletionItemKind.Class, range));
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(true, items));

		String content = "First";
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, content));

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, content.length());
		LSIncompleteCompletionProposal LSIncompleteCompletionProposal = (LSIncompleteCompletionProposal) proposals[0];
		LSIncompleteCompletionProposal.apply(viewer.getDocument());
		assertEquals(true, viewer.getDocument().get().equals("FirstClass"));
		assertEquals(new Point(viewer.getDocument().getLength(), 0),
				LSIncompleteCompletionProposal.getSelection(viewer.getDocument()));
	}

	@Test
	public void testApplyCompletionReplace() throws CoreException, InvocationTargetException {
		Range range = new Range(new Position(0, 0), new Position(0, 20));
		List<CompletionItem> items = Collections
				.singletonList(createCompletionItem("FirstClass", CompletionItemKind.Class, range));
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(true, items));

		String content = "FirstNotMatchedLabel";
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, content));
		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, 5);
		LSIncompleteCompletionProposal LSIncompleteCompletionProposal = (LSIncompleteCompletionProposal) proposals[0];
		LSIncompleteCompletionProposal.apply(viewer.getDocument());
		assertEquals("FirstClass", viewer.getDocument().get());
		assertEquals(new Point("FirstClass".length(), 0),
				LSIncompleteCompletionProposal.getSelection(viewer.getDocument()));
	}

	@Test
	public void testApplyCompletionReplaceAndTypingWithTextEdit() throws CoreException, InvocationTargetException, BadLocationException {
		Range range = new Range(new Position(0, 0), new Position(0, 22));
		List<CompletionItem> items = Collections
				.singletonList(createCompletionItem("FirstClass", CompletionItemKind.Class, range));
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(true, items));

		String content = "FirstNotMatchedLabel";
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project,content));

		int invokeOffset = 5;
		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, invokeOffset);
		LSIncompleteCompletionProposal LSIncompleteCompletionProposal = (LSIncompleteCompletionProposal) proposals[0];

		// simulate additional typing (to filter) after invoking completion
		viewer.getDocument().replace(5, 0, "No");

		LSIncompleteCompletionProposal.apply(viewer.getDocument());
		assertEquals("FirstClass", viewer.getDocument().get());
		assertEquals(new Point("FirstClass".length(), 0),
				LSIncompleteCompletionProposal.getSelection(viewer.getDocument()));
	}

	@Test
	public void testApplyCompletionReplaceAndTyping()
			throws CoreException, InvocationTargetException, BadLocationException {
		CompletionItem item = new CompletionItem("strncasecmp");
		item.setKind(CompletionItemKind.Function);
		item.setInsertText("strncasecmp()");

		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(true, Collections.singletonList(item)));

		String content = "str";
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, content));

		int invokeOffset = content.length();
		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, invokeOffset);
		assertEquals(1, proposals.length);
		LSIncompleteCompletionProposal LSIncompleteCompletionProposal = (LSIncompleteCompletionProposal) proposals[0];

		// simulate additional typing (to filter) after invoking completion
		viewer.getDocument().replace(content.length(), 0, "nc");

		LSIncompleteCompletionProposal.apply(viewer.getDocument());
		assertEquals(item.getInsertText(), viewer.getDocument().get());
		assertEquals(new Point(item.getInsertText().length(), 0),
				LSIncompleteCompletionProposal.getSelection(viewer.getDocument()));
	}

	@Test
	public void testCompletionReplace() throws CoreException, InvocationTargetException {
		IFile file = TestUtils.createUniqueTestFile(project, "line1\nlineInsertHere");
		ITextViewer viewer = TestUtils.openTextViewer(file);
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(true, Collections.singletonList(
			createCompletionItem("Inserted", CompletionItemKind.Text, new Range(new Position(1, 4), new Position(1, 4 + "InsertHere".length())))
		)));

		int invokeOffset = viewer.getDocument().getLength() - "InsertHere".length();
		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, invokeOffset);
		LSIncompleteCompletionProposal LSIncompleteCompletionProposal = (LSIncompleteCompletionProposal) proposals[0];
		LSIncompleteCompletionProposal.apply(viewer.getDocument());
		assertEquals("line1\nlineInserted", viewer.getDocument().get());
		assertEquals(new Point(viewer.getDocument().getLength(), 0),
				LSIncompleteCompletionProposal.getSelection(viewer.getDocument()));
	}

	@Test
	public void testItemOrdering() throws Exception {
		Range range = new Range(new Position(0, 0), new Position(0, 1));
		List<CompletionItem> items = Arrays.asList(new CompletionItem[] {
			createCompletionItem("AA", CompletionItemKind.Class, range),
			createCompletionItem("AB", CompletionItemKind.Class, range),
			createCompletionItem("BA", CompletionItemKind.Class, range),
			createCompletionItem("BB", CompletionItemKind.Class, range),
			createCompletionItem("CB", CompletionItemKind.Class, range),
			createCompletionItem("CC", CompletionItemKind.Class, range),
		});
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(true, items));

		String content = "B";
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project,content));

		int invokeOffset = 1;
		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, invokeOffset);
		assertEquals(6, proposals.length);
		assertEquals("AA", proposals[0].getDisplayString());
		assertEquals("AB", proposals[1].getDisplayString());
		assertEquals("BA", proposals[2].getDisplayString());
		assertEquals("BB", proposals[3].getDisplayString());

		((LSIncompleteCompletionProposal) proposals[0]).apply(viewer.getDocument());
		assertEquals("AA", viewer.getDocument().get());
	}

	@Test
	public void testBasicSnippet() throws PartInitException, InvocationTargetException, CoreException {
		CompletionItem completionItem = createCompletionItem("$1 and ${2:foo}", CompletionItemKind.Class, new Range(new Position(0, 0), new Position(0, 1)));
		completionItem.setInsertTextFormat(InsertTextFormat.Snippet);
		MockLanguageServer.INSTANCE
				.setCompletionList(new CompletionList(true, Collections.singletonList(completionItem)));
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project,""));
		int invokeOffset = 0;
		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, invokeOffset);
		assertEquals(1, proposals.length);
		((LSIncompleteCompletionProposal) proposals[0]).apply(viewer.getDocument());
		assertEquals(" and foo", viewer.getDocument().get());
		// TODO check link edit groups
	}

	@Test
	public void testDuplicateVariable() throws PartInitException, InvocationTargetException, CoreException {
		CompletionItem completionItem = createCompletionItem("${1:foo} and ${1:foo}", CompletionItemKind.Class, new Range(new Position(0, 0), new Position(0, 1)));
		completionItem.setInsertTextFormat(InsertTextFormat.Snippet);
		MockLanguageServer.INSTANCE
				.setCompletionList(new CompletionList(true, Collections.singletonList(completionItem)));
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project,""));
		int invokeOffset = 0;
		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, invokeOffset);
		assertEquals(1, proposals.length);
		((LSIncompleteCompletionProposal) proposals[0]).apply(viewer.getDocument());
		assertEquals("foo and foo", viewer.getDocument().get());
		// TODO check link edit groups
	}

	@Test
	public void testVariableReplacement() throws PartInitException, InvocationTargetException, CoreException {
		CompletionItem completionItem = createCompletionItem(
				"${1:$TM_FILENAME_BASE} ${2:$TM_FILENAME} ${3:$TM_FILEPATH} ${4:$TM_DIRECTORY} ${5:$TM_LINE_INDEX} ${6:$TM_LINE_NUMBER} ${7:$TM_CURRENT_LINE} ${8:$TM_SELECTED_TEXT}",
				CompletionItemKind.Class, new Range(new Position(0, 0), new Position(0, 1)));
		completionItem.setInsertTextFormat(InsertTextFormat.Snippet);
		MockLanguageServer.INSTANCE
				.setCompletionList(new CompletionList(true, Collections.singletonList(completionItem)));
		String content = "line1\nline2\nline3";
		IFile testFile = TestUtils.createUniqueTestFile(project, content);
		ITextViewer viewer = TestUtils.openTextViewer(testFile);
		int invokeOffset = 0;
		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, invokeOffset);
		assertEquals(1, proposals.length);
		((LSIncompleteCompletionProposal) proposals[0]).apply(viewer.getDocument());

		int lineIndex = completionItem.getTextEdit().getRange().getStart().getLine();
		String fileNameBase = testFile.getFullPath().removeFileExtension().lastSegment();
		String filePath = testFile.getRawLocation().toOSString();
		String fileDir = project.getLocation().toOSString();
		assertEquals(String.format("%s %s %s %s %d %d %s %s%s", fileNameBase, testFile.getName(), filePath, fileDir,
				lineIndex, lineIndex + 1, "line1", "l", content.substring(1)), viewer.getDocument().get());
		// TODO check link edit groups
	}

	@Test
	public void testMultipleLS() throws Exception {
		List<CompletionItem> items = new ArrayList<>();
		items.add(createCompletionItem("FirstClass", CompletionItemKind.Class));
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(true, items));

		IFile testFile = TestUtils.createUniqueTestFileMultiLS(project, "");
		ITextViewer viewer = TestUtils.openTextViewer(testFile);

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, 0);
		assertEquals(2 * items.size(), proposals.length);
	}

	@Test
	public void testCompletionExternalFile() throws Exception {
		List<CompletionItem> items = new ArrayList<>();
		items.add(createCompletionItem("FirstClassExternal", CompletionItemKind.Class));
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(true, items));

		File file = File.createTempFile("testCompletionExternalFile", ".lspt");
		try {
			ITextEditor editor = (ITextEditor) IDE.openEditorOnFileStore(
					PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(), EFS.getStore(file.toURI()));
			ITextViewer viewer = TestUtils.getTextViewer(editor);
			ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, 0);
			assertEquals(1, proposals.length);
			proposals[0].apply(viewer.getDocument());
			assertEquals("FirstClassExternal", viewer.getDocument().get());
		} finally {
			file.delete();
		}
	}

	@Test
	public void testAdditionalInformation() throws Exception {
		IDocument document = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, "")).getDocument();
		LSPDocumentInfo info = LanguageServiceAccessor
				.getLSPDocumentInfosFor(document, capabilities -> capabilities.getCompletionProvider() != null
						|| capabilities.getSignatureHelpProvider() != null)
				.get(0);
		LSIncompleteCompletionProposal completionProposal = new LSIncompleteCompletionProposal(document, 0,
				new CompletionItem("blah"), info.getLanguageClient());
		completionProposal.getAdditionalProposalInfo(new NullProgressMonitor()); // check no expection is sent
	}
}
