/*******************************************************************************
 * Copyright (c) 2016, 2022 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Miro Spoenemann (TypeFox) - extracted to separate file
 *  Rubén Porras Campo (Avaloq Evolution AG) - progress creation/notification implementation
 *******************************************************************************/
package org.eclipse.lsp4e;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.ProgressParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.WorkDoneProgressCreateParams;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;

public class LanguageClientImpl implements LanguageClient {

	private Consumer<PublishDiagnosticsParams> diagnosticConsumer;
	private final LSPProgressManager progressManager = new LSPProgressManager();

	private LanguageServer server;
	private LanguageServerWrapper wrapper;

	public final void connect(LanguageServer server, LanguageServerWrapper wrapper) {
		this.server = server;
		this.wrapper = wrapper;
		progressManager.connect(server);
	}

	protected void setDiagnosticsConsumer(@NonNull Consumer<PublishDiagnosticsParams> diagnosticConsumer) {
		this.diagnosticConsumer = diagnosticConsumer;
	}

	protected final LanguageServer getLanguageServer() {
		return server;
	}

	@Override
	public void telemetryEvent(Object object) {
		// TODO
	}

	@Override
	public final CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
		return ServerMessageHandler.showMessageRequest(wrapper, requestParams);
	}

	@Override
	public final void showMessage(MessageParams messageParams) {
		ServerMessageHandler.showMessage(wrapper.serverDefinition.label, messageParams);
	}

	@Override
	public final void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
		diagnosticConsumer.accept(diagnostics);
	}

	@Override
	public final void logMessage(MessageParams message) {
		CompletableFuture.runAsync(() -> ServerMessageHandler.logMessage(wrapper, message));
	}

	@SuppressWarnings("null")
	@Override
	public CompletableFuture<Void> createProgress(final WorkDoneProgressCreateParams params) {
		return progressManager.createProgress(params);
	}

	@SuppressWarnings("null")
	@Override
	public void notifyProgress(final ProgressParams params) {
		progressManager.notifyProgress(params);
	}

	@Override
	public final CompletableFuture<ApplyWorkspaceEditResponse> applyEdit(ApplyWorkspaceEditParams params) {
		return CompletableFuture.supplyAsync(() -> {
			Job job = new Job(Messages.serverEdit) {
				@Override
				public IStatus run(IProgressMonitor monitor) {
					LSPEclipseUtils.applyWorkspaceEdit(params.getEdit(), params.getLabel());
					return Status.OK_STATUS;
				}
			};
			job.schedule();
			try {
				job.join();
				return new ApplyWorkspaceEditResponse(true);
			} catch (InterruptedException e) {
				LanguageServerPlugin.logError(e);
				Thread.currentThread().interrupt();
				return new ApplyWorkspaceEditResponse(false);
			}
		});
	}

	@Override
	public CompletableFuture<Void> registerCapability(RegistrationParams params) {
		return CompletableFuture.runAsync(() -> wrapper.registerCapability(params));
	}

	@Override
	public CompletableFuture<Void> unregisterCapability(UnregistrationParams params) {
		return CompletableFuture.runAsync(() -> wrapper.unregisterCapability(params));
	}

	@Override
	public CompletableFuture<List<WorkspaceFolder>> workspaceFolders() {
		return CompletableFuture.completedFuture(Arrays.stream(ResourcesPlugin.getWorkspace().getRoot().getProjects()) //
			.filter(IProject::isAccessible) //
			.map(LSPEclipseUtils::toWorkspaceFolder) //
			.collect(Collectors.toList()));
	}
}
