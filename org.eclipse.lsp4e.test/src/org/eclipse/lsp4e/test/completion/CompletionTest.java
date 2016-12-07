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

public class CompletionTest {

	private IProject project;

	@Before
	public void setUp() throws CoreException {
		project = TestUtils.createProject("CompletionTest" + System.currentTimeMillis());
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
		CompletionList completionList = new CompletionList(false, items);
		MockLanguageSever.INSTANCE.setCompletionList(completionList);

		IFile testFile = project.getFile("test01.lspt");
		testFile.create(new ByteArrayInputStream(new byte[0]), true, null);

		ITextViewer viewer = TestUtils.createTextViewer(testFile);

		LSContentAssistProcessor contentAssistProcessor = new LSContentAssistProcessor();
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

		IFile testFile = project.getFile("test02.lspt");
		String content = "First";
		testFile.create(new ByteArrayInputStream(content.getBytes()), true, null);

		ITextViewer viewer = TestUtils.createTextViewer(testFile);

		LSContentAssistProcessor contentAssistProcessor = new LSContentAssistProcessor();
		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, content.length());
		assertEquals(1, proposals.length);
		// TODO compare items
	}
	
	@Test
	public void testCompleteOnFileEnd() throws Exception { // bug 508842
		List<CompletionItem> items = new ArrayList<>();
		CompletionItem item = new CompletionItem();
		item.setLabel("1024M");
		item.setKind(CompletionItemKind.Value);
		item.setTextEdit(new TextEdit(new Range(new Position(2, 10), new Position(2, 10)), "1024M"));
		CompletionList completionList = new CompletionList(false, Collections.singletonList(item));
		MockLanguageSever.INSTANCE.setCompletionList(completionList);	

		IFile testFile = project.getFile("test03.lspt");
		String content = "applications:\n" + 
				"- name: hello\n" + 
				"  memory: ";
		testFile.create(new ByteArrayInputStream(content.getBytes()), true, null);

		ITextViewer viewer = TestUtils.createTextViewer(testFile);

		LSContentAssistProcessor contentAssistProcessor = new LSContentAssistProcessor();
		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, content.length());
		assertEquals(1, proposals.length);
		proposals[0].apply(viewer.getDocument());
		assertEquals(content + "1024M", viewer.getDocument().get());
	}

	private CompletionItem createCompletionItem(String label, CompletionItemKind kind) {
		CompletionItem item = new CompletionItem();
		item.setLabel(label);
		item.setKind(kind);
		item.setTextEdit(new TextEdit(new Range(new Position(0, 0), new Position(0, label.length())), label));
		return item;
	}
	
	@Test
	public void testTriggerCharsWithoutPreliminaryCompletion() throws Exception  { // bug 508463
		Set<String> triggers = new HashSet<>();
		triggers.add("a");
		triggers.add("b");
		MockLanguageSever.INSTANCE.setCompletionTriggerChars(triggers);
		IFile testFile = project.getFile("test02.lspt");
		String content = "First";
		testFile.create(new ByteArrayInputStream(content.getBytes()), true, null);

		ITextViewer viewer = TestUtils.createTextViewer(testFile);

		LSContentAssistProcessor contentAssistProcessor = new LSContentAssistProcessor();
		assertArrayEquals(new char[] {'a', 'b'}, contentAssistProcessor.getCompletionProposalAutoActivationCharacters());
	}

}
