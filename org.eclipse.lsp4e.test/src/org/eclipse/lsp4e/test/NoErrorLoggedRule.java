/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.ILogListener;
import org.eclipse.core.runtime.IStatus;
import org.junit.Assert;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

public class NoErrorLoggedRule extends TestWatcher {

	private ILog log;
	private ILogListener listener;
	private List<IStatus> loggedErrors;

	public NoErrorLoggedRule(ILog log) {
		this.log = log;
		listener = (status, message) -> {
			if (status.getSeverity() == IStatus.ERROR) {
				loggedErrors.add(status);
			}
		};
	}
	
	@Override
	protected void starting(Description description) {
		super.starting(description);
		this.loggedErrors = new ArrayList<>();
		log.addLogListener(listener);
	}

	@Override
	protected void finished(Description description) {
		log.removeLogListener(listener);
		Assert.assertEquals("Some errors were logged", Collections.emptyList(), loggedErrors);
		super.finished(description);
	}

}
