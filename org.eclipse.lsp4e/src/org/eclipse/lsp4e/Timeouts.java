/*******************************************************************************
 * Copyright (c) 2022 Avaloq Evolution AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Rubén Porras Campo (Avaloq Evolution AG) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.preference.IPreferenceStore;

/**
 * A class for managing configurable timeouts.
 *
 */
final public class Timeouts {
	private static final String WILL_SAVE_WAIT_UNTIL_TIMEOUT__KEY = "timeout.willSaveWaitUntil"; //$NON-NLS-1$

	/**
	 * Converts a language server ID to the preference ID to define a timeout for
	 * willSaveWaitUntil
	 *
	 * @param serverId
	 *            the language server ID
	 * @return language server's preference ID to define a timeout for
	 *         willSaveWaitUntil
	 */
	public static @NonNull String lsToWillSaveWaitUntilTimeoutKey(String serverId) {
		return serverId + '.' + WILL_SAVE_WAIT_UNTIL_TIMEOUT__KEY;
	}

	/**
	 * Returns the timeout for willSaveWaitUntil saved in the preference store, or
	 * the default one, if none is set.
	 *
	 * @param store
	 *            the preference store
	 * @param serverId
	 *            the language server ID
	 * @return language server's timeout for willSaveWaitUntil
	 */
	public static int lsToWillSaveWaitUntilTimeout(@NonNull IPreferenceStore store, @NonNull String serverId) {
		int defaultWillSaveWaitUntilTimeoutInSeconds = 5;
		int willSaveWaitUntilTimeout = store.getInt(Timeouts.lsToWillSaveWaitUntilTimeoutKey(serverId));
		return willSaveWaitUntilTimeout != 0 ? willSaveWaitUntilTimeout : defaultWillSaveWaitUntilTimeoutInSeconds;
	}

}
