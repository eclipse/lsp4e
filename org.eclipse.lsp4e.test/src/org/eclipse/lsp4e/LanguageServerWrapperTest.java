/*******************************************************************************
 * Copyright (c) 2019 SAP SE and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Markus Ofterdinger (SAP SE) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e;

import static org.eclipse.lsp4e.test.TestUtils.waitForAndAssertCondition;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.lsp4e.test.AllCleanRule;
import org.eclipse.lsp4e.test.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServerMultiRootFolders;
import org.eclipse.lsp4j.jsonrpc.json.StreamMessageProducer;
import org.eclipse.ui.IEditorPart;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class LanguageServerWrapperTest {

	private IProject project1;
	private IProject project2;

	@Rule
	public AllCleanRule clear = new AllCleanRule();

	@Before
	public void setUp() throws CoreException {
		project1 = TestUtils.createProject("LanguageServerWrapperTestProject1" + System.currentTimeMillis());
		project2 = TestUtils.createProject("LanguageServerWrapperTestProject2" + System.currentTimeMillis());
	}

	@Test
	public void testConnect() throws Exception {
		IFile testFile1 = TestUtils.createFile(project1, "shouldUseExtension.lsptWithMultiRoot", "");
		IFile testFile2 = TestUtils.createFile(project2, "shouldUseExtension.lsptWithMultiRoot", "");

		IEditorPart editor1 = TestUtils.openEditor(testFile1);
		IEditorPart editor2 = TestUtils.openEditor(testFile2);

		@NonNull Collection<LanguageServerWrapper> wrappers = LanguageServiceAccessor.getLSWrappers(testFile1, request -> true);

		assertEquals(1, wrappers.size());

		LanguageServerWrapper wrapper = wrappers.iterator().next();
		waitForAndAssertCondition(2_000, () -> wrapper.isActive());

		assertTrue(wrapper.isConnectedTo(testFile1.getLocationURI()));
		assertTrue(wrapper.isConnectedTo(testFile2.getLocationURI()));

		TestUtils.closeEditor(editor1, false);
		TestUtils.closeEditor(editor2, false);
	}
	
	@Test
	public void doNotStopBusyDispatchers() throws Exception {
		final Logger LOG = Logger.getLogger(StreamMessageProducer.class.getName());
		List<String> logMessages = Collections.synchronizedList(new ArrayList<>());
		LOG.addHandler(new Handler() {
			
			@Override
			public void publish(LogRecord record) {
				logMessages.add(record.getMessage());
			}
			
			@Override
			public void flush() {
				
			}
			
			@Override
			public void close() throws SecurityException {
			}
		});
		IFile testFile1 = TestUtils.createFile(project1, "shouldUseExtension.lsptWithMultiRoot", "");
		IEditorPart editor1 = TestUtils.openEditor(testFile1);
		
		try {
			@NonNull Collection<LanguageServerWrapper> wrappers = LanguageServiceAccessor.getLSWrappers(testFile1, request -> true);
			LanguageServerWrapper wrapper = wrappers.iterator().next();
			assertEquals(1, wrappers.size());
			waitForAndAssertCondition(2_000, () -> MockLanguageServerMultiRootFolders.INSTANCE.isRunning());
			assertTrue(wrapper.isConnectedTo(testFile1.getLocationURI()));
			logMessages.clear();
			wrapper.stopDispatcher();
			waitForAndAssertCondition(2_000, () -> !MockLanguageServerMultiRootFolders.INSTANCE.isRunning());
			Assert.assertEquals(Collections.emptyList(), logMessages);
		} finally {
			TestUtils.closeEditor(editor1, false);
		}
		
	}
}
