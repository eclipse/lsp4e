/*******************************************************************************
 * Copyright (c) 2016, 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Miro Spoenemann (TypeFox) - extracted to separate file
 *******************************************************************************/
package org.eclipse.lsp4e;

import java.util.concurrent.CompletableFuture;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.lsp4e.operations.diagnostics.LSPDiagnosticsToMarkers;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;

public class LanguageClientImpl implements LanguageClient {

	private LSPDiagnosticsToMarkers diagnosticHandler;

	private LanguageServer server;
	private ProjectSpecificLanguageServerWrapper wrapper;

	public final void connect(LanguageServer server, ProjectSpecificLanguageServerWrapper wrapper) {
		this.server = server;
		this.wrapper = wrapper;
		this.diagnosticHandler = new LSPDiagnosticsToMarkers(wrapper.project, wrapper.serverDefinition.id);
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
		return ServerMessageHandler.showMessageRequest(requestParams);
	}

	@Override
	public final void showMessage(MessageParams messageParams) {
		ServerMessageHandler.showMessage(messageParams);
	}

	@Override
	public final void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
		this.diagnosticHandler.accept(diagnostics);
	}

	@Override
	public final void logMessage(MessageParams message) {
		ServerMessageHandler.logMessage(wrapper.project, wrapper.serverDefinition.label, message);
	}

	@Override
	public final CompletableFuture<ApplyWorkspaceEditResponse> applyEdit(ApplyWorkspaceEditParams params) {
		return CompletableFuture.supplyAsync(() -> {
			Job job = new Job(Messages.serverEdit) {
				@Override
				public IStatus run(IProgressMonitor monitor) {
					LSPEclipseUtils.applyWorkspaceEdit(params.getEdit());
					return Status.OK_STATUS;
				}
			};
			job.schedule();
			try {
				job.join();
				return new ApplyWorkspaceEditResponse(true);
			} catch (InterruptedException e) {
				LanguageServerPlugin.logError(e);
				return new ApplyWorkspaceEditResponse(Boolean.FALSE);
			}
		});
	}
}
