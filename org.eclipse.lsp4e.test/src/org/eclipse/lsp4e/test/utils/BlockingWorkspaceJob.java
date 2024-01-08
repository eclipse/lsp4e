/*******************************************************************************
 * Copyright (c) 2024 Erik Brangs and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * Erik Brangs - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.utils;

import java.util.concurrent.CountDownLatch;

import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

public class BlockingWorkspaceJob extends WorkspaceJob {

	public BlockingWorkspaceJob(String name) {
		super(name);
	}

	private CountDownLatch countDownLatch = new CountDownLatch(1);

	@Override
	public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
		try {
			countDownLatch.await();
		} catch (InterruptedException e) {
			return Status.CANCEL_STATUS;
		}
		return Status.OK_STATUS;
	}

	public void progress() {
		countDownLatch.countDown();
	}

}
