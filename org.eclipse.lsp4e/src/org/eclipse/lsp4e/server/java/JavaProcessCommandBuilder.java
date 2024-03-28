/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Angelo ZERR (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.server.java;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.Platform;

/**
 * A builder to create Java process command.
 */
public class JavaProcessCommandBuilder {

    private String javaPath;

    private String debugPort;

    private boolean debugSuspend;
    private String jar;

    private String cp;

    public JavaProcessCommandBuilder() {
        setJavaPath(computeJavaPath());
    }

    public JavaProcessCommandBuilder setJavaPath(String javaPath) {
        this.javaPath = javaPath;
        return this;
    }

    public JavaProcessCommandBuilder setDebugPort(String debugPort) {
        this.debugPort = debugPort;
        return this;
    }

    public JavaProcessCommandBuilder setDebugSuspend(boolean debugSuspend) {
        this.debugSuspend = debugSuspend;
        return this;
    }

    public JavaProcessCommandBuilder setJar(String jar) {
        this.jar = jar;
        return this;
    }

    public JavaProcessCommandBuilder setCp(String cp) {
        this.cp = cp;
        return this;
    }

    public List<String> create() {
        List<String> commands = new ArrayList<>();
        commands.add(javaPath);
        if (debugPort != null && !debugPort.isEmpty()) {
            String suspend = debugSuspend ? "y" : "n"; //$NON-NLS-1$ //$NON-NLS-2$
            commands.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=" + suspend + ",address=" + debugPort); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (jar != null) {
            commands.add("-jar"); //$NON-NLS-1$
            commands.add(jar);
        }
        if (cp != null) {
            commands.add("-cp"); //$NON-NLS-1$
            commands.add(cp);
        }
        return commands;
    }

    private static String computeJavaPath() {
    	return new File(System.getProperty("java.home"), //$NON-NLS-1$
				"bin/java" + (Platform.getOS().equals(Platform.OS_WIN32) ? ".exe" : "")).getAbsolutePath(); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }
}
