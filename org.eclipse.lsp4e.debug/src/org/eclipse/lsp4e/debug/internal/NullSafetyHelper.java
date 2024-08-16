/**
 * Copyright (c) 2022 Sebastian Thomschke and others.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * Sebastian Thomschke - initial implementation
 */
package org.eclipse.lsp4e.debug.internal;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Copied from lsp4e.plugin
 */
public final class NullSafetyHelper {

	/**
	 * Casts a non-null value marked as {@link Nullable} to {@link NonNull}.
	 * <p>
	 * Only use if you are sure the value is non-null but annotation-based null
	 * analysis was not able to determine it.
	 * <p>
	 * This method is not meant for non-null input validation.
	 *
	 * @return the input value cast to {@link NonNull}
	 * @throws IllegalStateException if the given value is {@code null}
	 */
	public static <T> @NonNull T castNonNull(final @Nullable T value) {
		if (value == null)
			throw new IllegalStateException("Unexpected null value present!"); //$NON-NLS-1$
		return value;
	}

	/**
	 * Casts the elements of given array to {@link NonNull} without any validation.
	 * <p>
	 * Only use if you are sure the value is non-null but annotation-based null
	 * analysis was not able to determine it.
	 *
	 * @return the input value cast to {@link NonNull}
	 */
	@SuppressWarnings("null")
	public static <T> @NonNull T castNonNullUnsafe(final T value) {
		return value;
	}

	/**
	 * Allows the temporary assignment of {@code null} to a {@code @NonNull} field
	 * during declaration, deferring proper initialization until a later point when
	 * the actual non-null value is available.
	 *
	 * <p>
	 * This method is useful when a field must be initialized later but cannot be
	 * left unassigned at the point of declaration (e.g. when a value is provided by
	 * a later setup step).
	 *
	 * <p>
	 * <strong>Note:</strong> The field must be assigned a non-null value before it
	 * is accessed to prevent {@link NullPointerException}s.
	 */
	@SuppressWarnings("null")
	public static <T> @NonNull T lateNonNull() {
		return (@NonNull T) null;
	}

	private NullSafetyHelper() {
	}
}
