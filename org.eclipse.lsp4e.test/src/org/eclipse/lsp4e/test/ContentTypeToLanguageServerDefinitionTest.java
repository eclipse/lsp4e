/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Rastislav Wagner (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.lsp4e.ContentTypeToLanguageServerDefinition;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServersRegistry;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class ContentTypeToLanguageServerDefinitionTest {

	public static final String SERVER_TO_DISABLE = "org.eclipse.lsp4e.test.server.disable";
	public static final String DISABLED_CONTENT_TYPE = "org.eclipse.lsp4e.test.content-type-disabled";
	public static final String DISABLED_SERVER_PREF = SERVER_TO_DISABLE + "/" + DISABLED_CONTENT_TYPE;

	@BeforeClass
	public static void setup() {
		LanguageServerPlugin.getDefault().getPreferenceStore()
				.setValue(DISABLED_SERVER_PREF, "false");
	}

	@AfterClass
	public static void cleanup() {
		TestUtils.getDisabledLS().setUserEnabled(false);
	}

	@Test
	public void testDisabledLanguageServerMappingsPreference() {
		List<ContentTypeToLanguageServerDefinition> disabledDefinitions = LanguageServersRegistry.getInstance()
				.getContentTypeToLSPExtensions().stream().filter(lsDefinition -> !lsDefinition.isEnabled(null))
				.collect(Collectors.toList());
		assertEquals(1, disabledDefinitions.size());
		assertEquals(SERVER_TO_DISABLE, disabledDefinitions.get(0).getValue().id);
		assertEquals(DISABLED_CONTENT_TYPE, disabledDefinitions.get(0).getKey().toString());

	}

	@Test
	public void testDisableLanguageServerMapping() {
		ContentTypeToLanguageServerDefinition lsDefinition = TestUtils.getDisabledLS();
		lsDefinition.setUserEnabled(false);
		assertFalse(lsDefinition.isEnabled(null));
		assertTrue(LanguageServerPlugin.getDefault().getPreferenceStore().contains(DISABLED_SERVER_PREF));
		assertFalse(LanguageServerPlugin.getDefault().getPreferenceStore().getBoolean(DISABLED_SERVER_PREF));
		lsDefinition.setUserEnabled(true);
		assertTrue(lsDefinition.isEnabled(null));
		assertTrue(LanguageServerPlugin.getDefault().getPreferenceStore().contains(DISABLED_SERVER_PREF));
		assertTrue(LanguageServerPlugin.getDefault().getPreferenceStore().getBoolean(DISABLED_SERVER_PREF));
	}

}
