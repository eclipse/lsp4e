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
package org.eclipse.lsp4e.server;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;

import org.eclipse.jdt.annotation.Nullable;

/**
 *
 * @since 0.1.0
 */
public abstract class ProcessStreamConnectionProvider implements StreamConnectionProvider {

	private @Nullable Process process;
	private List<String> commands;
	private String workingDir;

	public ProcessStreamConnectionProvider() {
	}

	public ProcessStreamConnectionProvider(List<String> commands, String workingDir) {
		this.commands = commands;
		this.workingDir = workingDir;
	}

	@Override
	public void start() throws IOException {
		if (this.workingDir == null || this.commands == null || this.commands.isEmpty() || this.commands.stream().anyMatch(Objects::isNull)) {
			throw new IOException("Unable to start language server: " + this.toString()); //$NON-NLS-1$
		}

		ProcessBuilder builder = createProcessBuilder();
		Process p = builder.start();
		this.process = p;
		if (!p.isAlive()) {
			throw new IOException("Unable to start language server: " + this.toString()); //$NON-NLS-1$
		}
	}

	protected ProcessBuilder createProcessBuilder() {
		ProcessBuilder builder = new ProcessBuilder(getCommands());
		builder.directory(new File(getWorkingDirectory()));
		builder.redirectError(ProcessBuilder.Redirect.INHERIT);
		return builder;
	}

	@Override
	public @Nullable InputStream getInputStream() {
		Process p = process;
		return p == null ? null : p.getInputStream();
	}

	@Override
	public @Nullable InputStream getErrorStream() {
		Process p = process;
		return p == null ? null : p.getErrorStream();
	}

	@Override
	public @Nullable OutputStream getOutputStream() {
		Process p = process;
		return p == null ? null : p.getOutputStream();
	}

	@Override
	public void stop() {
		Process p = process;
		if (p != null) {
			p.destroy();
		}
	}

	protected List<String> getCommands() {
		return commands;
	}

	public void setCommands(List<String> commands) {
		this.commands = commands;
	}

	protected String getWorkingDirectory() {
		return workingDir;
	}

	public void setWorkingDirectory(String workingDir) {
		this.workingDir = workingDir;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof ProcessStreamConnectionProvider)) {
			return false;
		}
		ProcessStreamConnectionProvider other = (ProcessStreamConnectionProvider) obj;
		return Objects.equals(this.getCommands(), other.getCommands())
				&& Objects.equals(this.getWorkingDirectory(), other.getWorkingDirectory());
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.getCommands(), this.getWorkingDirectory());
	}

}
