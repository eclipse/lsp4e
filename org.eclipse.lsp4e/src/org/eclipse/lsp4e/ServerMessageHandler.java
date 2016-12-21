/*******************************************************************************
 * Copyright (c) 2016 Rogue Wave Software Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e;

import java.util.Collections;
import java.util.Date;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.resources.IProject;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.mylyn.commons.notifications.core.AbstractNotification;
import org.eclipse.mylyn.commons.notifications.ui.NotificationsUi;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.MessageConsole;

@SuppressWarnings("restriction")
public class ServerMessageHandler {

	private static final String NAME_PATTERN = "%s (%s)"; //$NON-NLS-1$

	private static class LSPNotification extends AbstractNotification {

		private String label;
		private String description;

		public LSPNotification(String label, String description) {
			super("lsp.notification"); //$NON-NLS-1$
			this.label = label;
			this.description = description;
		}

		@Override
		public String getLabel() {
			return label;
		}

		@Override
		public String getDescription() {
			return description;
		}

		@Override
		public Date getDate() {
			return new Date();
		}

		@Override
		public <T> T getAdapter(Class<T> adapter) {
			return null;
		}

	}

	public static void logMessage(IProject project, String serverLabel, MessageParams params) {
		MessageConsole console = findConsole(String.format(NAME_PATTERN, serverLabel, project.getName()));
		console.newMessageStream().println(String.format("[%s]\t%s", params.getType(), params.getMessage())); //$NON-NLS-1$
	}

	public static void showMessage(MessageParams params) {
		AbstractNotification notification = new LSPNotification(String.format("LSP (%s)", params.getType()), //$NON-NLS-1$
				params.getMessage());
		NotificationsUi.getService().notify(Collections.singletonList(notification));
	}

	public static CompletableFuture<Void> showMessageRequest(ShowMessageRequestParams params) {
		// TODO
		return null;
	}

	private static MessageConsole findConsole(String name) {
		ConsolePlugin plugin = ConsolePlugin.getDefault();
		IConsoleManager conMan = plugin.getConsoleManager();
		IConsole[] existing = conMan.getConsoles();
		for (int i = 0; i < existing.length; i++)
			if (name.equals(existing[i].getName())) {
				return (MessageConsole) existing[i];
			}
		// no console found, so create a new one
		MessageConsole myConsole = new MessageConsole(name, null);
		conMan.addConsoles(new IConsole[] { myConsole });
		return myConsole;
	}

}
