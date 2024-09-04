/*******************************************************************************
 * Copyright (c) 2024 Advantest Europe GmbH. All rights reserved.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Dietrich Travkin (Solunar GmbH) - initial implementation of outline contents filtering (issue #254)
 *******************************************************************************/
package org.eclipse.lsp4e.outline;

import java.util.Arrays;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.ui.LSPImages;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.actions.CompoundContributionItem;

public class OutlineViewHideSymbolKindMenuContributor extends CompoundContributionItem {

	@Override
	protected IContributionItem[] getContributionItems() {
		return Arrays.stream(SymbolKind.values())
			.sorted((sk1, sk2) -> sk1.name().compareTo(sk2.name()))
			.map(this::createHideSymbolKindContributionItem)
			.toArray(IContributionItem[]::new);
	}

	private IContributionItem createHideSymbolKindContributionItem(SymbolKind kind) {
		return new ActionContributionItem(new HideSymbolKindAction(kind));
	}

	static boolean isHideSymbolKind(SymbolKind kind) {
		IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode(LanguageServerPlugin.PLUGIN_ID);
		return preferences.getBoolean(CNFOutlinePage.HIDE_DOCUMENT_SYMBOL_KIND_PREFERENCE_PREFIX + kind.name(), false);
	}

	static boolean toggleHideSymbolKind(SymbolKind kind) {
		IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode(LanguageServerPlugin.PLUGIN_ID);
		boolean oldValue = isHideSymbolKind(kind);

		preferences.putBoolean(CNFOutlinePage.HIDE_DOCUMENT_SYMBOL_KIND_PREFERENCE_PREFIX + kind.name(), !oldValue);

		return !oldValue;
	}

	private static class HideSymbolKindAction extends Action {
		private final SymbolKind kind;

		HideSymbolKindAction(SymbolKind kind) {
			super(kind.name(), IAction.AS_CHECK_BOX);
			this.kind = kind;
			setChecked(isHideSymbolKind(kind));

			Image img = LSPImages.imageFromSymbolKind(kind);
			if (img != null) {
				setImageDescriptor(ImageDescriptor.createFromImage(img));
			}
		}

		@Override
		public void run() {
			boolean checkedState = toggleHideSymbolKind(kind);
			setChecked(checkedState);
		}

	}

}
