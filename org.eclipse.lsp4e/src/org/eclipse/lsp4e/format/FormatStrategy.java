/*******************************************************************************
 * Copyright (c) 2023 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   See git history
 *******************************************************************************/
package org.eclipse.lsp4e.format;

/**
 * Format strategy to be applied to a document.
 *
 */
public enum FormatStrategy {
	/**
	 * No formatting. Disables the formatting for the given document.
	 */
	NO_FORMAT,
	/**
	 * Format all lines
	 */
	ALL_LINES,
	/**
	 * Format edited lines
	 */
	EDITED_LINES;
}
