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

import java.util.stream.Collector;

public class MoreCollectors {

	/**
	 * @return a collector that efficiently turns a stream of strings into a char
	 *         array
	 */
	public static Collector<String, ?, char[]> toCharArray() {
		return Collector.of( //
				StringBuilder::new, // supplier
				StringBuilder::append, // accumulator
				StringBuilder::append, // combiner
				sb -> { // finisher
					final int length = sb.length();
					final var array = new char[length];
					sb.getChars(0, length, array, 0);
					return array;
				});
	}
}