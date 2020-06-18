/*******************************************************************************
 * Copyright (c) 2016, 2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.hover;

import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.internal.text.html.BrowserInformationControl;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.browser.ProgressListener;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.texteditor.AbstractDecoratedTextEditorPreferenceConstants;

@SuppressWarnings("restriction")
class FocusableBrowserInformationControl extends BrowserInformationControl {

	private static final LocationListener HYPER_LINK_LISTENER = new LocationListener() {

		@Override
		public void changing(LocationEvent event) {
			if (!"about:blank".equals(event.location)) { //$NON-NLS-1$
				IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
				LSPEclipseUtils.open(event.location, page, null);
				event.doit = false;
			}
		}

		@Override
		public void changed(LocationEvent event) {
			// comment requested by sonar
		}
	};

	public FocusableBrowserInformationControl(Shell parent, String symbolicFontName, boolean resizable) {
		super(parent, JFaceResources.DEFAULT_FONT, resizable);
	}

	public FocusableBrowserInformationControl(Shell parent) {
		super(parent, JFaceResources.DEFAULT_FONT, EditorsUI.getTooltipAffordanceString());
	}

	@Override
	protected void createContent(Composite parent) {
		super.createContent(parent);
		Browser b = (Browser) (parent.getChildren()[0]);
		b.addProgressListener(ProgressListener.completedAdapter(event -> {
			if (getInput() == null)
				return;
			Browser browser = (Browser) event.getSource();
			@Nullable
			Point constraints = getSizeConstraints();
			Point hint = computeSizeHint();

			setSize(hint.x, hint.y);
			browser.execute("document.getElementsByTagName(\"html\")[0].style.whiteSpace = \"nowrap\""); //$NON-NLS-1$
			Double width = 20 + (Double) browser.evaluate("return document.body.scrollWidth;"); //$NON-NLS-1$

			setSize(width.intValue(), hint.y);
			browser.execute("document.getElementsByTagName(\"html\")[0].style.whiteSpace = \"normal\""); //$NON-NLS-1$
			Double height = (Double) browser.evaluate("return document.body.scrollHeight;"); //$NON-NLS-1$
			if (Platform.getPreferencesService().getBoolean(EditorsUI.PLUGIN_ID,
					AbstractDecoratedTextEditorPreferenceConstants.EDITOR_SHOW_TEXT_HOVER_AFFORDANCE, true,
					null)) {
				FontData[] fontDatas = JFaceResources.getDialogFont().getFontData();
				height = fontDatas[0].getHeight() + height;
			}

			width = Double.valueOf(width * 1.5);
			if (constraints != null && constraints.x < width) {
				width = (double) constraints.x;
			}
			if (constraints != null && constraints.y < height) {
				height = (double) constraints.y;
			}

			setSize(width.intValue(), height.intValue());
		}));
		b.setJavascriptEnabled(true);
	}

	@Override
	public IInformationControlCreator getInformationPresenterControlCreator() {
		return parent -> {
			BrowserInformationControl res = new FocusableBrowserInformationControl(parent, JFaceResources.DEFAULT_FONT,
					true);
			res.addLocationListener(HYPER_LINK_LISTENER);
			return res;
		};
	}
}