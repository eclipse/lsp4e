/*******************************************************************************
 * Copyright (c) 2017 Rogue Wave Software Inc. and others.
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

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.lsp4e.operations.completion.LSContentAssistProcessor;
import org.eclipse.lsp4e.test.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageSever;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureInformation;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ContextInformationTest {

	private IProject project;
	private LSContentAssistProcessor contentAssistProcessor;

	@Before
	public void setUp() throws CoreException {
		project = TestUtils.createProject("ContextInformationTest" + System.currentTimeMillis());
		contentAssistProcessor = new LSContentAssistProcessor();
	}

	@After
	public void tearDown() throws CoreException {
		project.delete(true, true, new NullProgressMonitor());
		MockLanguageSever.INSTANCE.shutdown();
	}

	@Test
	public void testNoContextInformation() throws CoreException, InvocationTargetException {
		MockLanguageSever.INSTANCE.setSignatureHelp(new SignatureHelp());

		IFile testFile = TestUtils.createUniqueTestFile(project, "");
		ITextViewer viewer = TestUtils.openTextViewer(testFile);

		IContextInformation[] infos = contentAssistProcessor.computeContextInformation(viewer, 0);
		assertEquals(0, infos.length);
	}

	@Test
	public void testContextInformationNoParameters() throws CoreException, InvocationTargetException {
		SignatureHelp signatureHelp = new SignatureHelp();
		SignatureInformation information = new SignatureInformation("label", "documentation", Collections.emptyList());
		signatureHelp.setSignatures(Collections.singletonList(information));
		MockLanguageSever.INSTANCE.setSignatureHelp(signatureHelp);

		IFile testFile = TestUtils.createUniqueTestFile(project, "method()");
		ITextViewer viewer = TestUtils.openTextViewer(testFile);

		IContextInformation[] infos = contentAssistProcessor.computeContextInformation(viewer, 0);
		assertEquals(1, infos.length);

		String expected = new StringBuilder(information.getLabel()).append('\n').append(information.getDocumentation())
				.toString();
		assertEquals(expected, infos[0].getInformationDisplayString());
	}

	@Test
	public void testTriggerChars() throws CoreException, InvocationTargetException {
		Set<String> triggers = new HashSet<>();
		triggers.add("a");
		triggers.add("b");
		MockLanguageSever.INSTANCE.setContextInformationTriggerChars(triggers);

		String content = "First";
		TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, content));

		assertArrayEquals(new char[] { 'a', 'b' },
				contentAssistProcessor.getContextInformationAutoActivationCharacters());
	}

	@Test
	public void testTriggerCharsNullList() throws CoreException, InvocationTargetException {
		MockLanguageSever.INSTANCE.setContextInformationTriggerChars(null);

		TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, "First"));

		assertArrayEquals(null, contentAssistProcessor.getContextInformationAutoActivationCharacters());
	}

}
