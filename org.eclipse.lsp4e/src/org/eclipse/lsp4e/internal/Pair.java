/*******************************************************************************
 * Copyright (c) 2023 Avaloq Group AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Rubén Porras Campo (Avaloq Group AG) - Initial Implementation
 *******************************************************************************/
package org.eclipse.lsp4e.internal;

public record Pair<F, S>(F first, S second) {

	public static <F, S> Pair<F, S> of(final F first, final S second) {
		return new Pair<>(first, second);
	}

}
