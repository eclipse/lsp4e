/*******************************************************************************
 * Copyright (c) 2022 Avaloq Group AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 *******************************************************************************/
package org.eclipse.lsp4e.operations.semanticTokens;

import java.util.function.Function;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.tm4e.ui.text.TMPresentationReconciler;
import org.eclipse.tm4e.ui.themes.ITokenProvider;

/**
 * A Class that maps TokenTypes to {@link IToken}.
 */
abstract class TokenTypeMapper implements Function<String, @Nullable IToken> {

	private static final TokenTypeMapper NO_OP = new TokenTypeMapper() {
		@Override
		public @Nullable IToken apply(final String tokenType) {
			return null;
		}
	};

	private static final class TM4ETokenTypeMapper extends TokenTypeMapper {
		private final ITextViewer viewer;
		private @Nullable TMPresentationReconciler tmPresentationReconciler;

		TM4ETokenTypeMapper(final ITextViewer viewer) {
			this.viewer = viewer;
		}

		/**
		 * Returns <code>null</code> if no TextMate grammar is associated with this
		 * tokenType
		 */
		@Override
		public @Nullable IToken apply(final String tokenType) {
			if (tmPresentationReconciler == null) {
				tmPresentationReconciler = TMPresentationReconciler.getTMPresentationReconciler(viewer);
			}

			if (tmPresentationReconciler != null) {
				final ITokenProvider theme = tmPresentationReconciler.getTokenProvider();
				if (theme != null) {
					return theme.getToken(tokenType);
				}
			}

			// Do NOT fallback to default theme, as this may result in a deadlock!
			// See https://github.com/eclipse/lsp4e/issues/1028
			// return TMUIPlugin.getThemeManager().getDefaultTheme().getToken(tokenType);
			return null;
		}
	}

	public static TokenTypeMapper create(final ITextViewer viewer) {
		/*
		 * try to instantiate TM4ETokenTypeMapper which depends on optional TM4E plugin
		 */
		try {
			return new TM4ETokenTypeMapper(viewer);
		} catch (final NoClassDefFoundError ex) {
			// TM4E is not available
			return NO_OP;
		}
	}
}
