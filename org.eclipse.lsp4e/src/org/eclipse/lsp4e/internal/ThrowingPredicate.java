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

import java.util.function.Predicate;

@FunctionalInterface
public interface ThrowingPredicate<I, X extends Throwable> extends Predicate<I> {

	static <T> ThrowingPredicate<T, RuntimeException> from(final Predicate<T> predicate) {
		return predicate::test;
	}

	@Override
	default boolean test(final I elem) {
		try {
			return testOrThrow(elem);
		} catch (final RuntimeException rex) {
			throw rex;
		} catch (final Throwable t) {
			throw new RuntimeException(t);
		}
	}

	boolean testOrThrow(I elem) throws X;
}