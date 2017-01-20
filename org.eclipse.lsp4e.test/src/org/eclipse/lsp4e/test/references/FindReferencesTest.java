/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.references;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.lsp4e.operations.references.LSFindReferences;
import org.eclipse.lsp4e.test.MockLanguageSever;
import org.eclipse.lsp4e.test.TestUtils;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.search.ui.ISearchResultViewPart;
import org.eclipse.search.ui.NewSearchUI;
import org.eclipse.search.ui.SearchUI;
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
		IViewPart searchPart = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().findView(SearchUI.SEARCH_RESULT_VIEW_ID);
		if (searchPart != null) {
			searchPart.getViewSite().getPage().hideView(searchPart);
		}
	}

	@After
	public void tearDown() throws CoreException {
		project.delete(true, true, new NullProgressMonitor());
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
		ISearchResultViewPart res = null;
		long start = System.currentTimeMillis();
		while (System.currentTimeMillis() - start < timeout) {
			if (!Display.getCurrent().readAndDispatch()) Display.getCurrent().sleep();
			res = NewSearchUI.getSearchResultView();
			if (res != null) {
				return res;
			}
		}
		return res;
	}

	@Test
	public void findReferencesNonBlocking() throws Exception {
		int responseDelay = 3000;
		int uiFreezeThreesholdreezeThreeshold = 200;
		MockLanguageSever.INSTANCE.setTimeToProceedQueries(responseDelay);
		TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, "dummyContent"));

		LSFindReferences handler = new LSFindReferences();
		IEvaluationService evaluationService = (IEvaluationService)PlatformUI.getWorkbench().getService(IEvaluationService.class);
		long time = System.currentTimeMillis();
		handler.execute(new ExecutionEvent(null, new HashMap<>(), null, evaluationService.getCurrentState()));
		
		long delay = System.currentTimeMillis() - time;
		assertTrue("Find references blocked UI for " + delay + "ms", delay < uiFreezeThreesholdreezeThreeshold);
		while (delay < responseDelay) {
			long triggerTime = System.currentTimeMillis();
			Display.getCurrent().asyncExec(() -> {
				long uiThreadRequestTime = System.currentTimeMillis() - triggerTime;
				assertTrue("UI Thread blocked for " + uiThreadRequestTime, uiThreadRequestTime < uiFreezeThreesholdreezeThreeshold);
			});
			delay = System.currentTimeMillis() - time;
			if (Display.getCurrent().readAndDispatch()) Display.getCurrent().sleep();
		}
	}
}
