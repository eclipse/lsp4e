/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.ui;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {

	public static String hyperlinkLabel;
	public static String PreferencesPage_Intro;
	public static String PreferencesPage_staticServers;
	public static String PreferencesPage_manualServers;
	public static String PreferencesPage_LaunchConfiguration;
	public static String PreferencesPage_LaunchMode;
	public static String PreferencesPage_Add;
	public static String PreferencesPage_Remove;
	public static String PreferencesPage_contentType;
	public static String PreferencesPage_languageServer;
	public static String NewContentTypeLSPLaunchDialog_associateContentType;
	public static String NewContentTypeLSPLaunchDialog_withLSPLaunch;
	public static String codeActions_description;
	public static String codeActions_label;
	public static String codeActions_emptyMenu;
	public static String codeLens_emptyMenu;
	public static String updateCodeActions_menu;
	public static String initializeLanguageServer_job;
	public static String rename_job;
	public static String referenceSearchQuery;
	public static String computing;
	public static String notImplemented;
	public static String LSPSymbolInWorkspaceDialog_DialogLabel;
	public static String LSPSymbolInWorkspaceDialog_DialogTitle;
	public static String updateCodelensMenu_job;
	public static String outline_computingSymbols;
	public static String findReferences_jobName;
	public static String findReferences_updateResultView_jobName;
	public static String rename_title;
	public static String rename_label;
	public static String rename_invalid;
	public static String serverEdit;
	public static String completionError;

	static {
		NLS.initializeMessages(Messages.class.getPackage().getName() + ".messages", Messages.class); //$NON-NLS-1$
	}
}
