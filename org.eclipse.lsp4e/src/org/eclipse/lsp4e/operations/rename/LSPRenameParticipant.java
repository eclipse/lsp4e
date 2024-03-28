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

import java.nio.file.FileSystems;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.internal.Pair;
import org.eclipse.lsp4j.FileOperationOptions;
import org.eclipse.lsp4j.FileRename;
import org.eclipse.lsp4j.RenameFilesParams;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.RenameParticipant;

public class LSPRenameParticipant extends RenameParticipant {

	private IFile file;
	private List<Pair<LanguageServerWrapper, LanguageServer>> servers;

	@SuppressWarnings("null")
	static List<Pair<LanguageServerWrapper, LanguageServer>> collectServers(IFile file)
	{
		return LanguageServers.forProject(file.getProject()).withFilter(f -> {
			if (f.getWorkspace() == null || f.getWorkspace().getFileOperations() == null) {
				return false;
			}
			FileOperationOptions willRename = f.getWorkspace().getFileOperations().getWillRename();
			if (willRename == null) {
				return false;
			}
			if (willRename.getFilters() == null || willRename.getFilters().isEmpty()) {
				return true;
			}
			return willRename.getFilters().stream().anyMatch(filter -> {
				return FileSystems.getDefault().getPathMatcher("glob:" + filter.getPattern().getGlob()) //$NON-NLS-1$
						.matches(FileSystems.getDefault().getPath(file.getRawLocation().toOSString()));
			});

		}).collectAll((w, ls) -> CompletableFuture.completedFuture(ls).thenApply(r -> Pair.of(w,r))).join();
	}


	@Override
	protected boolean initialize(Object element) {
		if (element instanceof IFile) {
			file = (IFile) element;
			this.servers = collectServers(file);

			return !servers.isEmpty();
		}
		return false;
	}

	@Override
	public String getName() {
		return "LSP4E Rename";
	}

	@Override
	public RefactoringStatus checkConditions(IProgressMonitor pm, CheckConditionsContext context)
			throws OperationCanceledException {

		return new RefactoringStatus();
	}


	static Change buildChange(List<Pair<LanguageServerWrapper, LanguageServer>> servers, RenameFilesParams params, String name)
	{
		List<CompositeChange> changes = servers.stream()
				.map(p -> p.getSecond().getWorkspaceService().willRenameFiles(params).thenApply(edits -> {
					if (edits == null) {
						return new CompositeChange(name);
					}

					return LSPEclipseUtils.toCompositeChange(edits, p.getFirst().serverDefinition.label);

				})).map(CompletableFuture::join).filter(c -> c != null && c.getChildren().length > 0).toList();
		if (changes.isEmpty()) {
			return null;
		}
		if (changes.size() == 1) {
			return changes.get(0);
		}
		return new CompositeChange(name, changes.toArray(new CompositeChange[0]));
	}

	@Override
	public Change createPreChange(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		var params = new RenameFilesParams();
		params.getFiles().add(new FileRename(LSPEclipseUtils.toUri(file).toString(), LSPEclipseUtils
				.toUri(file.getParent().getRawLocation().append(getArguments().getNewName())).toString()));


		return buildChange(servers, params, getName());
	}


	@Override
	public Change createChange(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		return null;
	}

}
