/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test;

import java.util.function.Supplier;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.lsp4e.ConnectDocumentToLanguageServerSetupParticipant;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.intro.IIntroPart;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

public class AllCleanRule extends TestWatcher {
	
	private final Supplier<ServerCapabilities> serverConfigurer;
	
	public AllCleanRule() {
		this.serverConfigurer = MockLanguageServer::defaultServerCapabilities;
	}
	
	public AllCleanRule(final Supplier<ServerCapabilities> serverConfigurer) {
		this.serverConfigurer = serverConfigurer;
	}

	@Override
	protected void starting(Description description) {
		super.starting(description);
		IIntroPart intro = PlatformUI.getWorkbench().getIntroManager().getIntro();
		if (intro != null) {
			PlatformUI.getWorkbench().getIntroManager().closeIntro(intro);
		}
		clear();
	}
	
	@Override
	protected void finished(Description description) {
		clear();
		super.finished(description);
	}

	private void clear() {
		// Give the platform three attempts to shut down windows
		for (int i = 3; i > 0 && !PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().closeAllEditors(false); i--) {}
		ConnectDocumentToLanguageServerSetupParticipant.waitForAll();
		for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
			try {
				project.delete(true, null);
			} catch (CoreException e) {
				e.printStackTrace();
			}
		}
		LanguageServiceAccessor.clearStartedServers();
		MockLanguageServer.reset(this.serverConfigurer);
		TestUtils.tearDown();
	}
}
