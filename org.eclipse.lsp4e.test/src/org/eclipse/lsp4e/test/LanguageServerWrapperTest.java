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
package org.eclipse.lsp4e.test;

import static org.eclipse.lsp4e.test.utils.TestUtils.waitForAndAssertCondition;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.test.utils.AllCleanRule;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.ui.IEditorPart;
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

		// e.g. LanguageServerWrapper@69fe8c75 [serverId=org.eclipse.lsp4e.test.server-with-multi-root-support, initialPath=null, initialProject=P/LanguageServerWrapperTestProject11691664858710, isActive=true]
		assertTrue(wrapper.toString().matches("LanguageServerWrapper@[0-9a-f]+ \\[serverId=org.eclipse.lsp4e.test.server-with-multi-root-support, initialPath=null, initialProject=P\\/LanguageServerWrapperTestProject1[0-9]+, isActive=true\\]"));

		assertTrue(wrapper.isConnectedTo(testFile1.getLocationURI()));
		assertTrue(wrapper.isConnectedTo(testFile2.getLocationURI()));

		TestUtils.closeEditor(editor1, false);
		TestUtils.closeEditor(editor2, false);
	}

	/**
	 * Check if {@code isActive()} is correctly synchronized with  {@code stop()}
	 * @see https://github.com/eclipse/lsp4e/pull/688
	 */
	@Test
	public void testStopAndActive() throws CoreException, IOException, AssertionError, InterruptedException, ExecutionException {
		IFile testFile1 = TestUtils.createFile(project1, "shouldUseExtension.lsptWithMultiRoot", "");
		IEditorPart editor1 = TestUtils.openEditor(testFile1);
		@NonNull Collection<LanguageServerWrapper> wrappers = LanguageServiceAccessor.getLSWrappers(testFile1, request -> true);
		assertEquals(1, wrappers.size());
		LanguageServerWrapper wrapper = wrappers.iterator().next();
		CountDownLatch started = new CountDownLatch(1);
		try {
			var startStopJob = ForkJoinPool.commonPool().submit(() -> {
				started.countDown();
				while (!Thread.interrupted()) {
					wrapper.stop();
					try {
						wrapper.start();
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}
			});
			try {
				started.await();
				for (int i = 0; i < 10000000; i++) {
					// Should not throw
					wrapper.isActive();
					if (startStopJob.isDone()) {
						throw new AssertionError("Job should run indefinitely");
					}
				}
			} finally {
				startStopJob.cancel(true);
				if (!startStopJob.isCancelled()) {
					startStopJob.get();
				}
			}
		} finally {
			TestUtils.closeEditor(editor1, false);
		}
	}
}
