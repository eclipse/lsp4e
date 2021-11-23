/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Andrew Obuchowicz (Red Hat Inc.)
 *******************************************************************************/
package org.eclipse.lsp4e.operations.codeactions;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ContextInformation;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

public class CodeActionCompletionProposal implements ICompletionProposal {

	private CodeAction fcodeAction;
	private Command fcommand;
	private String fdisplayString;
	private final LSPDocumentInfo finfo;

	public CodeActionCompletionProposal(Either<Command, CodeAction> command, LSPDocumentInfo info) {
		this.finfo = info;
		if (command.isLeft()) {
			fcommand = command.getLeft();
			fdisplayString = fcommand.getTitle();
		} else if (command.isRight()) {
			fcodeAction = command.getRight();
			fdisplayString = fcodeAction.getTitle();
		}
	}

	@Override
	public void apply(IDocument document) {
		if (fcodeAction != null) {
			if (fcodeAction.getEdit() != null) {
				LSPEclipseUtils.applyWorkspaceEdit(fcodeAction.getEdit());
			}
			if (fcodeAction.getCommand() != null) {
				executeCommand(fcodeAction.getCommand());
			}

		} else if (fcommand != null) {
			executeCommand(fcommand);
		} else {
			// Should never get here
		}
	}

	@Override
	public Point getSelection(IDocument document) {
		return null;
	}

	@Override
	public String getAdditionalProposalInfo() {
		return ""; //$NON-NLS-1$
	}

	@Override
	public String getDisplayString() {
		return this.fdisplayString;
	}

	@Override
	public Image getImage() {
		return null;
	}

	@Override
	public IContextInformation getContextInformation() {
		return new ContextInformation("some context display string", "some information display string"); //$NON-NLS-1$//$NON-NLS-2$
	}

	private void executeCommand(Command command) {
		ServerCapabilities capabilities = this.finfo.getCapabilites();
		if (capabilities != null) {
			ExecuteCommandOptions provider = capabilities.getExecuteCommandProvider();
			if (provider != null && provider.getCommands().contains(command.getCommand())) {
				ExecuteCommandParams params = new ExecuteCommandParams();
				params.setCommand(command.getCommand());
				params.setArguments(command.getArguments());
				this.finfo.getInitializedLanguageClient()
						.thenAcceptAsync(ls -> ls.getWorkspaceService().executeCommand(params));
			}
		}
	}
}
