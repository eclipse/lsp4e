/*******************************************************************************
 * Copyright (c) 2016, 2018 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *  Mickael Istria (Red Hat Inc.) - added test for Run config
 *  Martin Lippert (Pivotal Inc.) - added tests for multi-root folders, wrapper re-use, and more
 *******************************************************************************/
package org.eclipse.lsp4e.test;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.internal.core.IInternalDebugCoreConstants;
import org.eclipse.debug.internal.core.Preferences;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServersRegistry;
import org.eclipse.lsp4e.LanguageServersRegistry.LanguageServerDefinition;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4e.tests.mock.MockLanguageServerMultiRootFolders;
import org.eclipse.lsp4j.HoverOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.ui.texteditor.ITextEditor;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class LanguageServiceAccessorTest {

	@Rule public AllCleanRule clear = new AllCleanRule();
	private IProject project;

	@Before
	public void setUp() throws CoreException {
		project = TestUtils.createProject("LanguageServiceAccessorTest" + System.currentTimeMillis());
	}

	@Test
	public void testGetLSWrapper() throws IOException {
		LanguageServerDefinition serverDefinition = LanguageServersRegistry.getInstance().getDefinition("org.eclipse.lsp4e.test.server");
		assertNotNull(serverDefinition);
		LanguageServerWrapper lsWrapper = LanguageServiceAccessor.getLSWrapper(project, serverDefinition);
		assertNotNull(lsWrapper);
	}

	@Test
	public void testGetLSPDocumentInfoForInvalidDocument() {
		Collection<LSPDocumentInfo> infos = LanguageServiceAccessor.getLSPDocumentInfosFor(new Document(), null);
		assertTrue(infos.isEmpty());
	}

	@Test
	public void testGetLSPDocumentInfoForInvalidTextEditor() throws CoreException {
		IFile testFile = TestUtils.createFile(project, "not_associated_with_ls.abc", "");
		ITextViewer textViewer = TestUtils.openTextViewer(testFile);
		Collection<LSPDocumentInfo> infos = LanguageServiceAccessor.getLSPDocumentInfosFor(textViewer.getDocument(), capabilities -> Boolean.TRUE);
		assertTrue(infos.isEmpty());
	}

	@Test
	public void testGetLanguageServerInvalidFile() throws Exception {
		IFile testFile = TestUtils.createFile(project, "not_associated_with_ls.abc", "");
		Collection<LanguageServer> servers = new ArrayList<>();
		for (CompletableFuture<LanguageServer> future :LanguageServiceAccessor.getInitializedLanguageServers(testFile, capabilites -> Boolean.TRUE)){
			servers.add(future.get(1, TimeUnit.SECONDS));
		}
		assertTrue(servers.isEmpty());
	}

	@Test
	public void testLSAsExtension() throws Exception {
		IFile testFile = TestUtils.createFile(project, "shouldUseExtension.lspt", "");
		LanguageServer info = LanguageServiceAccessor
				.getInitializedLanguageServers(testFile, capabilites -> Boolean.TRUE).iterator().next()
				.get(1, TimeUnit.SECONDS);
		assertNotNull(info);
	}

	@Test
	public void testLSAsRunConfiguration() throws Exception {
		IFile testFile = TestUtils.createFile(project, "shouldUseRunConfiguration.lspt2", "");
		LanguageServer info = LanguageServiceAccessor
				.getInitializedLanguageServers(testFile, capabilites -> Boolean.TRUE).iterator().next()
				.get(1, TimeUnit.SECONDS);
		assertNotNull(info);
	}

	@Test
	public void testLSAsExtensionForDifferentLanguageId() throws Exception {
		IFile testFile = TestUtils.createFile(project, "shouldUseExtension.lspt-different", "");		@NonNull
		Collection<LanguageServerWrapper> lsWrappers = LanguageServiceAccessor.getLSWrappers(testFile,
				capabilites -> Boolean.TRUE);

		assertEquals(1, lsWrappers.size());
		LanguageServerWrapper wrapper = lsWrappers.iterator().next();
		assertNotNull(wrapper);

		IContentType contentType = Platform.getContentTypeManager().getContentType("org.eclipse.lsp4e.test.content-type-different");
		assertEquals("differentLanguageId", wrapper.getLanguageId(new IContentType[] {contentType}));
	}

	@Test
	public void testGetLSWrappersInitializationFailed() throws Exception {
		IFile testFile = TestUtils.createFile(project, "fileWithFailedServer.lsptWithException", "");
		Collection<LanguageServerWrapper> wrappers = LanguageServiceAccessor.getLSWrappers(testFile,
				capabilites -> Boolean.TRUE);
		assertEquals(1, wrappers.size());
	}

	@Test
	public void testReuseSameLSforMultiContentType() throws Exception {
		IFile testFile1 = TestUtils.createUniqueTestFile(project, "");
		IFile testFile2 = TestUtils.createUniqueTestFileMultiLS(project, "");
		// wrap in HashSet as a workaround of https://github.com/eclipse/lsp4j/issues/106
		Collection<LanguageServer> file1LanguageServers = new ArrayList<>();
		for (CompletableFuture<LanguageServer> future : LanguageServiceAccessor.getInitializedLanguageServers(testFile1,
				capabilites -> Boolean.TRUE)) {
			file1LanguageServers.add(future.get(1, TimeUnit.SECONDS));
		}
		assertEquals(1, file1LanguageServers.size());
		LanguageServer file1LS = file1LanguageServers.iterator().next();
		Collection<LanguageServer> file2LanguageServers = new ArrayList<>();
		for (CompletableFuture<LanguageServer> future : LanguageServiceAccessor.getInitializedLanguageServers(testFile2,
				capabilites -> Boolean.TRUE)) {
			file2LanguageServers.add(future.get(1, TimeUnit.SECONDS));
		}
		assertEquals(2, file2LanguageServers.size());
		assertTrue(file2LanguageServers.contains(file1LS)); // LS from file1 is reused
		assertEquals("Not right amount of language servers bound to project", 2, LanguageServiceAccessor.getLanguageServers(project, c -> Boolean.TRUE).size());
	}

	@Test
	public void testGetOnlyRunningLanguageServers() throws Exception {
		Display display = Display.getCurrent();

		IFile testFile1 = TestUtils.createUniqueTestFile(project, "");
		IFile testFile2 = TestUtils.createUniqueTestFile(project, "lspt-different", "");

		IEditorPart editor1 = TestUtils.openEditor(testFile1);
		IEditorPart editor2 = TestUtils.openEditor(testFile2);

		LanguageServiceAccessor.getInitializedLanguageServers(testFile1, capabilities -> Boolean.TRUE).iterator()
				.next();
		LanguageServiceAccessor.getInitializedLanguageServers(testFile2, capabilities -> Boolean.TRUE).iterator()
				.next();

		List<LanguageServer> runningServers = LanguageServiceAccessor.getActiveLanguageServers(capabilities -> Boolean.TRUE);
		assertEquals(2, runningServers.size());

		((AbstractTextEditor) editor1).close(false);
		((AbstractTextEditor) editor2).close(false);

		new LSDisplayHelper(() -> LanguageServiceAccessor.getActiveLanguageServers(capabilities -> Boolean.TRUE).size() == 0)
				.waitForCondition(display, 5000);
		assertEquals(0, LanguageServiceAccessor.getActiveLanguageServers(capabilities -> Boolean.TRUE).size());

		editor1 = TestUtils.openEditor(testFile1);
		LanguageServiceAccessor.getInitializedLanguageServers(testFile1, capabilities -> Boolean.TRUE).iterator()
				.next();

		new LSDisplayHelper(() -> LanguageServiceAccessor.getActiveLanguageServers(capabilities -> Boolean.TRUE).size() == 1)
				.waitForCondition(display, 5000);
		assertEquals(1, LanguageServiceAccessor.getActiveLanguageServers(capabilities -> Boolean.TRUE).size());
	}

	@Test
	public void testCreateNewLSAfterInitialProjectGotDeleted() throws Exception {
		IFile testFile1 = TestUtils.createUniqueTestFile(project, "");

		TestUtils.openEditor(testFile1);
		LanguageServiceAccessor.getInitializedLanguageServers(testFile1, capabilities -> Boolean.TRUE).iterator()
				.next();
		new LSDisplayHelper(() -> MockLanguageServer.INSTANCE.isRunning()).waitForCondition(Display.getCurrent(), 5000,
				300);

		Collection<LanguageServerWrapper> wrappers = LanguageServiceAccessor.getLSWrappers(testFile1,
				c -> Boolean.TRUE);
		LanguageServerWrapper wrapper1 = wrappers.iterator().next();
		assertTrue(wrapper1.isActive());

		PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().closeAllEditors(false);
		new LSDisplayHelper(() -> !MockLanguageServer.INSTANCE.isRunning()).waitForCondition(Display.getCurrent(), 5000,
				300);

		project.delete(true, true, new NullProgressMonitor());

		project = TestUtils.createProject("LanguageServiceAccessorTest2" + System.currentTimeMillis());
		IFile testFile2 = TestUtils.createUniqueTestFile(project, "");

		TestUtils.openEditor(testFile2);
		LanguageServiceAccessor.getInitializedLanguageServers(testFile2, capabilities -> Boolean.TRUE).iterator()
				.next();
		new LSDisplayHelper(() -> MockLanguageServer.INSTANCE.isRunning()).waitForCondition(Display.getCurrent(), 5000,
				300);

		wrappers = LanguageServiceAccessor.getLSWrappers(testFile2, c -> Boolean.TRUE);
		LanguageServerWrapper wrapper2 = wrappers.iterator().next();
		assertTrue(wrapper2.isActive());

		assertTrue(wrapper1 != wrapper2);
	}

	@Test
	public void testReuseMultirootFolderLSAfterInitialProjectGotDeleted() throws Exception {
		IFile testFile1 = TestUtils.createUniqueTestFile(project, "lsptWithMultiRoot", "");

		TestUtils.openEditor(testFile1);
		LanguageServiceAccessor.getInitializedLanguageServers(testFile1, capabilities -> Boolean.TRUE).iterator()
				.next();
		new LSDisplayHelper(() -> MockLanguageServerMultiRootFolders.INSTANCE.isRunning())
				.waitForCondition(Display.getCurrent(), 5000, 300);

		Collection<LanguageServerWrapper> wrappers = LanguageServiceAccessor.getLSWrappers(testFile1,
				c -> Boolean.TRUE);
		LanguageServerWrapper wrapper1 = wrappers.iterator().next();
		assertTrue(wrapper1.isActive());

		PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().closeAllEditors(false);
		new LSDisplayHelper(() -> !MockLanguageServerMultiRootFolders.INSTANCE.isRunning())
				.waitForCondition(Display.getCurrent(), 5000, 300);

		project.delete(true, true, new NullProgressMonitor());

		project = TestUtils.createProject("LanguageServiceAccessorTest2" + System.currentTimeMillis());
		IFile testFile2 = TestUtils.createUniqueTestFile(project, "lsptWithMultiRoot", "");

		TestUtils.openEditor(testFile2);
		LanguageServiceAccessor.getInitializedLanguageServers(testFile2, capabilities -> Boolean.TRUE).iterator()
				.next();
		new LSDisplayHelper(() -> MockLanguageServerMultiRootFolders.INSTANCE.isRunning())
				.waitForCondition(Display.getCurrent(), 5000, 300);

		wrappers = LanguageServiceAccessor.getLSWrappers(testFile2, c -> Boolean.TRUE);
		LanguageServerWrapper wrapper2 = wrappers.iterator().next();
		assertTrue(wrapper2.isActive());

		assertTrue(wrapper1 == wrapper2);
	}

	@Test
	public void testDontRestartUnrelatedLSForFileFromSameProject() throws Exception {
		IFile testFile1 = TestUtils.createUniqueTestFile(project, "");
		IFile testFile2 = TestUtils.createUniqueTestFile(project, "lspt-different", "");

		Collection<LanguageServerWrapper> wrappers1 = LanguageServiceAccessor.getLSWrappers(testFile1,
				c -> Boolean.TRUE);
		assertEquals(1, wrappers1.size());
		LanguageServerWrapper wrapper1 = wrappers1.iterator().next();
		assertTrue(wrapper1.isActive());

		wrapper1.disconnect(testFile1.getLocationURI());
		assertFalse(wrapper1.isActive());

		Collection<LanguageServerWrapper> wrappers2 = LanguageServiceAccessor.getLSWrappers(testFile2,
				c -> Boolean.TRUE);
		assertEquals(1, wrappers2.size());
		LanguageServerWrapper wrapper2 = wrappers2.iterator().next();
		assertTrue(wrapper2.isActive());

		// make sure the language server for testFile1 (which is unrelated to testFile2 is not started again)
		assertFalse(wrapper1.isActive());

		wrapper2.disconnect(testFile2.getLocationURI());
	}

	@Test
	public void testLastDocumentDisconnectedTimeoutManualStop() throws Exception {
		IFile testFile = TestUtils.createUniqueTestFile(project, "lsptWithLastDocumentDisconnectedTimeout", "");

		Collection<LanguageServerWrapper> wrappers = LanguageServiceAccessor.getLSWrappers(testFile, c -> Boolean.TRUE);
		assertEquals(1, wrappers.size());
		LanguageServerWrapper wrapper = wrappers.iterator().next();
		assertTrue(wrapper.isActive());

		wrapper.disconnect(testFile.getLocationURI());
		assertTrue(wrapper.isActive());
		wrapper.stop();
		assertFalse(wrapper.isActive());
	}

	@Test
	public void testLastDocumentDisconnectedTimeoutTimerStop() throws Exception {
		IFile testFile = TestUtils.createUniqueTestFile(project, "lsptWithLastDocumentDisconnectedTimeout", "");

		Collection<LanguageServerWrapper> wrappers = LanguageServiceAccessor.getLSWrappers(testFile, c -> Boolean.TRUE);
		assertEquals(1, wrappers.size());
		LanguageServerWrapper wrapper = wrappers.iterator().next();
		assertTrue(wrapper.isActive());

		wrapper.disconnect(testFile.getLocationURI());
		assertTrue(wrapper.isActive());
		Thread.sleep(TimeUnit.SECONDS.toMillis(5));
		assertFalse(wrapper.isActive());
	}

	@Test
	public void testLastDocumentDisconnectedTimeoutZero() throws Exception {
		IFile testFile = TestUtils.createUniqueTestFile(project, "");

		Collection<LanguageServerWrapper> wrappers = LanguageServiceAccessor.getLSWrappers(testFile, c -> Boolean.TRUE);
		assertEquals(1, wrappers.size());
		LanguageServerWrapper wrapper = wrappers.iterator().next();
		assertTrue(wrapper.isActive());

		wrapper.disconnect(testFile.getLocationURI());
		assertFalse(wrapper.isActive());
	}

	@Test
	public void testLanguageServerHierarchy_moreSpecializedFirst() throws Exception {
		// file with a content-type and a parent, each associated to one LS
		IFile testFile = TestUtils.createUniqueTestFile(project, "lsptchild", "");
		@NonNull Collection<LanguageServerWrapper> servers = LanguageServiceAccessor.getLSWrappers(testFile,
				c -> Boolean.TRUE);
		Iterator<LanguageServerWrapper> iterator = servers.iterator();
		assertEquals("org.eclipse.lsp4e.test.server2", iterator.next().serverDefinition.id);
		assertEquals("org.eclipse.lsp4e.test.server", iterator.next().serverDefinition.id);
	}

	@Test
	public void testLanguageServerHierarchy_parentContentTypeUsed() throws Exception {
		// file with a content-type whose parent (only) is associated to one LS
		IFile testFile = TestUtils.createUniqueTestFile(project, "lsptchildNoLS", "");
		@NonNull Collection<LanguageServerWrapper> servers = LanguageServiceAccessor.getLSWrappers(testFile,
				c -> Boolean.TRUE);
		Iterator<LanguageServerWrapper> iterator = servers.iterator();
		assertEquals("org.eclipse.lsp4e.test.server", iterator.next().serverDefinition.id);
		assertFalse("Should only be a single LS", iterator.hasNext());
	}

	@Test
	public void testLanguageServerEnablement() throws Exception {
		LanguageServerPlugin.getDefault().getPreferenceStore().setValue(
				"org.eclipse.lsp4e.test.server.disable" + "/" + "org.eclipse.lsp4e.test.content-type-disabled",
				"false");
		IFile disabledFile = TestUtils.createUniqueTestFile(project, "lspt-disabled", "");
		IFile enabledFile = TestUtils.createUniqueTestFile(project, "lspt-enabled", "");

		LanguageServiceAccessor.getLSWrappers(disabledFile, capabilities -> true).stream().forEach(
				wrapper -> assertFalse(wrapper.serverDefinition.id.equals("org.eclipse.lsp4e.test.server.disable")));
		assertTrue(LanguageServiceAccessor.getLSWrappers(enabledFile, capabilities -> true).stream()
				.filter(wrapper -> wrapper.serverDefinition.id.equals("org.eclipse.lsp4e.test.server.disable"))
				.findFirst().isPresent());

		LanguageServerPlugin.getDefault().getPreferenceStore().setValue(
				"org.eclipse.lsp4e.test.server.disable" + "/" + "org.eclipse.lsp4e.test.content-type-disabled",
				"true");
		assertTrue(LanguageServiceAccessor.getLSWrappers(disabledFile, capabilities -> true).stream()
				.filter(wrapper -> wrapper.serverDefinition.id.equals("org.eclipse.lsp4e.test.server.disable"))
				.findFirst().isPresent());
		assertTrue(LanguageServiceAccessor.getLSWrappers(enabledFile, capabilities -> true).stream()
				.filter(wrapper -> wrapper.serverDefinition.id.equals("org.eclipse.lsp4e.test.server.disable"))
				.findFirst().isPresent());
	}

	@Test
	public void testLanguageServerEnablementTester() throws Exception {
		IFile file = TestUtils.createUniqueTestFile(project, "lspt-tester", "");
		assertTrue(LanguageServiceAccessor.getLSWrappers(file, capabilities -> true).isEmpty());
		MappingEnablementTester.enabled = true;

		Collection<LanguageServerWrapper> wrappers = LanguageServiceAccessor.getLSWrappers(file, capabilities -> true);
		assertEquals(1, wrappers.size());
		assertEquals("org.eclipse.lsp4e.test.server.disable", wrappers.iterator().next().serverDefinition.id);
	}

	@Test
	public void testStatusHandlerLSAsRunConfiguration() throws Exception {
		// test which checks that status handler preferences is kept after the launch is
		// done.
		IFile testFile = TestUtils.createFile(project, "shouldUseRunConfiguration.lspt2", "");

		// Test with default status handler (see DebugPlugin#getStatusHandler)
		boolean oldStatusHandler = getStatusHandler();
		LanguageServiceAccessor.getLanguageServers(LSPEclipseUtils.getDocument(testFile), null).get(1, TimeUnit.SECONDS);
		assertEquals(getStatusHandler(), oldStatusHandler);

		// Test with status handler set to false
		setStatusHandler(false);
		oldStatusHandler = getStatusHandler();
		LanguageServiceAccessor.getLanguageServers(LSPEclipseUtils.getDocument(testFile), null).get(1, TimeUnit.SECONDS);
		assertEquals(getStatusHandler(), false);

		// Test with status handler set to true
		setStatusHandler(true);
		oldStatusHandler = getStatusHandler();
		LanguageServiceAccessor.getLanguageServers(LSPEclipseUtils.getDocument(testFile), null).get(1, TimeUnit.SECONDS);
		assertEquals(getStatusHandler(), true);
	}

	@Test
	public void testLSforExternalThenLocalFile() throws Exception {
		File local = TestUtils.createTempFile("testLSforExternalThenLocalFile", ".lspt");
		ITextEditor editor = (ITextEditor) IDE.openEditorOnFileStore(
				PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(), EFS.getStore(local.toURI()));
		Assert.assertEquals(1, LanguageServiceAccessor.getLanguageServers(
				LSPEclipseUtils.getTextViewer(editor).getDocument(), this::hasHoverCapabilities).get().size());
		PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().closeAllEditors(false);
		// opening another file should either reuse the LS or spawn another one, but not
		// both
		Assert.assertEquals(1,
				LanguageServiceAccessor.getLanguageServers(
						TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, "")).getDocument(),
						this::hasHoverCapabilities).get().size());
	}

	private boolean hasHoverCapabilities(ServerCapabilities capabilities) {
		Either<Boolean, HoverOptions> hoverProvider = capabilities.getHoverProvider();
		if(hoverProvider.isLeft()) {
			return hoverProvider.getLeft();
		} else {
			return hoverProvider.getRight() != null;
		}
	}

	@Test
	public void testSingletonLS() throws Exception {
		IFile testFile1 = TestUtils.createFile(project, "shouldUseSingletonLS.lsp-singletonLS", "");
		IProject project2 = TestUtils.createProject("project2");
		IFile testFile2 = TestUtils.createFile(project2, "shouldUseSingletonLS2.lsp-singletonLS", "");
		@NonNull CompletableFuture<List<@NonNull LanguageServer>> languageServers = LanguageServiceAccessor.getLanguageServers(LSPEclipseUtils.getDocument(testFile1), cap -> true);
		@NonNull CompletableFuture<List<@NonNull LanguageServer>> languageServers2 = LanguageServiceAccessor.getLanguageServers(LSPEclipseUtils.getDocument(testFile2), cap -> true);
		Assert.assertEquals(1, languageServers.get().size());
		Assert.assertEquals(languageServers.get(), languageServers2.get());
	}

	private static boolean getStatusHandler() {
		return Platform.getPreferencesService().getBoolean(DebugPlugin.getUniqueIdentifier(),
				IInternalDebugCoreConstants.PREF_ENABLE_STATUS_HANDLERS, true, null);
	}

	/**
	 * Update the the status handler preferences
	 *
	 * @param enabled
	 *            the status handler preferences
	 */
	private static void setStatusHandler(boolean enabled) {
		Preferences.setBoolean(DebugPlugin.getUniqueIdentifier(),
				IInternalDebugCoreConstants.PREF_ENABLE_STATUS_HANDLERS, enabled, null);
	}
}
