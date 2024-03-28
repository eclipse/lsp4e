/*******************************************************************************
 * Copyright (c) 2023 Dawid Pakuła and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Dawid Pakuła - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.rename;

import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.internal.Pair;
import org.eclipse.lsp4j.FileRename;
import org.eclipse.lsp4j.RenameFilesParams;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.MoveParticipant;

public class LSPMoveParticipant extends MoveParticipant {

	private IFile file;
	private List<Pair<LanguageServerWrapper, LanguageServer>> servers;

	@Override
	protected boolean initialize(Object element) {
		if (element instanceof IFile && getArguments().getDestination() instanceof IFolder) {
			file = (IFile) element;
			this.servers = LSPRenameParticipant.collectServers(file);

			return !servers.isEmpty();
		}
		return false;
	}


	@Override
	public String getName() {
		return "LSP4E Move";
	}

	@Override
	public RefactoringStatus checkConditions(IProgressMonitor pm, CheckConditionsContext context)
			throws OperationCanceledException {

		return new RefactoringStatus();
	}

	@Override
	public Change createPreChange(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		var params = new RenameFilesParams();
		params.getFiles().add(new FileRename(LSPEclipseUtils.toUri(file).toString(), LSPEclipseUtils
				.toUri(((IFolder) getArguments().getDestination()).getRawLocation().append(file.getName())).toString()));

		return LSPRenameParticipant.buildChange(servers, params, getName());
	}


	@Override
	public Change createChange(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		return null;
	}

}
