/*******************************************************************************
 * Copyright (c) 2022 Vegard IT GmbH and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Sebastian Thomschke (Vegard IT GmbH) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.format;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.e4.core.commands.ExpressionContext;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServersRegistry;
import org.eclipse.lsp4e.operations.format.LSPFormatter.VersionedFormatRequest;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.texteditor.DocumentProviderRegistry;
import org.eclipse.ui.texteditor.IDocumentProvider;

public class LSPFormatFilesHandler extends AbstractHandler {

	protected final LSPFormatter formatter = new LSPFormatter();

	@Override
	public Object execute(final ExecutionEvent event) throws ExecutionException {
		if (event.getApplicationContext() instanceof final ExpressionContext ctx) {
			final var job = Job.create("Formatting selected files...", monitor -> { //$NON-NLS-1$
				final var selectedFiles = getSelectedFiles(ctx);
				final var subMonitor = SubMonitor.convert(monitor, selectedFiles.size());
				for (final IFile file : selectedFiles) {
					subMonitor.worked(1);
					formatFile(file, monitor);
					subMonitor.done();
				}
			});
			job.setPriority(Job.BUILD);
			job.schedule();
		}
		return null;
	}

	protected void formatFile(final @NonNull IFile file, final IProgressMonitor monitor) {
		if (!file.exists() || !LanguageServersRegistry.getInstance().canUseLanguageServer(file))
			return;

		final var docProvider = getDocumentProvider(file);
		try {
			docProvider.connect(file);
			final IDocument doc = docProvider.getDocument(file);
			if (doc == null)
				return;

			monitor.setTaskName("Formatting " + file.getFullPath() + "..."); //$NON-NLS-1$//$NON-NLS-2$
			final VersionedFormatRequest formatRequest = formatter.versionedRequestFormatting(doc,
					new TextSelection(0, 0));

			List<? extends TextEdit> formatEdits = formatRequest.edits().get();
			if (formatEdits.isEmpty())
				return;

			docProvider.aboutToChange(doc);
			UI.getDisplay().syncExec(() -> formatter.applyEdits(doc, formatEdits, formatRequest.version()));
			docProvider.changed(doc);
			docProvider.saveDocument(monitor, file, doc, true);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			LanguageServerPlugin.logError(ex);
		} catch (final Exception ex) {
			LanguageServerPlugin.logError(ex);
		} finally {
			docProvider.disconnect(file);
		}
	}

	protected IDocumentProvider getDocumentProvider(IFile file) {
		return DocumentProviderRegistry.getDefault().getDocumentProvider(new FileEditorInput(file));
	}

	protected Set<@NonNull IFile> getSelectedFiles(final ExpressionContext ctx) {
		final var selection = getSelection(ctx);
		if (selection.isEmpty())
			return Collections.emptySet();

		final var files = new HashSet<@NonNull IFile>();
		for (final var item : selection) {
			try {
				if (item instanceof final IResource resource) {
					if (resource instanceof final IFile file) {
						files.add(file);
					} else if (resource instanceof IProject || resource instanceof IFolder) {
						resource.accept(childResource -> {
							// ignore linked or virtual children to prevent accidental formatting of
							// unrelated
							// resources (i.e. resources outside the project) during bulk format operations
							if (childResource.isLinked() || childResource.isVirtual())
								return false;
							if (childResource instanceof final IFile file) {
								files.add(file);
								return false;
							}
							return childResource instanceof final IFolder;
						});
					}
				}
			} catch (CoreException ex) {
				LanguageServerPlugin.logError(ex);
			}
		}
		return files;
	}

	protected Collection<?> getSelection(final ExpressionContext ctx) {
		final Object defVariable = ctx.getDefaultVariable();
		if (defVariable instanceof Collection<?> defVariableAsCollection) {
			return defVariableAsCollection;
		}
		return List.of(defVariable);
	}

	@Override
	public void setEnabled(final Object evaluationContext) {
		if (evaluationContext instanceof ExpressionContext ctx) {
			final var selection = getSelection(ctx);
			if (selection.isEmpty()) {
				setBaseEnabled(false);
				return;
			}

			// if the selection contains more than one entry or a folder, enable the handler
			if (selection.size() > 1 || selection.stream().anyMatch(IFolder.class::isInstance)) {
				setBaseEnabled(true);
				return;
			}

			// check if the selection is an IFile that has a known LS
			if (selection.iterator().next() instanceof IFile file) {
				setBaseEnabled(LanguageServersRegistry.getInstance().canUseLanguageServer(file));
				return;
			}
		}
		setBaseEnabled(false);
	}
}
