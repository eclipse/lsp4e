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

import static org.eclipse.lsp4e.test.TestUtils.waitForAndAssertCondition;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.lsp4e.operations.references.LSFindReferences;
import org.eclipse.lsp4e.test.AllCleanRule;
import org.eclipse.lsp4e.test.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.search.ui.ISearchResultViewPart;
import org.eclipse.search.ui.NewSearchUI;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.services.IEvaluationService;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class FindReferencesTest {

	@Rule public AllCleanRule clear = new AllCleanRule();
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
	public void tearDown() {
		IViewPart searchPart = NewSearchUI.getSearchResultView();
		if (searchPart != null) {
			searchPart.getViewSite().getPage().hideView(searchPart);
		}
	}

	@Test
	public void findReferencesShowsResultView() throws Exception {
		IFile testFile = TestUtils.createUniqueTestFile(project, "dummyContent");
		TestUtils.openTextViewer(testFile);
		MockLanguageServer.INSTANCE.getTextDocumentService().setMockReferences(
				new Location(testFile.getLocationURI().toString(),	new Range(
						new Position(1, 1), new Position(1, 2))));

		LSFindReferences handler = new LSFindReferences();
		IEvaluationService evaluationService = PlatformUI.getWorkbench().getService(IEvaluationService.class);
		handler.execute(new ExecutionEvent(null, new HashMap<>(), null, evaluationService.getCurrentState()));

		ISearchResultViewPart part = findSearchResultView(3000);
		assertNotNull("Search results not shown", part);
	}

	private ISearchResultViewPart findSearchResultView(int timeout) {
		waitForAndAssertCondition(timeout, () -> NewSearchUI.getSearchResultView() != null);
		return NewSearchUI.getSearchResultView();
	}

	@Test
	public void findReferencesNonBlocking() throws Exception {
		int responseDelay = 3000;
		int uiFreezeThreesholdreezeThreeshold = 300;
		MockLanguageServer.INSTANCE.setTimeToProceedQueries(responseDelay);
		TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, "dummyContent"));

		LSFindReferences handler = new LSFindReferences();
		IEvaluationService evaluationService = PlatformUI.getWorkbench().getService(IEvaluationService.class);
		long time = System.currentTimeMillis();
		handler.execute(new ExecutionEvent(null, new HashMap<>(), null, evaluationService.getCurrentState()));

		long delay = System.currentTimeMillis() - time;
		// TODO re-use the UI freeze monitoring org.eclipse.ui.monitoring instead
		assertTrue("Find references blocked UI for " + delay + "ms", delay < uiFreezeThreesholdreezeThreeshold);
		AtomicInteger blockedCount = new AtomicInteger();
		Thread uiThreadActiveChecker = new Thread(() -> {
			while (!Thread.currentThread().isInterrupted()) {
				long triggerTime = System.currentTimeMillis();
				PlatformUI.getWorkbench().getDisplay().syncExec(() -> {
					long uiThreadRequestTime = System.currentTimeMillis() - triggerTime;
					if (uiThreadRequestTime > uiFreezeThreesholdreezeThreeshold) {
						blockedCount.incrementAndGet();
					}
				});
			}
		});
		uiThreadActiveChecker.start();
		try {
			assertNotNull("Search Result view not found", findSearchResultView(5000));
			assertTrue("UI Thread was frozen " + blockedCount + " times", blockedCount.intValue() == 0);
		} finally {
			uiThreadActiveChecker.interrupt();
		}
	}
}
