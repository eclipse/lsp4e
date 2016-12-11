/*******************************************************************************
 * Copyright (c) 2016 Rogue Wave Software Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.completion;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.lsp4e.operations.completion.LSCompletionProposal;
import org.eclipse.lsp4e.operations.completion.LSContentAssistProcessor;
import org.eclipse.lsp4e.test.MockLanguageSever;
import org.eclipse.lsp4e.test.TestUtils;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.swt.graphics.Point;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings("restriction")
public class CompletionTest {

	private IProject project;
	private LSContentAssistProcessor contentAssistProcessor;

	@Before
	public void setUp() throws CoreException {
		project = TestUtils.createProject("CompletionTest" + System.currentTimeMillis());
		contentAssistProcessor = new LSContentAssistProcessor();
	}

	@After
	public void tearDown() throws CoreException {
		project.delete(true, true, new NullProgressMonitor());
		MockLanguageSever.INSTANCE.shutdown();
	}

	@Test
	public void testNoPrefix() throws CoreException, NoSuchMethodException, SecurityException, IllegalAccessException,
			IllegalArgumentException, InvocationTargetException {
		List<CompletionItem> items = new ArrayList<>();
		items.add(createCompletionItem("FirstClass", CompletionItemKind.Class));
		MockLanguageSever.INSTANCE.setCompletionList(new CompletionList(false, items));

		IFile testFile = project.getFile("test01.lspt");
		testFile.create(new ByteArrayInputStream(new byte[0]), true, null);

		ITextViewer viewer = TestUtils.openTextViewer(testFile);

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, 0);
		assertEquals(items.size(), proposals.length);
		// TODO compare both structures
		LSCompletionProposal lsCompletionProposal = (LSCompletionProposal)proposals[0];
		lsCompletionProposal.apply(viewer, '\n', 0, 0);
		assertEquals(new Point("FirstClass".length(), 0), lsCompletionProposal.getSelection(viewer.getDocument()));
	}

	@Test
	public void testPrefix() throws CoreException, NoSuchMethodException, SecurityException, IllegalAccessException,
			IllegalArgumentException, InvocationTargetException {
		List<CompletionItem> items = new ArrayList<>();
		items.add(createCompletionItem("FirstClass", CompletionItemKind.Class));
		items.add(createCompletionItem("SecondClass", CompletionItemKind.Class));
		MockLanguageSever.INSTANCE.setCompletionList(new CompletionList(false, items));

		String content = "First";
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, content));

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, content.length());
		assertEquals(1, proposals.length);
		// TODO compare items
		LSCompletionProposal lsCompletionProposal = (LSCompletionProposal)proposals[0];
		lsCompletionProposal.apply(viewer, '\n', 0, 0);
		assertEquals(new Point("FirstClass".length(), 0), lsCompletionProposal.getSelection(viewer.getDocument()));
	}

	@Test
	public void testCompleteOnFileEnd() throws Exception { // bug 508842
		CompletionItem item = new CompletionItem();
		item.setLabel("1024M");
		item.setKind(CompletionItemKind.Value);
		item.setTextEdit(new TextEdit(new Range(new Position(2, 10), new Position(2, 10)), "1024M"));
		CompletionList completionList = new CompletionList(false, Collections.singletonList(item));
		MockLanguageSever.INSTANCE.setCompletionList(completionList);

		String content = "applications:\n" + "- name: hello\n" + "  memory: ";
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, content));

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, content.length());
		assertEquals(1, proposals.length);
		
		LSCompletionProposal lsCompletionProposal = (LSCompletionProposal)proposals[0];
		lsCompletionProposal.apply(viewer, '\n', 0, 0);
		assertEquals(content + "1024M", viewer.getDocument().get());
		assertEquals(new Point(viewer.getDocument().getLength(), 0), lsCompletionProposal.getSelection(viewer.getDocument()));
	}

	@Test
	public void testTriggerCharsWithoutPreliminaryCompletion() throws Exception { // bug 508463
		Set<String> triggers = new HashSet<>();
		triggers.add("a");
		triggers.add("b");
		MockLanguageSever.INSTANCE.setCompletionTriggerChars(triggers);

		String content = "First";
		TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, content));

		assertArrayEquals(new char[] { 'a', 'b' },
				contentAssistProcessor.getCompletionProposalAutoActivationCharacters());
	}

	@Test
	public void testApplyCompletionWithPrefix() throws Exception {
		Range range = new Range(new Position(0, 0), new Position(0, 5));
		List<CompletionItem> items = Collections
				.singletonList(createCompletionItem("FirstClass", CompletionItemKind.Class, range));
		MockLanguageSever.INSTANCE.setCompletionList(new CompletionList(false, items));

		String content = "First";
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, content));

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, content.length());
		LSCompletionProposal lsCompletionProposal = (LSCompletionProposal)proposals[0];
		lsCompletionProposal.apply(viewer, '\n', 0, 0);
		assertEquals(true, viewer.getDocument().get().equals("FirstClass"));
		assertEquals(new Point(viewer.getDocument().getLength(), 0), lsCompletionProposal.getSelection(viewer.getDocument()));
	}

	@Test
	public void testApplyCompletionReplace() throws Exception {
		Range range = new Range(new Position(0, 0), new Position(0, 20));
		List<CompletionItem> items = Collections
				.singletonList(createCompletionItem("FirstClass", CompletionItemKind.Class, range));
		MockLanguageSever.INSTANCE.setCompletionList(new CompletionList(false, items));

		String content = "FirstNotMatchedLabel";
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project,content));

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, 5);
		LSCompletionProposal lsCompletionProposal = (LSCompletionProposal)proposals[0];
		lsCompletionProposal.apply(viewer, '\n', 0, 0);
		assertEquals("FirstClass", viewer.getDocument().get());
		assertEquals(new Point("FirstClass".length(), 0), lsCompletionProposal.getSelection(viewer.getDocument()));
	}
	
	@Test
	public void testCompletionReplace() throws Exception {
		IFile file = TestUtils.createUniqueTestFile(project, "line1\nlineInsertHere");
		ITextViewer viewer = TestUtils.openTextViewer(file);
		MockLanguageSever.INSTANCE.setCompletionList(new CompletionList(false, Collections.singletonList(
			createCompletionItem("Inserted", CompletionItemKind.Text, new Range(new Position(1, 4), new Position(1, 4 + "InsertHere".length())))
		)));
		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, viewer.getDocument().getLength() - "InsertHere".length());
		LSCompletionProposal lsCompletionProposal = (LSCompletionProposal)proposals[0];
		lsCompletionProposal.apply(viewer, '\n', 0, 0);
		assertEquals("line1\nlineInserted", viewer.getDocument().get());
		assertEquals(new Point(viewer.getDocument().getLength(), 0), lsCompletionProposal.getSelection(viewer.getDocument()));
	}

	private CompletionItem createCompletionItem(String label, CompletionItemKind kind) {
		return createCompletionItem(label, kind, new Range(new Position(0, 0), new Position(0, label.length())));
	}

	private CompletionItem createCompletionItem(String label, CompletionItemKind kind, Range range) {
		CompletionItem item = new CompletionItem();
		item.setLabel(label);
		item.setKind(kind);
		item.setTextEdit(new TextEdit(range, label));
		return item;
	}

}
