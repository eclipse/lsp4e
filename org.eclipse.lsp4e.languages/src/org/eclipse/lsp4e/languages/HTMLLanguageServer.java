/*******************************************************************************
 * Copyright (c) 2016 Rogue Wave Software Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.languages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.lsp4e.ProcessStreamConnectionProvider;

public class HTMLLanguageServer extends ProcessStreamConnectionProvider {

	public HTMLLanguageServer() {
		List<String> commands = new ArrayList<>();
		commands.add(InitializeLaunchConfigurations.getNodeJsLocation());
		commands.add(InitializeLaunchConfigurations.getVSCodeLocation("/resources/app/extensions/html/server/out/htmlServerMain.js"));
		commands.add("--stdio");
		String workingDir = InitializeLaunchConfigurations.getVSCodeLocation("/resources/app/extensions/html/server/out");
		setCommands(commands);
		setWorkingDirectory(workingDir);
	}

	@Override
	public Object getInitializationOptions(String rootPath) {
		return Collections.singletonMap("css", true);
	}
	
	@Override
	public String toString() {
		return "HTML Language Server: " + super.toString();
	}
}
