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
import org.eclipse.lsp4e.operations.completion.LSContentAssistProcessor;
import org.eclipse.lsp4e.test.MockLanguageSever;
import org.eclipse.lsp4e.test.TestUtils;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
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
	}

	@Test
	public void testPrefix() throws CoreException, NoSuchMethodException, SecurityException, IllegalAccessException,
			IllegalArgumentException, InvocationTargetException {
		List<CompletionItem> items = new ArrayList<>();
		items.add(createCompletionItem("FirstClass", CompletionItemKind.Class));
		items.add(createCompletionItem("SecondClass", CompletionItemKind.Class));
		MockLanguageSever.INSTANCE.setCompletionList(new CompletionList(false, items));

		String content = "First";
		ITextViewer viewer = TestUtils.openTextViewer(createUniqueTestFile(content));

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, content.length());
		assertEquals(1, proposals.length);
		// TODO compare items
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
		ITextViewer viewer = TestUtils.openTextViewer(createUniqueTestFile(content));

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, content.length());
		assertEquals(1, proposals.length);
		proposals[0].apply(viewer.getDocument());
		assertEquals(content + "1024M", viewer.getDocument().get());
	}

	@Test
	public void testTriggerCharsWithoutPreliminaryCompletion() throws Exception { // bug 508463
		Set<String> triggers = new HashSet<>();
		triggers.add("a");
		triggers.add("b");
		MockLanguageSever.INSTANCE.setCompletionTriggerChars(triggers);

		String content = "First";
		TestUtils.openTextViewer(createUniqueTestFile(content));

		assertArrayEquals(new char[] { 'a', 'b' },
				contentAssistProcessor.getCompletionProposalAutoActivationCharacters());
	}

	@Test
	public void testApplyCompletion() throws Exception {
		Range range = new Range(new Position(0, 0), new Position(0, 0));
		List<CompletionItem> items = Collections
				.singletonList(createCompletionItem("FirstClass", CompletionItemKind.Class, range));
		MockLanguageSever.INSTANCE.setCompletionList(new CompletionList(false, items));

		String content = "";
		ITextViewer viewer = TestUtils.openTextViewer(createUniqueTestFile(content));

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, content.length());
		proposals[0].apply(viewer.getDocument());
		assertEquals(true, viewer.getDocument().get().equals("FirstClass"));
	}

	@Test
	public void testApplyCompletionWithPrefix() throws Exception {
		Range range = new Range(new Position(0, 0), new Position(0, 5));
		List<CompletionItem> items = Collections
				.singletonList(createCompletionItem("FirstClass", CompletionItemKind.Class, range));
		MockLanguageSever.INSTANCE.setCompletionList(new CompletionList(false, items));

		String content = "First";
		ITextViewer viewer = TestUtils.openTextViewer(createUniqueTestFile(content));

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, content.length());
		proposals[0].apply(viewer.getDocument());
		assertEquals(true, viewer.getDocument().get().equals("FirstClass"));
	}

	@Test
	public void testApplyCompletionReplace() throws Exception {
		Range range = new Range(new Position(0, 0), new Position(0, 20));
		List<CompletionItem> items = Collections
				.singletonList(createCompletionItem("FirstClass", CompletionItemKind.Class, range));
		MockLanguageSever.INSTANCE.setCompletionList(new CompletionList(false, items));

		String content = "FirstNotMatchedLabel";
		ITextViewer viewer = TestUtils.openTextViewer(createUniqueTestFile(content));

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, 5);
		proposals[0].apply(viewer.getDocument());
		assertEquals(true, viewer.getDocument().get().equals("FirstClass"));
	}

	private IFile createUniqueTestFile(String content) throws CoreException {
		IFile testFile = project.getFile("test" + (System.currentTimeMillis()) + ".lspt");
		testFile.create(new ByteArrayInputStream(content.getBytes()), true, null);
		return testFile;
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
