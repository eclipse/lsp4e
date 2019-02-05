/*******************************************************************************
 * Copyright (c) 2016 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.hover;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.internal.text.html.BrowserInformationControl;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.tests.util.DisplayHelper;
import org.eclipse.lsp4e.operations.hover.LSBasedHover;
import org.eclipse.lsp4e.test.AllCleanRule;
import org.eclipse.lsp4e.test.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.ProgressAdapter;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@SuppressWarnings("restriction")
public class HoverTest {

	@Rule public AllCleanRule clear = new AllCleanRule();
	private IProject project;
	private LSBasedHover hover;
	
	@Before
	public void setUp() throws CoreException {
		project = TestUtils.createProject("HoverTest" + System.currentTimeMillis());
		hover = new LSBasedHover();
	}

	@Test
	public void testHoverRegion() throws CoreException, InvocationTargetException {
		Hover hoverResponse = new Hover(Collections.singletonList(Either.forLeft("HoverContent")), new Range(new Position(0,  0), new Position(0, 10)));
		MockLanguageServer.INSTANCE.setHover(hoverResponse);

		IFile file = TestUtils.createUniqueTestFile(project, "HoverRange Other Text");
		ITextViewer viewer = TestUtils.openTextViewer(file);

		assertEquals(new Region(0, 10), hover.getHoverRegion(viewer, 5));
	}
	
	@Test
	public void testHoverRegionInvalidOffset() throws CoreException, InvocationTargetException {
		MockLanguageServer.INSTANCE.setHover(null);

		IFile file = TestUtils.createUniqueTestFile(project, "HoverRange Other Text");
		ITextViewer viewer = TestUtils.openTextViewer(file);

		assertEquals(new Region(15, 0), hover.getHoverRegion(viewer, 15));
	}
	
	@Test
	public void testHoverInfo() throws CoreException, InvocationTargetException {
		Hover hoverResponse = new Hover(Collections.singletonList(Either.forLeft("HoverContent")), new Range(new Position(0,  0), new Position(0, 10)));
		MockLanguageServer.INSTANCE.setHover(hoverResponse);

		IFile file = TestUtils.createUniqueTestFile(project, "HoverRange Other Text");
		ITextViewer viewer = TestUtils.openTextViewer(file);

		// TODO update test when MARKDOWN to HTML will be finished
		assertTrue(hover.getHoverInfo(viewer, new Region(0, 10)).contains("HoverContent"));
	}
	
	@Test
	public void testHoverInfoEmptyContentList() throws CoreException, InvocationTargetException {
		Hover hoverResponse = new Hover(Collections.emptyList(), new Range(new Position(0,  0), new Position(0, 10)));
		MockLanguageServer.INSTANCE.setHover(hoverResponse);

		IFile file = TestUtils.createUniqueTestFile(project, "HoverRange Other Text");
		ITextViewer viewer = TestUtils.openTextViewer(file);

		assertEquals(null, hover.getHoverInfo(viewer, new Region(0, 10)));
	}
	
	@Test
	public void testHoverInfoInvalidOffset() throws CoreException, InvocationTargetException {
		MockLanguageServer.INSTANCE.setHover(null);

		IFile file = TestUtils.createUniqueTestFile(project, "HoverRange Other Text");
		ITextViewer viewer = TestUtils.openTextViewer(file);

		assertEquals(null, hover.getHoverInfo(viewer, new Region(0, 10)));
	}
	
	@Test
	public void testHoverEmptyContentItem() throws CoreException, InvocationTargetException {
		Hover hoverResponse = new Hover(Collections.singletonList(Either.forLeft("")), new Range(new Position(0,  0), new Position(0, 10)));
		MockLanguageServer.INSTANCE.setHover(hoverResponse);

		IFile file = TestUtils.createUniqueTestFile(project, "HoverRange Other Text");
		ITextViewer viewer = TestUtils.openTextViewer(file);

		assertEquals(null, hover.getHoverInfo(viewer, new Region(0, 10)));
	}

	@Test
	public void testHoverOnExternalFile()
			throws CoreException, InvocationTargetException, IOException, InterruptedException {
		Hover hoverResponse = new Hover(Collections.singletonList(Either.forLeft("blah")),
				new Range(new Position(0, 0), new Position(0, 0)));
		MockLanguageServer.INSTANCE.setHover(hoverResponse);

		File file = File.createTempFile("testHoverOnExternalfile", ".lspt");
		try {
			ITextViewer viewer = TestUtils.getTextViewer(IDE.openInternalEditorOnFileStore(
					PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(), EFS.getStore(file.toURI())));
			Assert.assertTrue(hover.getHoverInfo(viewer, new Region(0, 0)).contains("blah"));
		} finally {
			Files.deleteIfExists(file.toPath());
		}
	}

	@Test
	public void testMultipleHovers() throws Exception {
		Hover hoverResponse = new Hover(Collections.singletonList(Either.forLeft("HoverContent")), new Range(new Position(0,  0), new Position(0, 10)));
		MockLanguageServer.INSTANCE.setHover(hoverResponse);

		IFile file = TestUtils.createUniqueTestFileMultiLS(project, "HoverRange Other Text");
		ITextViewer viewer = TestUtils.openTextViewer(file);

		// TODO update test when MARKDOWN to HTML will be finished
		String hoverInfo = hover.getHoverInfo(viewer, new Region(0, 10));
		int index = hoverInfo.indexOf("HoverContent");
		assertNotEquals("Hover content not found", -1, index);
		index += "HoverContent".length();
		index = hoverInfo.indexOf("HoverContent", index);
		assertNotEquals("Hover content found only once", -1, index);
	}

	@Test
	public void testIntroUrlLink() throws Exception {
		Hover hoverResponse = new Hover(
				Collections.singletonList(Either.forLeft(
						"[My intro URL link](http://org.eclipse.ui.intro/execute?command=org.eclipse.ui.file.close)")),
				new Range(new Position(0, 0), new Position(0, 10)));
		MockLanguageServer.INSTANCE.setHover(hoverResponse);

		IFile file = TestUtils.createUniqueTestFile(project, "HoverRange Other Text");
		ITextViewer viewer = TestUtils.openTextViewer(file);

		String hoverContent = hover.getHoverInfo(viewer, new Region(0, 10));

		LSBasedHover hoverManager = new LSBasedHover();

		Display display = PlatformUI.getWorkbench().getDisplay();
		final Shell shell = new Shell(display);
		BrowserInformationControl wrapperControl = null, control = null;
		try {
			final RowLayout layout = new RowLayout(SWT.VERTICAL);
			layout.fill = true;
			shell.setLayout(layout);
			shell.setSize(320, 200);
			shell.open();

			wrapperControl = (BrowserInformationControl) hoverManager.getHoverControlCreator()
					.createInformationControl(shell);
			control = (BrowserInformationControl) wrapperControl
					.getInformationPresenterControlCreator().createInformationControl(shell);
			Field f = BrowserInformationControl.class.getDeclaredField("fBrowser"); //
			f.setAccessible(true);

			Browser browser = (Browser) f.get(control);
			browser.setJavascriptEnabled(true);

			AtomicBoolean completed = new AtomicBoolean(false);

			browser.addProgressListener(new ProgressAdapter() {
				@Override
				public void completed(ProgressEvent event) {
					browser.removeProgressListener(this);
					browser.execute("document.getElementsByTagName('a')[0].click()");
					completed.set(true);
				}
			});

			assertNotNull("Editor should be opened", viewer.getTextWidget());

			browser.setText(hoverContent);

			assertTrue("action didn't close editor", new DisplayHelper() {
				@Override
				protected boolean condition() {
					return completed.get() && (viewer.getTextWidget() == null || viewer.getTextWidget().isDisposed());
				}
			}.waitForCondition(browser.getDisplay(), 10000));
		} finally {
			if (control != null) {
				control.dispose();
			}
			if (wrapperControl != null) {
				wrapperControl.dispose();
			}
			shell.dispose();
		}
	}

}
