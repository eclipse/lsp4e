/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.languages;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.eclipse.core.externaltools.internal.IExternalToolConstants;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentTypeManager;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.lsp4e.LSPStreamConnectionProviderRegistry;
import org.eclipse.lsp4e.LaunchConfigurationStreamProvider;
import org.eclipse.ui.IStartup;

/**
 * Initialize the LaunchConfiguration that will be used to start language servers.
 * TODO: find a better way to contribute that, or maybe use some dedicated launch types.
 * @author mistria
 *
 */
public class InitializeLaunchConfigurations implements IStartup {

	public static final String VSCODE_CSS_NAME = "VSCode-CSS";
	public static final String VSCODE_JSON_NAME = "VSCode-JSON";
	public static final String OMNISHARP_NAME = "OmniSharp";

	@Override
	public void earlyStartup() {
		IContentTypeManager contentTypeManager = Platform.getContentTypeManager();
		ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager();
		ILaunchConfigurationType externalType = launchManager.getLaunchConfigurationType(IExternalToolConstants.ID_PROGRAM_LAUNCH_CONFIGURATION_TYPE);
		LSPStreamConnectionProviderRegistry registry = LSPStreamConnectionProviderRegistry.getInstance();
		// OmniSharp
		try {
			String omniSharpLaunchName = OMNISHARP_NAME;
			ILaunchConfiguration omniSharpLauch = null;
			for (ILaunchConfiguration launch : launchManager.getLaunchConfigurations(externalType)) {
				if (launch.getName().equals(omniSharpLaunchName)) {
					omniSharpLauch = launch;
				}
			}
			if (omniSharpLauch == null) {
				ILaunchConfigurationWorkingCopy workingCopy = externalType.newInstance(null, omniSharpLaunchName);
				// some common config
				workingCopy.setAttribute(IExternalToolConstants.ATTR_LAUNCH_IN_BACKGROUND, true);
				workingCopy.setAttribute(IExternalToolConstants.ATTR_BUILDER_ENABLED, false);
				workingCopy.setAttribute(IExternalToolConstants.ATTR_SHOW_CONSOLE, false);
				workingCopy.setAttribute(IExternalToolConstants.ATTR_BUILD_SCOPE, "${none}");
				workingCopy.setAttribute(DebugPlugin.ATTR_CAPTURE_OUTPUT, true);
				workingCopy.setAttribute(IExternalToolConstants.ATTR_LOCATION, getNodeJsLocation());
				workingCopy.setAttribute(IExternalToolConstants.ATTR_TOOL_ARGUMENTS, "/home/mistria/git/omnisharp-node-client/languageserver/server.js");
				workingCopy.setAttribute(ILaunchManager.ATTR_APPEND_ENVIRONMENT_VARIABLES, true);
				Map<String, String> environment = new HashMap<>(1);
				environment.put("LD_LIBRARY_PATH", "/home/mistria/apps/OmniSharp.NET/icu54:" + System.getenv("LD_LIBRARY_PATH"));
				workingCopy.setAttribute(ILaunchManager.ATTR_ENVIRONMENT_VARIABLES, environment);
				omniSharpLauch = workingCopy.doSave();
				registry.registerAssociation(contentTypeManager.getContentType("org.eclipse.lsp4e.languages.csharp"),
						LaunchConfigurationStreamProvider.findLaunchConfiguration(IExternalToolConstants.ID_PROGRAM_LAUNCH_CONFIGURATION_TYPE, InitializeLaunchConfigurations.OMNISHARP_NAME),
						Collections.singleton(ILaunchManager.RUN_MODE));
			}
		} catch (CoreException e) {
			e.printStackTrace();
		}
		// VSCode CSS
//		try {
//			String omniSharpLaunchName = VSCODE_CSS_NAME;
//			ILaunchConfiguration omniSharpLauch = null;
//			for (ILaunchConfiguration launch : launchManager.getLaunchConfigurations(externalType)) {
//				if (launch.getName().equals(omniSharpLaunchName)) {
//					omniSharpLauch = launch;
//				}
//			}
//			if (omniSharpLauch == null) {
//				ILaunchConfigurationWorkingCopy workingCopy = externalType.newInstance(null, omniSharpLaunchName);
//				// some common config
//				workingCopy.setAttribute(IExternalToolConstants.ATTR_LAUNCH_IN_BACKGROUND, true);
//				workingCopy.setAttribute(IExternalToolConstants.ATTR_BUILDER_ENABLED, false);
//				workingCopy.setAttribute(IExternalToolConstants.ATTR_SHOW_CONSOLE, false);
//				workingCopy.setAttribute(IExternalToolConstants.ATTR_BUILD_SCOPE, "${none}");
//				workingCopy.setAttribute(DebugPlugin.ATTR_CAPTURE_OUTPUT, false);
//				workingCopy.setAttribute(IExternalToolConstants.ATTR_LOCATION, getNodeJsLocation());
//				// Assume node is already installed on machine and uses it
//				// TODO: implement smarter and multi-platform discovery
//				workingCopy.setAttribute(IExternalToolConstants.ATTR_TOOL_ARGUMENTS, getVSCodeLocation("/resources/app/extensions/css/server/out/cssServerMain.js") + " --stdio");
//				omniSharpLauch = workingCopy.doSave();
//				registry.registerAssociation(contentTypeManager.getContentType("org.eclipse.lsp4e.languages.css"),
//						LaunchConfigurationStreamProvider.findLaunchConfiguration(IExternalToolConstants.ID_PROGRAM_LAUNCH_CONFIGURATION_TYPE, InitializeLaunchConfigurations.VSCODE_CSS_NAME),
//						Collections.singleton(ILaunchManager.RUN_MODE));
//				registry.registerAssociation(contentTypeManager.getContentType("org.eclipse.lsp4e.languages.less"),
//						LaunchConfigurationStreamProvider.findLaunchConfiguration(IExternalToolConstants.ID_PROGRAM_LAUNCH_CONFIGURATION_TYPE, InitializeLaunchConfigurations.VSCODE_CSS_NAME),
//						Collections.singleton(ILaunchManager.RUN_MODE));
//				registry.registerAssociation(contentTypeManager.getContentType("org.eclipse.lsp4e.languages.scss"),
//						LaunchConfigurationStreamProvider.findLaunchConfiguration(IExternalToolConstants.ID_PROGRAM_LAUNCH_CONFIGURATION_TYPE, InitializeLaunchConfigurations.VSCODE_CSS_NAME),
//						Collections.singleton(ILaunchManager.RUN_MODE));
//			}
//		} catch (CoreException e) {
//			e.printStackTrace();
//		}
//		List<String> commands = new ArrayList<>();
//		
//		commands.add(getNodeJsLocation());
//		commands.add(getVSCodeLocation("/resources/app/extensions/css/server/out/cssServerMain.js"));
//		commands.add("--stdio");
//		String workingDir = getVSCodeLocation("/resources/app/extensions/css/server/out");
//		StreamConnectionProvider provider = new ProcessStreamConnectionProvider(commands, workingDir);
//		registry.registerAssociation(contentTypeManager.getContentType("org.eclipse.lsp4e.languages.css"), provider);
		
		// VSCode CSS
		try {
			String omniSharpLaunchName = VSCODE_JSON_NAME;
			ILaunchConfiguration omniSharpLauch = null;
			for (ILaunchConfiguration launch : launchManager.getLaunchConfigurations(externalType)) {
				if (launch.getName().equals(omniSharpLaunchName)) {
					omniSharpLauch = launch;
				}
			}
			if (omniSharpLauch == null) {
				ILaunchConfigurationWorkingCopy workingCopy = externalType.newInstance(null, omniSharpLaunchName);
				// some common config
				workingCopy.setAttribute(IExternalToolConstants.ATTR_LAUNCH_IN_BACKGROUND, true);
				workingCopy.setAttribute(IExternalToolConstants.ATTR_BUILDER_ENABLED, false);
				workingCopy.setAttribute(IExternalToolConstants.ATTR_SHOW_CONSOLE, false);
				workingCopy.setAttribute(IExternalToolConstants.ATTR_BUILD_SCOPE, "${none}");
				workingCopy.setAttribute(DebugPlugin.ATTR_CAPTURE_OUTPUT, true);
				workingCopy.setAttribute(IExternalToolConstants.ATTR_LOCATION, getNodeJsLocation());
				// Assume node is already installed on machine and uses it
				// TODO: implement smarter and multi-platform discovery
				workingCopy.setAttribute(IExternalToolConstants.ATTR_TOOL_ARGUMENTS, getVSCodeLocation("/resources/app/extensions/json/server/out/jsonServerMain.js") + " --stdio");
				omniSharpLauch = workingCopy.doSave();
				registry.registerAssociation(contentTypeManager.getContentType("org.eclipse.lsp4e.languages.json"),
						LaunchConfigurationStreamProvider.findLaunchConfiguration(IExternalToolConstants.ID_PROGRAM_LAUNCH_CONFIGURATION_TYPE, InitializeLaunchConfigurations.VSCODE_JSON_NAME),
						Collections.singleton(ILaunchManager.RUN_MODE));
			}
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}

	public static String getVSCodeLocation(String appendPathSuffix) {
		String res = null;
		if (Platform.getOS().equals(Platform.OS_LINUX)) {
			res = "/usr/share/code";
		} else if (Platform.getOS().equals(Platform.OS_WIN32)) {
			res = "C:/Program Files (x86)/Microsoft VS Code";
		} else if (Platform.getOS().equals(Platform.OS_MACOSX)) {
			res = "/usr/share/code";
		}
		if (res != null && new File(res).isDirectory()) {
			if (res.contains(" ")) {
				return "\"" + res + appendPathSuffix + "\"";
			}
			return res + appendPathSuffix;
		}
		return "/unknown/path/to/VSCode" + appendPathSuffix;
	}

	public static String getNodeJsLocation() {
		String res = "/path/to/node";
		String[] command = new String[] {"/bin/bash", "-c", "which node"};
		if (Platform.getOS().equals(Platform.OS_WIN32)) {
			command = new String[] {"cmd", "/c", "where node"};
		}
		BufferedReader reader = null;
		try {
			Process p = Runtime.getRuntime().exec(command);
			reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
			res = reader.readLine();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			IOUtils.closeQuietly(reader);
		}
		return res;
	}

}
