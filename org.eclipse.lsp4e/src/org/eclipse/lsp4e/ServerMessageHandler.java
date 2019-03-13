/*******************************************************************************
 * Copyright (c) 2016 Rogue Wave Software Inc. and others.
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

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.CompletableFuture;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.mylyn.commons.notifications.core.AbstractNotification;
import org.eclipse.mylyn.commons.notifications.ui.AbstractUiNotification;
import org.eclipse.mylyn.commons.notifications.ui.NotificationsUi;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;

@SuppressWarnings("restriction")
public class ServerMessageHandler {

	private ServerMessageHandler() {
		// this class shouldn't be instantiated
	}

	private static final String NAME_PATTERN = "%s (%s)"; //$NON-NLS-1$

	private static class LSPNotification extends AbstractUiNotification {

		private final String label;
		private final MessageParams messageParams;

		public LSPNotification(String label, MessageParams messageParams) {
			super("lsp.notification"); //$NON-NLS-1$
			this.label = label;
			this.messageParams = messageParams;
		}

		@Override
		public String getLabel() {
			return label;
		}

		@Override
		public String getDescription() {
			return messageParams.getMessage();
		}

		@Override
		public Date getDate() {
			return new Date();
		}

		@Override
		public <T> T getAdapter(Class<T> adapter) {
			return null;
		}

		@Override
		public Image getNotificationImage() {
			return null;
		}

		@Override
		public Image getNotificationKindImage() {
			switch (messageParams.getType()) {
			case Error:
				return PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJS_ERROR_TSK);
			case Warning:
				return PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJS_WARN_TSK);
			case Info:
				return PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJS_INFO_TSK);
			default:
				return null;
			}
		}

		@Override
		public void open() {
			Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
			switch (messageParams.getType()) {
			case Error:
				MessageDialog.openError(shell, label, messageParams.getMessage());
				break;
			case Warning:
				MessageDialog.openWarning(shell, label, messageParams.getMessage());
				break;
			case Info:
				MessageDialog.openInformation(shell, label, messageParams.getMessage());
				break;
			default:
				MessageDialog.open(MessageDialog.NONE, shell, label, messageParams.getMessage(), SWT.None);
			}
		}

	}

	public static void logMessage(LanguageServerWrapper wrapper, MessageParams params) {
		MessageConsole console = findConsole(
				String.format(NAME_PATTERN, wrapper.serverDefinition.label, wrapper.toString()));
		if (console != null) {
			StringBuilder log = new StringBuilder();
			log.append('[');
			log.append(params.getType().toString());
			log.append(']');
			log.append('\t');
			log.append(params.getMessage());
			MessageConsoleStream stream = console.newMessageStream();
			stream.println(log.toString());
			try {
				stream.close();
			} catch (IOException e) {
				LanguageServerPlugin.logError(e);
			}
		}
	}

	public static void showMessage(String title, MessageParams params) {
		AbstractNotification notification = new LSPNotification(String.format("LSP (%s)", title), //$NON-NLS-1$
				params);
		NotificationsUi.getService().notify(Collections.singletonList(notification));
	}

	public static CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams params) {
		// TODO
		return null;
	}

	private static MessageConsole findConsole(String name) {
		ConsolePlugin plugin = ConsolePlugin.getDefault();
		IConsoleManager conMan = plugin.getConsoleManager();
		IConsole[] existing = conMan.getConsoles();
		for (int i = 0; i < existing.length; i++) {
			if (name.equals(existing[i].getName())) {
				return (MessageConsole) existing[i];
			}
		}
		// no console found, so create a new one
		MessageConsole myConsole = new MessageConsole(name, null);
		conMan.addConsoles(new IConsole[] { myConsole });
		return myConsole;
	}

}
