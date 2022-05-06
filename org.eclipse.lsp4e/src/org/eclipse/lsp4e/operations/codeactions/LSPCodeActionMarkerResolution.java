/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.codeactions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServersRegistry;
import org.eclipse.lsp4e.LanguageServersRegistry.LanguageServerDefinition;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.operations.diagnostics.LSPDiagnosticsToMarkers;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionOptions;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.IMarkerResolution;
import org.eclipse.ui.IMarkerResolution2;
import org.eclipse.ui.IMarkerResolutionGenerator2;
import org.eclipse.ui.internal.progress.ProgressInfoItem;

public class LSPCodeActionMarkerResolution implements IMarkerResolutionGenerator2 {

	private static final String LSP_REMEDIATION = "lspCodeActions"; //$NON-NLS-1$

	private static final IMarkerResolution2 COMPUTING = new IMarkerResolution2() {

		@Override
		public void run(IMarker marker) {
			// join on Future?
		}

		@Override
		public String getLabel() {
			return Messages.computing;
		}

		@Override
		public Image getImage() {
			// load class so image is loaded
			return JFaceResources.getImage(ProgressInfoItem.class.getPackage().getName() + ".PROGRESS_DEFAULT"); //$NON-NLS-1$
		}

		@Override
		public String getDescription() {
			return Messages.computing;
		}
	};

	@Override
	public IMarkerResolution[] getResolutions(IMarker marker) {
		Object att;
		try {
			checkMarkerResoultion(marker);
			att = marker.getAttribute(LSP_REMEDIATION);
		} catch (IOException | CoreException | ExecutionException | TimeoutException e) {
			LanguageServerPlugin.logError(e);
			return new IMarkerResolution[0];
		} catch (InterruptedException e) {
			LanguageServerPlugin.logError(e);
			Thread.currentThread().interrupt();
			return new IMarkerResolution[0];
		}
		if (att == COMPUTING) {
			return new IMarkerResolution[] { COMPUTING };
		}
		List<Either<Command, CodeAction>> commands = (List<Either<Command, CodeAction>>) att;
		if (commands == null) {
			return new IMarkerResolution[0];
		}
		List<IMarkerResolution> res = new ArrayList<>(commands.size());
		for (Either<Command, CodeAction> command : commands) {
			if (command != null) {
				if (command.isLeft()) {
					res.add(new CommandMarkerResolution(command.getLeft()));
				} else {
					res.add(new CodeActionMarkerResolution(command.getRight()));
				}
			}
		}
		return res.toArray(new IMarkerResolution[res.size()]);
	}

	private void checkMarkerResoultion(IMarker marker) throws IOException, CoreException, InterruptedException, ExecutionException, TimeoutException {
		if (marker.getAttribute(LSP_REMEDIATION) == null) {
			IResource res = marker.getResource();
			if (res != null && res.getType() == IResource.FILE) {
				IFile file = (IFile)res;
				String languageServerId = marker.getAttribute(LSPDiagnosticsToMarkers.LANGUAGE_SERVER_ID, null);
				List<CompletableFuture<LanguageServer>> languageServerFutures = new ArrayList<>();
				if (languageServerId != null) { // try to use same LS as the one that created the marker
					LanguageServerDefinition definition = LanguageServersRegistry.getInstance().getDefinition(languageServerId);
					if (definition != null) {
						CompletableFuture<LanguageServer> serverFuture = LanguageServiceAccessor
								.getInitializedLanguageServer(file, definition,
										serverCapabilities -> serverCapabilities == null
												|| providesCodeActions(serverCapabilities));
						if (serverFuture != null) {
							languageServerFutures.add(serverFuture);
						}
					}
				}
				if (languageServerFutures.isEmpty()) { // if it's not there, try any other server
					languageServerFutures.addAll(LanguageServiceAccessor.getInitializedLanguageServers(file,
							capabilities -> {
								Either<Boolean, CodeActionOptions> codeActionProvider = capabilities
										.getCodeActionProvider();
								if (codeActionProvider == null) {
									return false;
								} else if (codeActionProvider.isLeft()) {
									return Boolean.TRUE.equals(codeActionProvider.getLeft());
								} else if (codeActionProvider.isRight()) {
									return true;
								}
								return false;
							}));
				}
				List<CompletableFuture<?>> futures = new ArrayList<>();
				for (CompletableFuture<LanguageServer> lsf : languageServerFutures) {
					marker.setAttribute(LSP_REMEDIATION, COMPUTING);
					Diagnostic diagnostic = (Diagnostic)marker.getAttribute(LSPDiagnosticsToMarkers.LSP_DIAGNOSTIC);
					CodeActionContext context = new CodeActionContext(Collections.singletonList(diagnostic));
					CodeActionParams params = new CodeActionParams();
					params.setContext(context);
					params.setTextDocument(new TextDocumentIdentifier(LSPEclipseUtils.toUri(marker.getResource()).toString()));
					params.setRange(diagnostic.getRange());
					CompletableFuture<List<Either<Command, CodeAction>>> codeAction = lsf
							.thenComposeAsync(ls -> ls.getTextDocumentService().codeAction(params));
					futures.add(codeAction);
					codeAction.thenAcceptAsync(actions -> {
						try {
							marker.setAttribute(LSP_REMEDIATION, actions);
						} catch (CoreException e) {
							LanguageServerPlugin.logError(e);
						}
					});
				}
				// wait a bit to avoid showing too much "Computing" without looking like a freeze
				CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])).get(300, TimeUnit.MILLISECONDS);
			}
		}
	}

	static boolean providesCodeActions(@NonNull ServerCapabilities serverCapabilities) {
		Either<Boolean, CodeActionOptions> codeActionProvider = serverCapabilities.getCodeActionProvider();
		if (codeActionProvider == null) {
			return false;
		}
		if (codeActionProvider.isLeft()) {
			return codeActionProvider.getLeft() != null && codeActionProvider.getLeft();
		}
		if (codeActionProvider.isRight()) {
			return codeActionProvider.getRight() != null;
		}
		return false;
	}

	@Override
	public boolean hasResolutions(IMarker marker) {
		try {
			checkMarkerResoultion(marker);
			Object remediation = marker.getAttribute(LSP_REMEDIATION);
			return remediation == COMPUTING || (remediation instanceof Collection && !((Collection<?>)remediation).isEmpty());
		} catch (Exception ex) {
			LanguageServerPlugin.logError(ex);
		}
		return false;
	}
}
