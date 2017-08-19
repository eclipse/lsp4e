/*******************************************************************************
 * Copyright (c) 2016-2017 Rogue Wave Software Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *  Mickael Istria (Red Hat Inc.) - added test for Run config
 *******************************************************************************/
package org.eclipse.lsp4e.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.lsp4e.ProjectSpecificLanguageServerWrapper;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.ui.PlatformUI;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class LanguageServiceAccessorTest {

	private IProject project;

	@Before
	public void setUp() throws CoreException {
		project = TestUtils.createProject("LanguageServiceAccessorTest" + System.currentTimeMillis());
	}

	@After
	public void tearDown() throws CoreException {
		PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().closeAllEditors(false);
		project.delete(true, true, new NullProgressMonitor());
	}

	@Test
	public void testGetLSPDocumentInfoForInvalidDocument() {
		Collection<LSPDocumentInfo> infos = LanguageServiceAccessor.getLSPDocumentInfosFor(new Document(), null);
		assertTrue(infos.isEmpty());
	}

	@Test
	public void testGetLSPDocumentInfoForInvalidTextEditor() throws CoreException, InvocationTargetException {
		IFile testFile = TestUtils.createFile(project, "not_associated_with_ls.abc", "");
		ITextViewer textViewer = TestUtils.openTextViewer(testFile);
		Collection<LSPDocumentInfo> infos = LanguageServiceAccessor.getLSPDocumentInfosFor(textViewer.getDocument(), capabilities -> Boolean.TRUE);
		assertTrue(infos.isEmpty());
	}
	
	@Test
	public void testGetLanguageServerInvalidFile() throws Exception {
		IFile testFile = TestUtils.createFile(project, "not_associated_with_ls.abc", "");
		Collection<LanguageServer> servers = LanguageServiceAccessor.getLanguageServers(testFile, capabilites -> Boolean.TRUE);
		assertTrue(servers.isEmpty());
	}

	@Test
	public void testLSAsExtension() throws Exception {
		IFile testFile = TestUtils.createFile(project, "shouldUseExtension.lspt", "");
		LanguageServer info = LanguageServiceAccessor.getLanguageServers(testFile, capabilites -> Boolean.TRUE).iterator().next();
		assertNotNull(info);
	}

	@Test
	public void testLSAsRunConfiguration() throws Exception {
		IFile testFile = TestUtils.createFile(project, "shouldUseRunConfiguration.lspt2", "");
		LanguageServer info = LanguageServiceAccessor.getLanguageServers(testFile, capabilites -> Boolean.TRUE).iterator().next();
		assertNotNull(info);
	}
	
	@Test
	public void testLSAsExtensionForDifferentLanguageId() throws Exception {
		IFile testFile = TestUtils.createFile(project, "shouldUseExtension.lspt-different", "");		@NonNull
		Collection<ProjectSpecificLanguageServerWrapper> lsWrappers = LanguageServiceAccessor.getLSWrappers(testFile, capabilites -> Boolean.TRUE);
		
		assertEquals(1, lsWrappers.size());
		ProjectSpecificLanguageServerWrapper wrapper = lsWrappers.iterator().next();
		assertNotNull(wrapper);
		
		IContentType contentType = Platform.getContentTypeManager().getContentType("org.eclipse.lsp4e.test.content-type-different");
		assertEquals("differentLanguageId", wrapper.getLanguageId(new IContentType[] {contentType}));
	}

	@Test
	public void testReuseSameLSforMultiContentType() throws Exception {
		IFile testFile1 = TestUtils.createUniqueTestFile(project, "");
		IFile testFile2 = TestUtils.createUniqueTestFileMultiLS(project, "");
		// wrap in HashSet as a workaround of https://github.com/eclipse/lsp4j/issues/106
		Collection<LanguageServer> file1LanguageServers = new HashSet<>(LanguageServiceAccessor.getLanguageServers(testFile1, c -> Boolean.TRUE));
		assertEquals(1, file1LanguageServers.size());
		LanguageServer file1LS = file1LanguageServers.iterator().next();
		Collection<LanguageServer> file2LanguageServers = new HashSet<>(LanguageServiceAccessor.getLanguageServers(testFile2, c -> Boolean.TRUE)); // shou
		assertEquals(2, file2LanguageServers.size());
		assertTrue(file2LanguageServers.contains(file1LS)); // LS from file1 is reused
		assertEquals("Not right amount of language servers bound to project", 2, LanguageServiceAccessor.getLanguageServers(project, c -> Boolean.TRUE).size());
	}

	@Test
	public void testDontRestartUnrelatedLSForFileFromSameProject() throws Exception {
		IFile testFile1 = TestUtils.createUniqueTestFile(project, "");
		IFile testFile2 = TestUtils.createUniqueTestFile(project, "lspt-different", "");

		Collection<ProjectSpecificLanguageServerWrapper> wrappers1 = LanguageServiceAccessor.getLSWrappers(testFile1, c -> Boolean.TRUE);
		assertEquals(1, wrappers1.size());
		ProjectSpecificLanguageServerWrapper wrapper1 = wrappers1.iterator().next();
		assertTrue(wrapper1.isActive());
		
		wrapper1.disconnect(testFile1.getFullPath());
		assertFalse(wrapper1.isActive());
		
		Collection<ProjectSpecificLanguageServerWrapper> wrappers2 = LanguageServiceAccessor.getLSWrappers(testFile2, c -> Boolean.TRUE);
		assertEquals(1, wrappers2.size());
		ProjectSpecificLanguageServerWrapper wrapper2 = wrappers2.iterator().next();
		assertTrue(wrapper2.isActive());
		
		// make sure the language server for testFile1 (which is unrelated to testFile2 is not started again)
		assertFalse(wrapper1.isActive());

		wrapper2.disconnect(testFile2.getFullPath());
	}

	@Test
	public void testLanguageServerHierarchy_moreSpecializedFirst() throws Exception {
		// file with a content-type and a parent, each associated to one LS
		IFile testFile = TestUtils.createUniqueTestFile(project, "lsptchild", "");
		@NonNull Collection<ProjectSpecificLanguageServerWrapper> servers = LanguageServiceAccessor.getLSWrappers(testFile, c -> Boolean.TRUE);
		Iterator<ProjectSpecificLanguageServerWrapper> iterator = servers.iterator();
		assertEquals("org.eclipse.lsp4e.test.server2", iterator.next().serverDefinition.id);
		assertEquals("org.eclipse.lsp4e.test.server", iterator.next().serverDefinition.id);
	}
		
	@Test
	public void testLanguageServerHierarchy_parentContentTypeUsed() throws Exception {
		// file with a content-type whose parent (only) is associated to one LS
		IFile testFile = TestUtils.createUniqueTestFile(project, "lsptchildNoLS", "");
		@NonNull Collection<ProjectSpecificLanguageServerWrapper> servers = LanguageServiceAccessor.getLSWrappers(testFile, c -> Boolean.TRUE);
		Iterator<ProjectSpecificLanguageServerWrapper> iterator = servers.iterator();
		assertEquals("org.eclipse.lsp4e.test.server", iterator.next().serverDefinition.id);
		assertFalse("Should only be a single LS", iterator.hasNext());
	}
}
