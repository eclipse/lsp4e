/*******************************************************************************
 * Copyright (c) 2024 Sebastian Thomschke and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * Sebastian Thomschke - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.internal;

import java.util.function.Consumer;

/**
 * @author <a href="https://sebthom.de/">Sebastian Thomschke</a>
 */
@FunctionalInterface
public interface ThrowingConsumer<I, X extends Throwable> extends Consumer<I> {

	static <T> ThrowingConsumer<T, RuntimeException> from(final Consumer<T> consumer) {
		return consumer::accept;
	}

	@Override
	default void accept(final I elem) {
		try {
			acceptOrThrow(elem);
		} catch (final RuntimeException rex) {
			throw rex;
		} catch (final Throwable t) {
			throw new RuntimeException(t);
		}
	}

	void acceptOrThrow(I elem) throws X;
}
