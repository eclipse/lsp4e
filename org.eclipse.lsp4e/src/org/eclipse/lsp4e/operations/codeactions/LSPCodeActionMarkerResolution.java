/*******************************************************************************
 * Copyright (c) 2016-2022 Red Hat Inc. and others.
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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.AbstractInformationControlManager;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.ITextViewerExtension2;
import org.eclipse.jface.text.TextViewer;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.quickassist.IQuickAssistAssistant;
import org.eclipse.jface.text.quickassist.QuickAssistAssistant;
import org.eclipse.jface.text.source.ISourceViewerExtension3;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServersRegistry;
import org.eclipse.lsp4e.LanguageServersRegistry.LanguageServerDefinition;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.operations.diagnostics.LSPDiagnosticsToMarkers;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
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
		Object att = null;
		try {
			att = marker.getAttribute(LSP_REMEDIATION);
			if (att == null) {
				checkMarkerResoultion(marker);
				att = marker.getAttribute(LSP_REMEDIATION);
			}
		} catch (IOException | CoreException | ExecutionException e) {
			LanguageServerPlugin.logError(e);
			return new IMarkerResolution[0];
		} catch (InterruptedException e) {
			LanguageServerPlugin.logError(e);
			Thread.currentThread().interrupt();
			return new IMarkerResolution[0];
		}
		if (att == COMPUTING) {
			return new IMarkerResolution[] { COMPUTING };
		} else if (att == null) {
			return new IMarkerResolution[0];
		}
		final var commands = (List<Either<Command, CodeAction>>) att;
		final var res = new ArrayList<IMarkerResolution>(commands.size());
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

	private void checkMarkerResoultion(IMarker marker) throws IOException, CoreException, InterruptedException, ExecutionException {
		IResource res = marker.getResource();
		if (res != null && res.getType() == IResource.FILE) {
			final var file = (IFile) res;
			Object[] attributes = marker.getAttributes(new String[]{LSPDiagnosticsToMarkers.LANGUAGE_SERVER_ID, LSPDiagnosticsToMarkers.LSP_DIAGNOSTIC});
			final var languageServerId = (String) attributes[0];
			final var futures = new ArrayList<CompletableFuture<?>>();
			final var diagnostic = (Diagnostic) attributes[1];
			for (CompletableFuture<LanguageServer> lsf : getLanguageServerFutures(file, languageServerId)) {
				marker.setAttribute(LSP_REMEDIATION, COMPUTING);
				final var context = new CodeActionContext(Collections.singletonList(diagnostic));
				final var params = new CodeActionParams();
				params.setContext(context);
				params.setTextDocument(LSPEclipseUtils.toTextDocumentIdentifier(res));
				params.setRange(diagnostic.getRange());
				CompletableFuture<List<Either<Command, CodeAction>>> codeAction = lsf
						.thenComposeAsync(ls -> ls.getTextDocumentService().codeAction(params));
				futures.add(codeAction);
				codeAction.thenAcceptAsync(actions -> {
					try {
						marker.setAttribute(LSP_REMEDIATION, actions);
						Display display = UI.getDisplay();
						display.asyncExec(() -> {
							ITextViewer textViewer = UI.getActiveTextViewer();
							if (textViewer != null) {
								// Do not re-invoke hover right away as hover may not be showing at all yet
								display.timerExec(500, () -> reinvokeQuickfixProposalsIfNecessary(textViewer));
							}
						});
					} catch (CoreException e) {
						LanguageServerPlugin.logError(e);
					}
				});
			}
			// wait a bit to avoid showing too much "Computing" without looking like a freeze
			try {
				CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])).get(300, TimeUnit.MILLISECONDS);
			} catch (TimeoutException e) {
				LanguageServerPlugin.logWarning("Could get code actions due to timeout after 300 miliseconds in `textDocument/codeAction`", e); //$NON-NLS-1$
			}
		}
	}

	private List<CompletableFuture<LanguageServer>> getLanguageServerFutures(@NonNull IFile file, @Nullable String languageServerId)
			throws IOException {
		List<CompletableFuture<LanguageServer>> languageServerFutures = new ArrayList<>();
		if (languageServerId != null) { // try to use same LS as the one that created the marker
			LanguageServerDefinition definition = LanguageServersRegistry.getInstance().getDefinition(languageServerId);
			if (definition != null) {
				CompletableFuture<LanguageServer> serverFuture = LanguageServiceAccessor.getInitializedLanguageServer(
						file, definition, LSPCodeActionMarkerResolution::providesCodeActions);
				if (serverFuture != null) {
					languageServerFutures.add(serverFuture);
				}
			}
		}
		if (languageServerFutures.isEmpty()) { // if it's not there, try any other server
			languageServerFutures.addAll(LanguageServiceAccessor.getInitializedLanguageServers(file,
					LSPCodeActionMarkerResolution::providesCodeActions));
		}
		return languageServerFutures;
	}

	private void reinvokeQuickfixProposalsIfNecessary(ITextViewer textViewer) {
		try {
			// Quick assist proposals popup case
			if (textViewer instanceof final ISourceViewerExtension3 textViewer3) {
				IQuickAssistAssistant quickAssistant = textViewer3.getQuickAssistAssistant();
				if (quickAssistant != null) {
					Field f = QuickAssistAssistant.class.getDeclaredField("fQuickAssistAssistantImpl"); //$NON-NLS-1$
					f.setAccessible(true);
					final var ca = (ContentAssistant) f.get(quickAssistant);
					Method m = ContentAssistant.class.getDeclaredMethod("isProposalPopupActive"); //$NON-NLS-1$
					m.setAccessible(true);
					boolean isProposalPopupActive = (Boolean) m.invoke(ca);
					if (isProposalPopupActive) {
						quickAssistant.showPossibleQuickAssists();
					}
				}
			}
			// Hover case
			if (textViewer instanceof final ITextViewerExtension2 textViewer2) {
				ITextHover hover = textViewer2.getCurrentTextHover();
				boolean hoverShowing = hover != null;
				if (hoverShowing) {
					Field f = TextViewer.class.getDeclaredField("fTextHoverManager"); //$NON-NLS-1$
					f.setAccessible(true);
					final var manager = (AbstractInformationControlManager) f.get(textViewer);
					if (manager != null) {
						manager.showInformation();
					}
				}
			}
		} catch (Exception e) {
			LanguageServerPlugin.logError(e);
		}
	}

	static boolean providesCodeActions(final ServerCapabilities capabilities) {
		return capabilities != null && LSPEclipseUtils.hasCapability(capabilities.getCodeActionProvider());
	}

	@Override
	public boolean hasResolutions(IMarker marker) {
		try {
			Object remediation = marker.getAttribute(LSP_REMEDIATION);
			if (remediation == null) {
				checkMarkerResoultion(marker);
				remediation = marker.getAttribute(LSP_REMEDIATION);
			}
			return remediation == COMPUTING || (remediation instanceof Collection<?> collection && !collection.isEmpty());
		} catch (Exception ex) {
			LanguageServerPlugin.logError(ex);
		}
		return false;
	}
}
