/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - Initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.utils;

import java.io.File;
import java.net.URLClassLoader;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.externaltools.internal.IExternalToolConstants;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentTypeManager;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServersRegistry;
import org.eclipse.lsp4e.LaunchConfigurationStreamProvider;
import org.eclipse.lsp4e.test.utils.mock.MockLanguageServer;
import org.eclipse.ui.IStartup;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;

@SuppressWarnings("restriction")
public class CreateAndRegisterContentTypeLSPLaunchConfigMapping implements IStartup {

	@Override
	public void earlyStartup() {
		IContentTypeManager contentTypeManager = Platform.getContentTypeManager();
		ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager();
		ILaunchConfigurationType externalType = launchManager.getLaunchConfigurationType(IExternalToolConstants.ID_PROGRAM_LAUNCH_CONFIGURATION_TYPE);
		LanguageServersRegistry registry = LanguageServersRegistry.getInstance();
		try {
			String externalProcessLaunchName = "Mock external LS";
			ILaunchConfiguration mockServerLauch = null;
			for (ILaunchConfiguration launch : launchManager.getLaunchConfigurations(externalType)) {
				if (launch.getName().equals(externalProcessLaunchName)) {
					mockServerLauch = launch;
				}
			}
			if (mockServerLauch == null) {
				ILaunchConfigurationWorkingCopy workingCopy = externalType.newInstance(null, externalProcessLaunchName);
				// some common config
				workingCopy.setAttribute(IExternalToolConstants.ATTR_LAUNCH_IN_BACKGROUND, true);
				workingCopy.setAttribute(IExternalToolConstants.ATTR_BUILDER_ENABLED, false);
				workingCopy.setAttribute(IExternalToolConstants.ATTR_SHOW_CONSOLE, false);
				workingCopy.setAttribute(IExternalToolConstants.ATTR_BUILD_SCOPE, "${none}");
				workingCopy.setAttribute(DebugPlugin.ATTR_CAPTURE_OUTPUT, true);
				String exe = "";
				if (Platform.OS_WIN32.equals(Platform.getOS())) {
					exe = ".exe";
				}
				workingCopy.setAttribute(IExternalToolConstants.ATTR_LOCATION, new File(System.getProperty("java.home"),"bin/java" + exe).getAbsolutePath());
				workingCopy.setAttribute(IExternalToolConstants.ATTR_TOOL_ARGUMENTS, "-cp " +
						getClassPath(MockLanguageServer.class, Platform.getBundle("org.eclipse.lsp4e.test")) + " " + 
						MockLanguageServer.class.getName());
				mockServerLauch = workingCopy.doSave();
				registry.registerAssociation(contentTypeManager.getContentType("org.eclipse.lsp4e.test.content-type2"),
						LaunchConfigurationStreamProvider.findLaunchConfiguration(IExternalToolConstants.ID_PROGRAM_LAUNCH_CONFIGURATION_TYPE, mockServerLauch.getName()),
						Collections.singleton(ILaunchManager.RUN_MODE));
			}
		} catch (CoreException e) {
			LanguageServerPlugin.logError(e);
		}

	}

	private String getClassPath(Class<?> clazz, final Bundle... additionalBundles) {
		ClassLoader loader = clazz.getClassLoader();
		if (loader instanceof URLClassLoader urlClassLoader) {
			return Arrays.asList(urlClassLoader.getURLs()).stream().map(url -> url.getFile()).collect(Collectors.joining(System.getProperty("path.separator")));
		}
		final var toProcess = new ArrayDeque<>(List.of(additionalBundles));
		Set<Bundle> processed = new HashSet<>();
		Bundle current = FrameworkUtil.getBundle(clazz);
		if (current != null) {
			toProcess.add(current);
		}
		while (!toProcess.isEmpty()) {
			current = toProcess.pop();
			if (processed.contains(current)) {
				continue;
			}
			for (BundleWire dep : current.adapt(BundleWiring.class).getRequiredWires(null)) {
				toProcess.add(dep.getProvider().getBundle());
			}
			processed.add(current);
		}
		return processed.stream()
				.filter(bundle -> bundle.getBundleId() != 0)
				.map(bundle -> FileLocator.getBundleFileLocation(bundle).orElse(null))
				.flatMap(location -> Arrays.stream(location.isFile()
						? new String[] { location.getAbsolutePath() }
						: new String[] { location.getAbsolutePath(), new File(location, "bin").getAbsolutePath() }))
				.collect(Collectors.joining(System.getProperty("path.separator")));
	}
}
