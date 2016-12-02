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
package org.eclipse.lsp4e;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class ProcessStreamConnectionProvider implements StreamConnectionProvider {

	private Process process;
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
		ProcessBuilder builder = new ProcessBuilder(getCommands());
		builder.directory(new File(getWorkingDirectory()));
		this.process = builder.start();
		if (!this.process.isAlive()) {
			throw new IOException("Unable to start language server: " + this.toString()); //$NON-NLS-1$
		}
	}

	@Override
	public InputStream getInputStream() {
		return process.getInputStream();
	}

	@Override
	public OutputStream getOutputStream() {
		return process.getOutputStream();
	}

	@Override
	public void stop() {
		process.destroy();
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
		if (getCommands().size() != other.getCommands().size()) {
			return false;
		}
		return this.getCommands().containsAll(other.getCommands()) && this.getWorkingDirectory().equals(other.getWorkingDirectory());
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		for (String s : this.getCommands()) {
			result = result * prime + s.hashCode();
		}
		return result ^ this.getWorkingDirectory().hashCode();
	}

}
