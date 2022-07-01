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

import static org.eclipse.lsp4e.LSPEclipseUtils.*;
import static org.eclipse.lsp4e.LanguageServiceAccessor.*;
import static org.eclipse.lsp4e.test.TestUtils.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.internal.core.IInternalDebugCoreConstants;
import org.eclipse.debug.internal.core.Preferences;
import org.eclipse.jface.text.Document;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServersRegistry;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4e.tests.mock.MockLanguageServerMultiRootFolders;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.ui.texteditor.ITextEditor;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class LanguageServiceAccessorTest {

	private static final Predicate<ServerCapabilities> MATCH_ALL = capabilities -> true;

	@Rule
	public AllCleanRule clear = new AllCleanRule();
	private IProject project;

	@Before
	public void setUp() throws CoreException {
		project = createProject("LanguageServiceAccessorTest" + System.currentTimeMillis());
	}

	@Test
	public void testGetLSWrapper() throws IOException {
		var serverDefinition = LanguageServersRegistry.getInstance().getDefinition("org.eclipse.lsp4e.test.server");
		assertNotNull(serverDefinition);

		var lsWrapper = getLSWrapper(project, serverDefinition);
		assertNotNull(lsWrapper);
	}

	@Test
	public void testGetLSPDocumentInfoForInvalidDocument() {
		var infos = getLSPDocumentInfosFor(new Document(), MATCH_ALL);
		assertTrue(infos.isEmpty());
	}

	@Test
	public void testGetLSPDocumentInfoForInvalidTextEditor() throws CoreException {
		var testFile = createFile(project, "not_associated_with_ls.abc", "");
		var textViewer = openTextViewer(testFile);
		var infos = getLSPDocumentInfosFor(textViewer.getDocument(), MATCH_ALL);
		assertTrue(infos.isEmpty());
	}

	@Test
	public void testGetLanguageServerInvalidFile() throws Exception {
		var testFile = createFile(project, "not_associated_with_ls.abc", "");
		assertEmpty(getInitializedLanguageServers(testFile, MATCH_ALL));
	}

	@Test
	public void testLSAsExtension() throws Exception {
		var testFile = createFile(project, "shouldUseExtension.lspt", "");
		var ls = getInitializedLanguageServers(testFile, MATCH_ALL).get(0).get(2, TimeUnit.SECONDS);
		assertNotNull(ls);
	}

	@Test
	public void testLSAsRunConfiguration() throws Exception {
		var testFile = createFile(project, "shouldUseRunConfiguration.lspt2", "");
		var ls = getInitializedLanguageServers(testFile, MATCH_ALL).get(0).get(2, TimeUnit.SECONDS);
		assertNotNull(ls);
	}

	@Test
	public void testLSAsExtensionForDifferentLanguageId() throws Exception {
		var testFile = createFile(project, "shouldUseExtension.lspt-different", "");
		var lsWrappers = getLSWrappers(testFile, MATCH_ALL);
		assertEquals(1, lsWrappers.size());

		var wrapper = lsWrappers.iterator().next();
		assertNotNull(wrapper);

		var contentType = Platform.getContentTypeManager()
				.getContentType("org.eclipse.lsp4e.test.content-type-different");
		assertEquals("differentLanguageId", wrapper.getLanguageId(new IContentType[] { contentType }));
	}

	@Test
	public void testGetLSWrappersInitializationFailed() throws Exception {
		var testFile = createFile(project, "fileWithFailedServer.lsptWithException", "");
		var wrappers = getLSWrappers(testFile, MATCH_ALL);
		assertEquals(1, wrappers.size());
	}

	@Test
	public void testReuseSameLSforMultiContentType() throws Exception {
		var testFile1 = createUniqueTestFile(project, "");
		var testFile2 = createUniqueTestFileMultiLS(project, "");

		var file1LanguageServers = getInitializedLanguageServers(testFile1, MATCH_ALL);
		assertEquals(1, file1LanguageServers.size());

		var file2LanguageServers = new ArrayList<>();
		for (var future : getInitializedLanguageServers(testFile2, MATCH_ALL)) {
			file2LanguageServers.add(future.get(2, TimeUnit.SECONDS));
		}
		assertEquals(2, file2LanguageServers.size());

		var file1LS = file1LanguageServers.get(0).get(2, TimeUnit.SECONDS);
		assertTrue(file2LanguageServers.contains(file1LS)); // LS from file1 is reused

		assertEquals("Not right amount of language servers bound to project", 2,
				getLanguageServers(project, MATCH_ALL).size());
	}

	@Test
	public void testGetOnlyRunningLanguageServers() throws Exception {
		var testFile1 = createUniqueTestFile(project, "");
		var testFile2 = createUniqueTestFile(project, "lspt-different", "");

		var editor1 = openEditor(testFile1);
		var editor2 = openEditor(testFile2);

		assertNotEmpty(getInitializedLanguageServers(testFile1, MATCH_ALL));
		assertNotEmpty(getInitializedLanguageServers(testFile2, MATCH_ALL));

		var runningServers = getActiveLanguageServers(MATCH_ALL);
		assertEquals(2, runningServers.size());

		((AbstractTextEditor) editor1).close(false);
		((AbstractTextEditor) editor2).close(false);

		waitForCondition(5_000, () -> getActiveLanguageServers(MATCH_ALL).isEmpty());
		assertEquals(0, getActiveLanguageServers(MATCH_ALL).size());

		editor1 = openEditor(testFile1);
		assertNotEmpty(getInitializedLanguageServers(testFile1, MATCH_ALL));

		waitForCondition(5_000, () -> getActiveLanguageServers(MATCH_ALL).size() == 1);
		assertEquals(1, getActiveLanguageServers(MATCH_ALL).size());
	}

	@Test
	public void testCreateNewLSAfterInitialProjectGotDeleted() throws Exception {
		var testFile1 = createUniqueTestFile(project, "");

		openEditor(testFile1);
		assertNotEmpty(getInitializedLanguageServers(testFile1, MATCH_ALL));
		waitForCondition(5_000, () -> MockLanguageServer.INSTANCE.isRunning());

		var wrappers = getLSWrappers(testFile1, MATCH_ALL);
		var wrapper1 = wrappers.iterator().next();
		assertTrue(wrapper1.isActive());

		PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().closeAllEditors(false);
		waitForCondition(5_000, () -> !MockLanguageServer.INSTANCE.isRunning());

		project.delete(true, true, new NullProgressMonitor());

		project = createProject("LanguageServiceAccessorTest2" + System.currentTimeMillis());
		var testFile2 = createUniqueTestFile(project, "");

		openEditor(testFile2);
		assertNotEmpty(getInitializedLanguageServers(testFile2, MATCH_ALL));
		waitForCondition(5_000, () -> MockLanguageServer.INSTANCE.isRunning());

		wrappers = getLSWrappers(testFile2, MATCH_ALL);
		var wrapper2 = wrappers.iterator().next();
		assertTrue(wrapper2.isActive());

		assertTrue(wrapper1 != wrapper2);
	}

	@Test
	public void testReuseMultirootFolderLSAfterInitialProjectGotDeleted() throws Exception {
		var testFile1 = createUniqueTestFile(project, "lsptWithMultiRoot", "");

		openEditor(testFile1);
		assertNotEmpty(getInitializedLanguageServers(testFile1, MATCH_ALL));
		waitForCondition(5_000, () -> MockLanguageServerMultiRootFolders.INSTANCE.isRunning());

		var wrappers = getLSWrappers(testFile1, MATCH_ALL);
		var wrapper1 = wrappers.iterator().next();
		assertTrue(wrapper1.isActive());

		PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().closeAllEditors(false);
		waitForCondition(5_000, () -> !MockLanguageServerMultiRootFolders.INSTANCE.isRunning());

		project.delete(true, true, new NullProgressMonitor());

		project = createProject("LanguageServiceAccessorTest2" + System.currentTimeMillis());
		var testFile2 = createUniqueTestFile(project, "lsptWithMultiRoot", "");

		openEditor(testFile2);
		assertNotEmpty(getInitializedLanguageServers(testFile2, MATCH_ALL));
		waitForCondition(5_000, () -> MockLanguageServerMultiRootFolders.INSTANCE.isRunning());

		wrappers = getLSWrappers(testFile2, MATCH_ALL);
		var wrapper2 = wrappers.iterator().next();
		assertTrue(wrapper2.isActive());

		assertTrue(wrapper1 == wrapper2);
	}

	@Test
	public void testDontRestartUnrelatedLSForFileFromSameProject() throws Exception {
		var testFile1 = createUniqueTestFile(project, "");
		var testFile2 = createUniqueTestFile(project, "lspt-different", "");

		var wrappers1 = getLSWrappers(testFile1, MATCH_ALL);
		assertEquals(1, wrappers1.size());

		var wrapper1 = wrappers1.iterator().next();
		assertTrue(wrapper1.isActive());

		wrapper1.disconnect(testFile1.getLocationURI());
		assertFalse(wrapper1.isActive());

		var wrappers2 = getLSWrappers(testFile2, MATCH_ALL);
		assertEquals(1, wrappers2.size());

		var wrapper2 = wrappers2.iterator().next();
		assertTrue(wrapper2.isActive());

		// make sure the language server for testFile1 (which is unrelated to testFile2 is not started again)
		assertFalse(wrapper1.isActive());

		wrapper2.disconnect(testFile2.getLocationURI());
	}

	@Test
	public void testLastDocumentDisconnectedTimeoutManualStop() throws Exception {
		var testFile = createUniqueTestFile(project, "lsptWithLastDocumentDisconnectedTimeout", "");

		var wrappers = getLSWrappers(testFile, MATCH_ALL);
		assertEquals(1, wrappers.size());

		var wrapper = wrappers.iterator().next();
		assertTrue(wrapper.isActive());

		wrapper.disconnect(testFile.getLocationURI());
		assertTrue(wrapper.isActive());

		wrapper.stop();
		assertFalse(wrapper.isActive());
	}

	@Test
	public void testLastDocumentDisconnectedTimeoutTimerStop() throws Exception {
		var testFile = createUniqueTestFile(project, "lsptWithLastDocumentDisconnectedTimeout", "");

		var wrappers = getLSWrappers(testFile, MATCH_ALL);
		assertEquals(1, wrappers.size());

		var wrapper = wrappers.iterator().next();
		assertTrue(wrapper.isActive());

		wrapper.disconnect(testFile.getLocationURI());
		assertTrue(wrapper.isActive());

		Thread.sleep(TimeUnit.SECONDS.toMillis(5));
		assertFalse(wrapper.isActive());
	}

	@Test
	public void testLastDocumentDisconnectedTimeoutZero() throws Exception {
		var testFile = createUniqueTestFile(project, "");

		var wrappers = getLSWrappers(testFile, MATCH_ALL);
		assertEquals(1, wrappers.size());

		var wrapper = wrappers.iterator().next();
		assertTrue(wrapper.isActive());

		wrapper.disconnect(testFile.getLocationURI());
		assertFalse(wrapper.isActive());
	}

	@Test
	public void testLanguageServerHierarchy_moreSpecializedFirst() throws Exception {
		// file with a content-type and a parent, each associated to one LS
		var testFile = createUniqueTestFile(project, "lsptchild", "");
		var servers = getLSWrappers(testFile, MATCH_ALL);
		var iterator = servers.iterator();
		assertEquals("org.eclipse.lsp4e.test.server2", iterator.next().serverDefinition.id);
		assertEquals("org.eclipse.lsp4e.test.server", iterator.next().serverDefinition.id);
	}

	@Test
	public void testLanguageServerHierarchy_parentContentTypeUsed() throws Exception {
		// file with a content-type whose parent (only) is associated to one LS
		var testFile = createUniqueTestFile(project, "lsptchildNoLS", "");
		var servers = getLSWrappers(testFile, MATCH_ALL);
		var iterator = servers.iterator();
		assertEquals("org.eclipse.lsp4e.test.server", iterator.next().serverDefinition.id);
		assertFalse("Should only be a single LS", iterator.hasNext());
	}

	@Test
	public void testLanguageServerEnablement() throws Exception {
		final var serverId = ContentTypeToLanguageServerDefinitionTest.SERVER_TO_DISABLE;
		final var prefKey = serverId + "/" + "org.eclipse.lsp4e.test.content-type-disabled";
		LanguageServerPlugin.getDefault().getPreferenceStore().setValue(prefKey, Boolean.FALSE.toString());

		var disabledFile = createUniqueTestFile(project, "lspt-disabled", "");
		assertFalse(getLSWrappers(disabledFile, MATCH_ALL).stream().filter(w -> w.serverDefinition.id.equals(serverId))
				.findAny().isPresent());

		var enabledFile = createUniqueTestFile(project, "lspt-enabled", "");
		assertTrue(getLSWrappers(enabledFile, MATCH_ALL).stream().filter(w -> w.serverDefinition.id.equals(serverId))
				.findAny().isPresent());

		LanguageServerPlugin.getDefault().getPreferenceStore().setValue(prefKey, Boolean.TRUE.toString());

		assertTrue(getLSWrappers(disabledFile, MATCH_ALL).stream().filter(w -> w.serverDefinition.id.equals(serverId))
				.findAny().isPresent());
		assertTrue(getLSWrappers(enabledFile, MATCH_ALL).stream().filter(w -> w.serverDefinition.id.equals(serverId))
				.findAny().isPresent());
	}

	@Test
	public void testLanguageServerEnablementTester() throws Exception {
		final var serverId = ContentTypeToLanguageServerDefinitionTest.SERVER_TO_DISABLE;

		var file = createUniqueTestFile(project, "lspt-tester", "");
		assertTrue(getLSWrappers(file, MATCH_ALL).isEmpty());
		MappingEnablementTester.enabled = true;

		var wrappers = getLSWrappers(file, MATCH_ALL);
		assertEquals(1, wrappers.size());
		assertEquals(serverId, wrappers.iterator().next().serverDefinition.id);
	}

	@Test
	public void testStatusHandlerLSAsRunConfiguration() throws Exception {
		// test which checks that status handler preferences is kept after the launch is done.
		var testFile = createFile(project, "shouldUseRunConfiguration.lspt2", "");

		// Test with default status handler (see DebugPlugin#getStatusHandler)
		var oldStatusHandler = isStatusHandlersEnabled();
		getLanguageServers(getDocument(testFile), null).get(2, TimeUnit.SECONDS);
		assertEquals(isStatusHandlersEnabled(), oldStatusHandler);

		// Test with status handler set to false
		setStatusHandlersEnabled(false);
		oldStatusHandler = isStatusHandlersEnabled();
		getLanguageServers(getDocument(testFile), null).get(2, TimeUnit.SECONDS);
		assertEquals(isStatusHandlersEnabled(), false);

		// Test with status handler set to true
		setStatusHandlersEnabled(true);
		oldStatusHandler = isStatusHandlersEnabled();
		getLanguageServers(getDocument(testFile), null).get(2, TimeUnit.SECONDS);
		assertEquals(isStatusHandlersEnabled(), true);
	}

	@Test
	public void testLSforExternalThenLocalFile() throws Exception {
		var wb = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		var local = createTempFile("testLSforExternalThenLocalFile", ".lspt");
		var editor = (ITextEditor) IDE.openEditorOnFileStore(wb.getActivePage(), EFS.getStore(local.toURI()));

		Predicate<ServerCapabilities> hasHoverCapabilities = capabilities -> {
			var hoverProvider = capabilities.getHoverProvider();
			return hoverProvider.isLeft() ? hoverProvider.getLeft() : hoverProvider.getRight() != null;
		};

		assertEquals(1, getLanguageServers(getTextViewer(editor).getDocument(), hasHoverCapabilities).get().size());
		wb.getActivePage().closeAllEditors(false);
		// opening another file should either reuse the LS or spawn another one, but not both
		assertEquals(1, getLanguageServers( //
				openTextViewer(createUniqueTestFile(project, "")).getDocument(), hasHoverCapabilities).get().size());
	}

	@Test
	public void testSingletonLS() throws Exception {
		var testFile1 = createFile(project, "shouldUseSingletonLS.lsp-singletonLS", "");
		var languageServers = getLanguageServers(getDocument(testFile1), MATCH_ALL);

		var project2 = createProject("project2");
		var testFile2 = createFile(project2, "shouldUseSingletonLS2.lsp-singletonLS", "");
		var languageServers2 = getLanguageServers(getDocument(testFile2), MATCH_ALL);
		assertEquals(1, languageServers.get().size());
		assertEquals(languageServers.get(), languageServers2.get());
	}

	private static boolean isStatusHandlersEnabled() {
		return Platform.getPreferencesService().getBoolean(DebugPlugin.getUniqueIdentifier(),
				IInternalDebugCoreConstants.PREF_ENABLE_STATUS_HANDLERS, true, null);
	}

	/**
	 * Update the the status handler preferences
	 *
	 * @param enabled
	 *            the status handler preferences
	 */
	private static void setStatusHandlersEnabled(boolean enabled) {
		Preferences.setBoolean(DebugPlugin.getUniqueIdentifier(),
				IInternalDebugCoreConstants.PREF_ENABLE_STATUS_HANDLERS, enabled, null);
	}

	private static <T> void assertEmpty(Collection<T> coll) {
		assertTrue("Given collection is expected to be empty! " + coll, coll.isEmpty());
	}

	private static <T> void assertNotEmpty(Collection<T> coll) {
		assertFalse("Given collection must not be empty!", coll.isEmpty());
	}
}
