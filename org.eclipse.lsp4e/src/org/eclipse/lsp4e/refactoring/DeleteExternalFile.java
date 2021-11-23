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
package org.eclipse.lsp4e.refactoring;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

public class DeleteExternalFile extends Change {

	private final @NonNull File file;

	public DeleteExternalFile(@NonNull File file) {
		this.file = file;
	}

	@Override
	public String getName() {
		return "Delete " + this.file.getName(); //$NON-NLS-1$
	}

	@Override
	public void initializeValidationData(IProgressMonitor pm) {
		// nothing to do yet, comment requested by sonar
	}

	@Override
	public RefactoringStatus isValid(IProgressMonitor pm) throws CoreException {
		return RefactoringStatus.create(Status.OK_STATUS);
	}

	@Override
	public Change perform(IProgressMonitor pm) throws CoreException {
		try {
			Files.delete(this.file.toPath());
		} catch (IOException e) {
			LanguageServerPlugin.logError(e);
			throw new CoreException(new Status(IStatus.ERROR, LanguageServerPlugin.PLUGIN_ID, e.getMessage(), e));
		}
		return null;
	}

	@Override
	public Object getModifiedElement() {
		return this.file;
	}

}
