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

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.tm4e.ui.TMUIPlugin;
import org.eclipse.tm4e.ui.text.TMPresentationReconciler;
import org.eclipse.tm4e.ui.themes.ITheme;
import org.eclipse.tm4e.ui.themes.ITokenProvider;
import org.eclipse.ui.PlatformUI;

/**
 * A Class that maps TokenTypes to {@link IToken}.
 */
public class TokenTypeMapper implements Function<String, IToken> {
	private @NonNull final ITextViewer viewer;

	public TokenTypeMapper(@NonNull final ITextViewer viewer) {
		this.viewer = viewer;
	}

	@Override
	public IToken apply(final String tokenType) {
		if (tokenType == null) {
			return null;
		}
		TMPresentationReconciler tmPresentationReconciler = TMPresentationReconciler
				.getTMPresentationReconciler(viewer);

		if (tmPresentationReconciler != null) {
			ITokenProvider tokenProvider = tmPresentationReconciler.getTokenProvider();
			if (tokenProvider != null) {
				return tokenProvider.getToken(tokenType);
			}
		}
		final ITheme[] defaultTheme = new ITheme[1];
		// Needs to be executed in UI-thread:
		PlatformUI.getWorkbench().getDisplay().syncExec(() -> defaultTheme[0] = TMUIPlugin.getThemeManager().getDefaultTheme());
		return defaultTheme[0].getToken(tokenType);
	}
}
