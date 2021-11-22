/*******************************************************************************
 * Copyright (c) 2016, 2021 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e;

import java.util.concurrent.CompletableFuture;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.notifications.AbstractNotificationPopup;
import org.eclipse.lsp4e.ui.LSPImages;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.ISharedImages;

public class ServerMessageHandler {

	private ServerMessageHandler() {
		// this class shouldn't be instantiated
	}

	private static class LSPNotification extends AbstractNotificationPopup {

		private final String label;
		private final MessageParams messageParams;

		public LSPNotification(String label, MessageParams messageParams) {
			super(Display.getCurrent());
			setParentShell(UI.getActiveShell());
			this.label = label;
			this.messageParams = messageParams;
		}

		@Override
		public String getPopupShellTitle() {
			return label;
		}

		@Override
		protected void createContentArea(Composite parent) {
			Label label = new Label(parent, SWT.WRAP);
			label.setText(messageParams.getMessage());
		}

		@Override
		public Image getPopupShellImage(int maximumHeight) {
			switch (messageParams.getType()) {
			case Error:
				return LSPImages.getSharedImage(ISharedImages.IMG_OBJS_ERROR_TSK);
			case Warning:
				return LSPImages.getSharedImage(ISharedImages.IMG_OBJS_WARN_TSK);
			case Info:
				return LSPImages.getSharedImage(ISharedImages.IMG_OBJS_INFO_TSK);
			default:
				return null;
			}
		}

	}

	public static void logMessage(LanguageServerWrapper wrapper, MessageParams params) {
		StringBuilder log = new StringBuilder();
		log.append('[');
		log.append(params.getType().toString());
		log.append(']');
		log.append('\t');
		log.append(params.getMessage());

		switch(params.getType()) {
		case Error:
			LanguageServerPlugin.logError(log.toString(), null);
			break;
		case Warning:
			LanguageServerPlugin.logWarning(log.toString(), null);
			break;
		default:
			LanguageServerPlugin.logInfo(log.toString());
		}
	}

	public static void showMessage(String title, MessageParams params) {
		Display.getDefault().asyncExec(() -> {
			AbstractNotificationPopup notification = new LSPNotification(String.format("LSP (%s)", title), //$NON-NLS-1$
					params);
			notification.open();
		});
	}

	public static CompletableFuture<MessageActionItem> showMessageRequest(LanguageServerWrapper wrapper, ShowMessageRequestParams params) {
		String options[] = params.getActions().stream().map(MessageActionItem::getTitle).toArray(String[]::new);
		CompletableFuture<MessageActionItem> future = new CompletableFuture<>();

		Display.getDefault().asyncExec(() -> {
			Shell shell = new Shell(Display.getCurrent());
			MessageDialog dialog = new MessageDialog(shell, wrapper.serverDefinition.label,
					null, params.getMessage(), MessageDialog.INFORMATION, 0, options);
			MessageActionItem result = new MessageActionItem();
			int dialogResult = dialog.open();
			if (dialogResult != SWT.DEFAULT) { // the dialog was not dismissed without pressing a button (ESC key, close box, etc.)
				result.setTitle(options[dialogResult]);
			}
			// according to https://github.com/Microsoft/language-server-protocol/issues/230
			// the right thing to do is to return the res
			future.complete(result);
		});
		return future;
	}

}
