/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.color;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Collections;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.tests.util.DisplayHelper;
import org.eclipse.lsp4e.test.AllCleanRule;
import org.eclipse.lsp4e.test.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.Color;
import org.eclipse.lsp4j.ColorInformation;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class ColorTest {

	@Rule public AllCleanRule cleanRule = new AllCleanRule();
	private RGB color;

	@Before
	public void setUp() {
		color = new RGB(56, 78, 90); // a color that's not likely used anywhere else
		MockLanguageServer.INSTANCE.getTextDocumentService().setDocumentColors(Collections.singletonList(new ColorInformation(new Range(new Position(0, 0), new Position(0, 1)), new Color(color.red / 255., color.green / 255., color.blue / 255., 0))));
	}
	
	@Test
	public void testColorProvider() throws Exception {
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(TestUtils.createProject("testColorProvider"), "a"));
		StyledText widget = viewer.getTextWidget();
		Assert.assertTrue(new DisplayHelper() {
			@Override
			protected boolean condition() {
				return containsColor(widget, color);
			}
		}.waitForCondition(widget.getDisplay(), 3000));
	}

	@Test
	public void testColorProviderExternalFile() throws Exception {
		File file = File.createTempFile("testColorProviderExternalFile", ".lspt");
		try {
			try (
				FileOutputStream out = new FileOutputStream(file);
			) {
				out.write('a');
			}
			ITextViewer viewer = TestUtils.getTextViewer(IDE.openEditorOnFileStore(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(), EFS.getStore(file.toURI())));
			StyledText widget = viewer.getTextWidget();
			Assert.assertTrue(new DisplayHelper() {
				@Override
				protected boolean condition() {
					return containsColor(widget, color);
				}
			}.waitForCondition(widget.getDisplay(), 3000));
		} finally {
			Files.deleteIfExists(file.toPath());
		}
	}

	/**
	 * TODO consider reusing directly code from Test_org_eclipse_swt_custom_StyledText
	 * @param widget
	 * @param expectedRGB
	 * @return
	 */
	protected static boolean containsColor(Control widget, RGB expectedRGB) {
		if (widget.getSize().x == 0) {
			return false;
		}
		GC gc = new GC(widget);
		Image image = new Image(widget.getDisplay(), widget.getSize().x, widget.getSize().y);
		gc.copyArea(image, 0, 0);
		gc.dispose();
		ImageData imageData = image.getImageData();
		for (int x = 0; x < image.getBounds().width; x++) {
			for (int y = 0; y < image.getBounds().height; y++) {
				RGB pixelRGB = imageData.palette.getRGB(imageData.getPixel(x, y));
				if (expectedRGB.equals(pixelRGB)) {
					image.dispose();
					return true;
				}
			}
		}
		image.dispose();
		return false;
	}

}
