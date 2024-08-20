/*******************************************************************************
 * Copyright (c) 2017 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.completion;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.operations.completion.LSContentAssistProcessor;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureInformation;
import org.junit.Before;
import org.junit.Test;

public class ContextInformationTest extends AbstractCompletionTest {

	@Override
	@Before
	public void setUp() {
		contentAssistProcessor = new LSContentAssistProcessor();
	}

	@Test
	public void testNoContextInformation() throws CoreException {
		MockLanguageServer.INSTANCE.setSignatureHelp(new SignatureHelp());

		IFile testFile = TestUtils.createUniqueTestFile(project, "");
		ITextViewer viewer = TestUtils.openTextViewer(testFile);

		IContextInformation[] infos = contentAssistProcessor.computeContextInformation(viewer, 0);
		assertEquals(0, infos.length);
	}

	@Test
	public void testContextInformationNoParameters() throws CoreException {
		final var signatureHelp = new SignatureHelp();
		final var information = new SignatureInformation("label", "documentation", Collections.emptyList());
		signatureHelp.setSignatures(List.of(information));
		MockLanguageServer.INSTANCE.setSignatureHelp(signatureHelp);

		IFile testFile = TestUtils.createUniqueTestFile(project, "method()");
		ITextViewer viewer = TestUtils.openTextViewer(testFile);

		IContextInformation[] infos = contentAssistProcessor.computeContextInformation(viewer, 0);
		assertEquals(1, infos.length);

		String expected = new StringBuilder(information.getLabel()).append('\n')
				.append(LSPEclipseUtils.getDocString(information.getDocumentation()))
				.toString();
		assertEquals(expected, infos[0].getInformationDisplayString());
	}

	@Test
	public void testTriggerChars() throws CoreException {
		final var triggers = new HashSet<String>();
		triggers.add("a");
		triggers.add("b");
		MockLanguageServer.INSTANCE.setContextInformationTriggerChars(triggers);

		final var content = "First";
		TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, content));

		assertArrayEquals(new char[] { 'a', 'b' },
				contentAssistProcessor.getContextInformationAutoActivationCharacters());
	}

	@Test
	public void testTriggerCharsNullList() throws CoreException {
		MockLanguageServer.INSTANCE.setContextInformationTriggerChars(null);

		TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, "First"));

		assertArrayEquals(new char[0], contentAssistProcessor.getContextInformationAutoActivationCharacters());
	}
}
