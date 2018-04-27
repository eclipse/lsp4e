/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.references;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.Thread.State;
import java.util.HashMap;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.tests.util.DisplayHelper;
import org.eclipse.lsp4e.operations.references.LSFindReferences;
import org.eclipse.lsp4e.test.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageSever;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.search.ui.ISearchResultViewPart;
import org.eclipse.search.ui.NewSearchUI;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.services.IEvaluationService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FindReferencesTest {

	private IProject project;

	@Before
	public void setUp() throws CoreException {
		project = TestUtils.createProject("CompletionTest" + System.currentTimeMillis());
		IViewPart searchPart = NewSearchUI.getSearchResultView();
		if (searchPart != null) {
			searchPart.getViewSite().getPage().hideView(searchPart);
		}
	}

	@After
	public void tearDown() throws CoreException {
		PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().closeAllEditors(false);
		project.delete(true, true, new NullProgressMonitor());
		IViewPart searchPart = NewSearchUI.getSearchResultView();
		if (searchPart != null) {
			searchPart.getViewSite().getPage().hideView(searchPart);
		}
		MockLanguageSever.INSTANCE.shutdown();
	}
	
	@Test
	public void findReferencesShowsResultView() throws Exception {
		IFile testFile = TestUtils.createUniqueTestFile(project, "dummyContent");
		TestUtils.openTextViewer(testFile);
		MockLanguageSever.INSTANCE.getTextDocumentService().setMockReferences(
				new Location(testFile.getLocationURI().toString(),	new Range(
						new Position(1, 1), new Position(1, 2))));
		
		LSFindReferences handler = new LSFindReferences();
		IEvaluationService evaluationService = (IEvaluationService)PlatformUI.getWorkbench().getService(IEvaluationService.class);
		handler.execute(new ExecutionEvent(null, new HashMap<>(), null, evaluationService.getCurrentState()));
		
		ISearchResultViewPart part = findSearchResultView(1000);
		assertNotNull("Search results not shown", part);
	}
	
	private ISearchResultViewPart findSearchResultView(int timeout) {
		new DisplayHelper() {
			@Override
			protected boolean condition() {
				return  NewSearchUI.getSearchResultView() != null;
			}
		}.waitForCondition(Display.getCurrent(), timeout);
		return NewSearchUI.getSearchResultView();
	}

	@Test
	public void findReferencesNonBlocking() throws Exception {
		int responseDelay = 3000;
		int uiFreezeThreesholdreezeThreeshold = 300;
		MockLanguageSever.INSTANCE.setTimeToProceedQueries(responseDelay);
		TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, "dummyContent"));

		LSFindReferences handler = new LSFindReferences();
		IEvaluationService evaluationService = (IEvaluationService)PlatformUI.getWorkbench().getService(IEvaluationService.class);
		long time = System.currentTimeMillis();
		handler.execute(new ExecutionEvent(null, new HashMap<>(), null, evaluationService.getCurrentState()));

		long delay = System.currentTimeMillis() - time;
		assertTrue("Find references blocked UI for " + delay + "ms", delay < uiFreezeThreesholdreezeThreeshold);
		Thread uiThreadActiveChecker = new Thread(() -> {
			while (!Thread.currentThread().isInterrupted()) {
				long triggerTime = System.currentTimeMillis();
				PlatformUI.getWorkbench().getDisplay().syncExec(() -> {
					long uiThreadRequestTime = System.currentTimeMillis() - triggerTime;
					assertTrue("UI Thread blocked for " + uiThreadRequestTime, uiThreadRequestTime < uiFreezeThreesholdreezeThreeshold);
				});
			}
		});
		uiThreadActiveChecker.start();
		try {
			assertNotNull("Search Result view not found", findSearchResultView(2000));
			assertEquals("UI Thread was frozen", State.RUNNABLE, uiThreadActiveChecker.getState());
		} finally {
			uiThreadActiveChecker.interrupt();
		}
	}
}
