/*******************************************************************************
 * Copyright (c) 2018 Pivotal Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Kris De Volder - Initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.commands;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.test.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageSever;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.Unregistration;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.WorkspaceFoldersOptions;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.ui.PlatformUI;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.gson.Gson;

public class DynamicRegistrationTest {

	private static final String WORKSPACE_EXECUTE_COMMAND = "workspace/executeCommand";
	private static final String WORKSPACE_DID_CHANGE_FOLDERS = "workspace/didChangeWorkspaceFolders";

	private IProject project;

	@Before
	public void setUp() throws Exception {
		MockLanguageSever.reset();
		LanguageServiceAccessor.clearStartedServers();
		project = TestUtils.createProject("CommandRegistrationTest" + System.currentTimeMillis());
		IFile testFile = TestUtils.createFile(project, "shouldUseExtension.lspt", "");

		// Make sure mock language server is created...
		LanguageServer info = LanguageServiceAccessor
				.getInitializedLanguageServers(testFile, capabilites -> Boolean.TRUE).iterator().next()
				.get(1, TimeUnit.SECONDS);
		assertNotNull(info);
	}

	@After
	public void tearDown() throws CoreException {
		PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().closeAllEditors(false);
		project.delete(true, true, new NullProgressMonitor());
	}

	@Test
	public void testCommandRegistration() throws Exception {
		@NonNull List<@NonNull LanguageServer> servers = LanguageServiceAccessor.getLanguageServers(c -> true);
		assertEquals(1, servers.size());

		assertTrue(LanguageServiceAccessor.getLanguageServers(handlesCommand("test.command")).isEmpty());
		
		UUID registration = registerCommands("test.command", "test.command.2");
		try {
			assertEquals(1, LanguageServiceAccessor.getLanguageServers(handlesCommand("test.command")).size());
			assertEquals(1, LanguageServiceAccessor.getLanguageServers(handlesCommand("test.command.2")).size());
		} finally {
			unregister(registration);
		}
		assertTrue(LanguageServiceAccessor.getLanguageServers(handlesCommand("test.command")).isEmpty());
		assertTrue(LanguageServiceAccessor.getLanguageServers(handlesCommand("test.command.2")).isEmpty());
	}

	@Test
	public void testWorkspaceFoldersRegistration() throws Exception {
		@NonNull List<@NonNull LanguageServer> servers = LanguageServiceAccessor.getLanguageServers(c -> true);
		assertEquals(1, servers.size());

		assertTrue(LanguageServiceAccessor.getLanguageServers(c -> hasWorkspaceFolderSupport(c)).isEmpty());

		UUID registration = registerWorkspaceFolders();
		try {
			assertEquals(1, LanguageServiceAccessor.getLanguageServers(c -> hasWorkspaceFolderSupport(c)).size());
		} finally {
			unregister(registration);
		}
		assertTrue(LanguageServiceAccessor.getLanguageServers(c -> hasWorkspaceFolderSupport(c)).isEmpty());
		assertEquals(1, LanguageServiceAccessor.getLanguageServers(c -> !hasWorkspaceFolderSupport(c)).size());
	}

	//////////////////////////////////////////////////////////////////////////////////

	private void unregister(UUID registration) throws Exception {
		LanguageClient client = getMockClient();
		Unregistration unregistration = new Unregistration(registration.toString(), WORKSPACE_EXECUTE_COMMAND);
		client.unregisterCapability(new UnregistrationParams(Arrays.asList(unregistration)))
		.get(1, TimeUnit.SECONDS);
	}

	private UUID registerWorkspaceFolders() throws Exception {
		UUID id = UUID.randomUUID();
		LanguageClient client = getMockClient();
		Registration registration = new Registration();
		registration.setId(id.toString());
		registration.setMethod(WORKSPACE_DID_CHANGE_FOLDERS);
		client.registerCapability(new RegistrationParams(Arrays.asList(registration)))
		.get(1, TimeUnit.SECONDS);
		return id;
	}

	private UUID registerCommands(String... command) throws Exception {
		UUID id = UUID.randomUUID();
		LanguageClient client = getMockClient();
		Registration registration = new Registration();
		registration.setId(id.toString());
		registration.setMethod(WORKSPACE_EXECUTE_COMMAND);
		registration.setRegisterOptions(new Gson().toJsonTree(new ExecuteCommandOptions(Arrays.asList(command))));
		client.registerCapability(new RegistrationParams(Arrays.asList(registration))).get(1, TimeUnit.SECONDS);
		return id;
	}

	private LanguageClient getMockClient() {
		List<LanguageClient> proxies = MockLanguageSever.INSTANCE.getRemoteProxies();
		assertEquals(1, proxies.size());
		return proxies.get(0);
	}

	private Predicate<ServerCapabilities> handlesCommand(String command) {
		return (cap) -> {
			ExecuteCommandOptions commandProvider = cap.getExecuteCommandProvider();
			return commandProvider != null && commandProvider.getCommands().contains(command);
		};
	}

	private boolean hasWorkspaceFolderSupport(ServerCapabilities cap) {
		if (cap != null) {
			WorkspaceServerCapabilities ws = cap.getWorkspace();
			if (ws != null) {
				WorkspaceFoldersOptions f = ws.getWorkspaceFolders();
				if (f != null) {
					return f.getSupported();
				}
			}
		}
		return false;
	}

}
