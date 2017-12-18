/*******************************************************************************
 * Copyright (c) 2017 Kichwa Coders Ltd. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.lsp4e.debug.debugmodel;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class TraceInputStream extends FilterInputStream {
	private OutputStream trace;

	public TraceInputStream(InputStream in, OutputStream trace) {
		super(in);
		this.trace = trace;
	}

	@Override
	public int read() throws IOException {
		int b = in.read();
		trace.write(b);
		trace.flush();
		return b;
	}

	@Override
	public int read(byte b[], int off, int len) throws IOException {
		int n = in.read(b, off, len);
		trace.write(b, off, n);
		trace.flush();
		return n;
	}
}
