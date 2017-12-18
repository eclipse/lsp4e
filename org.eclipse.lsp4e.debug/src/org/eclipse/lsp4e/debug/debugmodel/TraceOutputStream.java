/*******************************************************************************
 * Copyright (c) 2017 Kichwa Coders Ltd. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.lsp4e.debug.debugmodel;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class TraceOutputStream extends FilterOutputStream {

	private OutputStream trace;

	public TraceOutputStream(OutputStream out, OutputStream trace) {
		super(out);
		this.trace = trace;
	}

	@Override
	public void write(int b) throws IOException {
		trace.write(b);
		trace.flush();
		out.write(b);
	}

	@Override
	public void write(byte b[], int off, int len) throws IOException {
		trace.write(b, off, len);
		trace.flush();
		out.write(b, off, len);
	}
}