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

import java.io.IOException;
import java.net.URL;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.internal.text.html.BrowserInformationControl;
import org.eclipse.jface.resource.ColorRegistry;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.DefaultInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.browser.ProgressListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.texteditor.AbstractDecoratedTextEditorPreferenceConstants;

@SuppressWarnings("restriction")
public class FocusableBrowserInformationControl extends BrowserInformationControl {

	private static final String HEAD = "<head>"; //$NON-NLS-1$

	private static final LocationListener HYPER_LINK_LISTENER = new LocationListener() {

		@Override
		public void changing(LocationEvent event) {
			if (!"about:blank".equals(event.location)) { //$NON-NLS-1$
				LSPEclipseUtils.open(event.location, UI.getActivePage(), null);
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
	public void setInput(Object input) {
		if (input instanceof String) {
			input = styleHtml((String)input);
		}
		super.setInput(input);
	}

	public String styleHtml(String html) {
		if (html == null || html.isEmpty()) {
			return html;
		}

		// put CSS styling to match Eclipse style
		ColorRegistry colorRegistry = JFaceResources.getColorRegistry();
		Color foreground = colorRegistry.get("org.eclipse.ui.workbench.HOVER_FOREGROUND"); //$NON-NLS-1$
		Color background = colorRegistry.get("org.eclipse.ui.workbench.HOVER_BACKGROUND"); //$NON-NLS-1$
		String style = "<style TYPE='text/css'>html { " + //$NON-NLS-1$
				"font-family: " + JFaceResources.getDefaultFontDescriptor().getFontData()[0].getName() + "; " + //$NON-NLS-1$ //$NON-NLS-2$
				"font-size: " + Integer.toString(JFaceResources.getDefaultFontDescriptor().getFontData()[0].getHeight()) //$NON-NLS-1$
				+ "pt; " + //$NON-NLS-1$
				(background != null ? "background-color: " + toHTMLrgb(background.getRGB()) + "; " : "") + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				(foreground != null ? "color: " + toHTMLrgb(foreground.getRGB()) + "; " : "") + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				" }</style>"; //$NON-NLS-1$

		String hlStyle = null;
		try {
			URL urlHJScript = FileLocator.toFileURL(LanguageServerPlugin.getDefault().getClass().getResource("/resources/highlight.min.js/highlight.min.js")); //$NON-NLS-1$
			URL urlHJCss = FileLocator.toFileURL(LanguageServerPlugin.getDefault().getClass().getResource(isDarkTheme() ? //
					"/resources/highlight.min.js/styles/dark.min.css" : //$NON-NLS-1$
					"/resources/highlight.min.js/styles/default.min.css")); //$NON-NLS-1$
			if (urlHJScript != null && urlHJCss != null) {
				hlStyle = "<link rel='stylesheet' href='" + urlHJCss.toString() + "'>" + //$NON-NLS-1$ //$NON-NLS-2$
						"<script src='" + urlHJScript.toString() + "'></script>" + //$NON-NLS-1$ //$NON-NLS-2$
						"<script>hljs.highlightAll();</script>"; //$NON-NLS-1$
			}
		} catch (IOException e) {
			LanguageServerPlugin.logError(e);
		}

		int headIndex = html.indexOf(HEAD);
		StringBuilder builder = new StringBuilder(html.length() + style.length());
		builder.append(html.substring(0, headIndex + HEAD.length()));
		builder.append(style);
		if (hlStyle != null) {
			builder.append(hlStyle);
		}
		builder.append(html.substring(headIndex + HEAD.length()));
		return builder.toString();
	}

	private boolean isDarkTheme() {
		RGB color = getShell().getBackground().getRGB();
		return (color.red * 0.299 + color.green * 0.587+ color.blue *0.114) < 128; //turn to grey and check the level
	}

	private static @NonNull String toHTMLrgb(RGB rgb) {
		StringBuilder builder = new StringBuilder(7);
		builder.append('#');
		appendAsHexString(builder, rgb.red);
		appendAsHexString(builder, rgb.green);
		appendAsHexString(builder, rgb.blue);
		return builder.toString();
	}

	private static void appendAsHexString(StringBuilder buffer, int intValue) {
		String hexValue= Integer.toHexString(intValue);
		if (hexValue.length() == 1) {
			buffer.append('0');
		}
		buffer.append(hexValue);
	}

	@Override
	public IInformationControlCreator getInformationPresenterControlCreator() {
		return parent -> {
			if (BrowserInformationControl.isAvailable(parent)) {
				BrowserInformationControl res = new FocusableBrowserInformationControl(parent, JFaceResources.DEFAULT_FONT,
						true);
				res.addLocationListener(HYPER_LINK_LISTENER);
				return res;
			} else {
				return new DefaultInformationControl(parent);
			}
		};
	}

}