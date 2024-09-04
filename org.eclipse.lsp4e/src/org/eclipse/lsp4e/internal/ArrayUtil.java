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

import static org.eclipse.lsp4e.internal.NullSafetyHelper.castNonNull;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;

import org.eclipse.jdt.annotation.Nullable;

public class ArrayUtil {

	/** reusable empty byte array */
	public static final byte[] NO_BYTES = new byte[0];

	/** reusable empty char array */
	public static final char[] NO_CHARS = new char[0];

	/** reusable empty {@link Object} array */
	public static final Object[] NO_OBJECTS = new Object[0];

	/** reusable empty {@link String} array */
	public static final String[] NO_STRINGS = new String[0];

	/**
	 * @return true if any element of the given array is matched by the filter
	 */
	public static <T, X extends Throwable> boolean anyMatch(final T @Nullable [] array,
			final ThrowingPredicate<? super T, X> filter) throws X {
		if (array == null || array.length == 0)
			return false;
		for (final T e : array) {
			if (filter.testOrThrow(e))
				return true;
		}
		return false;
	}

	/**
	 * @return a modifiable {@link ArrayList} with the given elements
	 */
	@SafeVarargs
	public static <T> ArrayList<T> asArrayList(final T... array) {
		final var list = new ArrayList<T>(array.length);
		Collections.addAll(list, array);
		return list;
	}

	/**
	 * @return a modifiable {@link HashSet} with the given elements
	 */
	@SafeVarargs
	public static <T> HashSet<T> asHashSet(final T... array) {
		final var set = new HashSet<T>();
		Collections.addAll(set, array);
		return set;
	}

	public static <T> boolean contains(final T @Nullable [] array, final T searchFor) {
		if (array == null || array.length == 0)
			return false;
		for (final T e : array) {
			if (Objects.equals(e, searchFor))
				return true;
		}
		return false;
	}

	/**
	 * @return a new array containing only elements that match the predicate
	 */
	public static <T, X extends Throwable> T[] filter(final T[] array, final ThrowingPredicate<? super T, X> filter)
			throws X {
		if (array.length == 0)
			return array;

		final var result = new ArrayList<T>();
		for (final T item : array)
			if (filter.testOrThrow(item)) {
				result.add(item);
			}

		@SuppressWarnings("unchecked")
		final T[] resultArray = (T[]) Array.newInstance(getComponentType(array), result.size());
		return result.toArray(resultArray);
	}

	/**
	 * @return returns the first element of the given array or null if the array is
	 *         empty
	 */
	@SafeVarargs
	public static @Nullable <T> T findFirst(final T @Nullable... array) {
		if (array == null || array.length == 0)
			return null;
		return array[0];
	}

	/**
	 * @return returns the first element of the given array matching the filter or null if the array is
	 *         empty or no match was found
	 */
	public static @Nullable <T, X extends Throwable> T findFirstMatching(final T @Nullable [] array,
			final ThrowingPredicate<T, X> filter) throws X {
		if (array == null || array.length == 0)
			return null;
		for (final T e : array) {
			if (filter.testOrThrow(e))
				return e;
		}
		return null;
	}

	/**
	 * Iterates over the given array and applies the specified consumer to each
	 * element.
	 * <p>
	 * Does nothing if array is null/empty or consumer is null.
	 */
	public static <T, X extends Throwable> void forEach(final T @Nullable [] array,
			final @Nullable ThrowingConsumer<T, X> consumer) throws X {
		if (array == null || array.length == 0 || consumer == null)
			return;

		for (final T element : array) {
			consumer.acceptOrThrow(element);
		}
	}

	/**
	 * Returns the component type of the specified array.
	 */
	@SuppressWarnings("unchecked")
	public static <T> Class<T> getComponentType(final T[] array) {
		return (Class<T>) castNonNull(array.getClass().getComponentType());
	}

	private ArrayUtil() {
	}
}
