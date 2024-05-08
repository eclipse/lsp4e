/*******************************************************************************
 * Copyright (c) 2024 Vegard IT GmbH and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Sebastian Thomschke (Vegard IT GmbH) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.utils;

import java.io.PrintStream;

import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.ServerCapabilities;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;

/**
 * Test base class that configures a {@link AllCleanRule} and a
 * {@link TestInfoRule} and works around a surefire-plugin issue which
 * suppresses output to stderr
 */
public abstract class AbstractTest {

	private static PrintStream originalSystemErr;

	private static final boolean isExecutedBySurefirePlugin = System.getProperty("surefire.real.class.path") != null;

	@BeforeClass
	public static void setUpSystemErrRedirection() throws Exception {
		if (isExecutedBySurefirePlugin) {
			// redirect stderr to stdout during test execution as it is otherwise suppressed
			// by the surefire-plugin
			originalSystemErr = System.err;
			System.setErr(System.out);
		}
	}

	@AfterClass
	public static void tearDownSystemErrRedirection() throws Exception {
		if (isExecutedBySurefirePlugin) {
			System.setErr(originalSystemErr);
		}
	}

	public final @Rule(order = 1) AllCleanRule allCleanRule = new AllCleanRule(this::getServerCapabilities);
	public final @Rule(order = 0) TestInfoRule testInfo = new TestInfoRule();

	/**
	 * Override if required, used by {@link #allCleanRule}
	 */
	protected ServerCapabilities getServerCapabilities() {
		return MockLanguageServer.defaultServerCapabilities();
	}
}
