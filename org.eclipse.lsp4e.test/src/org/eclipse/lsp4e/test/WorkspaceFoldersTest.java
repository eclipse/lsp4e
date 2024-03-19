/*******************************************************************************
 * Copyright (c) 2022 Cocotec Ltd and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Ahmed Hussain (Cocotec Ltd) - initial implementation
 *
 *******************************************************************************/
package org.eclipse.lsp4e.test;

import static org.eclipse.lsp4e.test.utils.TestUtils.waitForAndAssertCondition;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.lsp4e.ConnectDocumentToLanguageServerSetupParticipant;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.test.utils.AllCleanRule;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.test.utils.TestUtils.JobSynchronizer;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4e.tests.mock.MockWorkspaceService;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceFoldersOptions;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class WorkspaceFoldersTest implements Supplier<ServerCapabilities> {

	@Rule public AllCleanRule clear = new AllCleanRule(this);
	private IProject project;

	@Before
	public void setUp() throws CoreException {
		MockLanguageServer.INSTANCE.getWorkspaceService().getWorkspaceFoldersEvents().clear();
		project = TestUtils.createProject("WorkspaceFoldersTest" + System.currentTimeMillis());
	}

	@Test
	public void testRecycleLSAfterInitialProjectGotDeletedIfWorkspaceFolders() throws Exception {
		IFile testFile1 = TestUtils.createUniqueTestFile(project, "");

		TestUtils.openEditor(testFile1);
		Collection<LanguageServerWrapper> wrappers = LanguageServiceAccessor.getLSWrappers(testFile1, c -> true);
		waitForAndAssertCondition(5_000, () -> MockLanguageServer.INSTANCE.isRunning());

		LanguageServerWrapper wrapper1 = wrappers.iterator().next();
		assertTrue(wrapper1.isActive());

		UI.getActivePage().closeAllEditors(false);
		waitForAndAssertCondition(5_000, () -> !MockLanguageServer.INSTANCE.isRunning());

		project.delete(true, true, new NullProgressMonitor());

		project = TestUtils.createProject("LanguageServiceAccessorTest2" + System.currentTimeMillis());
		IFile testFile2 = TestUtils.createUniqueTestFile(project, "");

		TestUtils.openEditor(testFile2);
		wrappers = LanguageServiceAccessor.getLSWrappers(testFile2, c -> true);
		waitForAndAssertCondition(5_000, () -> MockLanguageServer.INSTANCE.isRunning());

		LanguageServerWrapper wrapper2 = wrappers.iterator().next();
		assertTrue(wrapper2.isActive());

		// See corresponding LanguageServiceAccessorTest.testCreateNewLSAfterInitialProjectGotDeleted() -
		// if WorkspaceFolders capability present then can recycle the wrapper/server, otherwise a new one gets created
		assertTrue(wrapper1 == wrapper2);
	}

	@Test
	public void testPojectCreate() throws Exception {
		IFile testFile1 = TestUtils.createUniqueTestFile(project, "");

		TestUtils.openEditor(testFile1);
		Collection<LanguageServerWrapper> wrappers = LanguageServiceAccessor.getLSWrappers(testFile1, c -> true);
		waitForAndAssertCondition(5_000, () -> MockLanguageServer.INSTANCE.isRunning());
		ConnectDocumentToLanguageServerSetupParticipant.waitForAll();

		LanguageServerWrapper wrapper1 = wrappers.iterator().next();
		assertTrue(wrapper1.isActive());

		UI.getActivePage().closeAllEditors(false);
		waitForAndAssertCondition(5_000, () -> !MockLanguageServer.INSTANCE.isRunning());

		final MockWorkspaceService mockWorkspaceService = MockLanguageServer.INSTANCE.getWorkspaceService();
		final List<DidChangeWorkspaceFoldersParams> events = mockWorkspaceService.getWorkspaceFoldersEvents();
		assertEquals(1, events.size());
		final List<WorkspaceFolder> added = events.get(0).getEvent().getAdded();
		assertEquals(1, added.size());
		assertEquals(new File(project.getLocationURI()), new File(new URI(added.get(0).getUri()).normalize()));
	}

	@Test
	public void testProjectClose() throws Exception {
		IFile testFile1 = TestUtils.createUniqueTestFile(project, "");

		TestUtils.openEditor(testFile1);
		LanguageServiceAccessor.getLSWrappers(testFile1, capabilities -> true).iterator().next();
		waitForAndAssertCondition(5_000, () -> MockLanguageServer.INSTANCE.isRunning());
		ConnectDocumentToLanguageServerSetupParticipant.waitForAll();
		final JobSynchronizer synchronizer = new JobSynchronizer();
		project.close(synchronizer);
		synchronizer.await();

		waitForAndAssertCondition(5_000, () -> {
			assertEquals(2, MockLanguageServer.INSTANCE.getWorkspaceService().getWorkspaceFoldersEvents().size());
			return true;
		});
		final MockWorkspaceService mockWorkspaceService = MockLanguageServer.INSTANCE.getWorkspaceService();
		final List<DidChangeWorkspaceFoldersParams> events = mockWorkspaceService.getWorkspaceFoldersEvents();
		assertEquals(2, events.size());
		final List<WorkspaceFolder> removed = events.get(1).getEvent().getRemoved();
		assertEquals(1, removed.size());
		assertEquals(new File(project.getLocationURI()), new File(new URI(removed.get(0).getUri())));
	}

	@Test
	public void testProjectDelete() throws Exception {
		IFile testFile1 = TestUtils.createUniqueTestFile(project, "");

		TestUtils.openEditor(testFile1);
		Collection<LanguageServerWrapper> wrappers = LanguageServiceAccessor.getLSWrappers(testFile1, c -> true);
		waitForAndAssertCondition(5_000, () -> MockLanguageServer.INSTANCE.isRunning());
		ConnectDocumentToLanguageServerSetupParticipant.waitForAll();

		LanguageServerWrapper wrapper1 = wrappers.iterator().next();
		assertTrue(wrapper1.isActive());

		// Grab this before deletion otherwise project.getLocationURI will be null...
		final File expected = new File(project.getLocationURI());
		final JobSynchronizer synchronizer = new JobSynchronizer();
		project.delete(true, true, synchronizer);
		synchronizer.await();
		final MockWorkspaceService mockWorkspaceService = MockLanguageServer.INSTANCE.getWorkspaceService();
		final List<DidChangeWorkspaceFoldersParams> events = mockWorkspaceService.getWorkspaceFoldersEvents();
		assertEquals(2, events.size());
		final List<WorkspaceFolder> removed = events.get(1).getEvent().getRemoved();
		assertEquals(1, removed.size());

		// Compare files to bodge round URI canonicalization problems
		assertEquals(expected, new File(new URI(removed.get(0).getUri())));
	}

	@Test
	public void testProjectReopen() throws Exception {
		IFile testFile1 = TestUtils.createUniqueTestFile(project, "");

		TestUtils.openEditor(testFile1);
		LanguageServiceAccessor.getLSWrappers(testFile1, capabilities -> true).iterator().next();
		waitForAndAssertCondition(5_000, () -> MockLanguageServer.INSTANCE.isRunning());
		ConnectDocumentToLanguageServerSetupParticipant.waitForAll();

		final JobSynchronizer synchronizer = new JobSynchronizer();
		project.close(synchronizer);
		synchronizer.await();

		waitForAndAssertCondition(5_000, () -> !project.isOpen());

		final JobSynchronizer synchronizer2 = new JobSynchronizer();
		project.open(synchronizer2);
		synchronizer2.await();

		waitForAndAssertCondition(5_000, () -> project.isOpen());

		waitForAndAssertCondition(5_000, () -> {
			assertEquals(3, MockLanguageServer.INSTANCE.getWorkspaceService().getWorkspaceFoldersEvents().size());
			return true;
		});
		final MockWorkspaceService mockWorkspaceService = MockLanguageServer.INSTANCE.getWorkspaceService();
		final List<DidChangeWorkspaceFoldersParams> events = mockWorkspaceService.getWorkspaceFoldersEvents();
		final List<WorkspaceFolder> added = events.get(2).getEvent().getAdded();
		assertEquals(1, added.size());
		assertEquals(new File(project.getLocationURI()), new File(new URI(added.get(0).getUri())));
	}

	@Override
	public ServerCapabilities get() {
		// Enable workspace folders on the mock server (for this test only)
		final ServerCapabilities base = MockLanguageServer.defaultServerCapabilities();

		final WorkspaceServerCapabilities wsc = new WorkspaceServerCapabilities();
		final WorkspaceFoldersOptions wso = new WorkspaceFoldersOptions();
		wso.setSupported(true);
		wso.setChangeNotifications(true);
		wsc.setWorkspaceFolders(wso);
		base.setWorkspace(wsc);
		return base;
	}
}
