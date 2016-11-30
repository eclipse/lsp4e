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
package org.eclipse.languageserver;

import java.util.Collections;
import java.util.Date;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.mylyn.commons.notifications.core.AbstractNotification;
import org.eclipse.mylyn.commons.notifications.ui.NotificationsUi;

@SuppressWarnings("restriction")
public class ServerMessageHandler {

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

	public static void logMessage(MessageParams params) {
		// TODO 
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

}
