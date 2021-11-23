/*******************************************************************************
 * Copyright (c) 2017 Kichwa Coders Ltd. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.debug.console;

import org.eclipse.core.runtime.ListenerList;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.debug.core.IStreamListener;
import org.eclipse.debug.core.model.IFlushableStreamMonitor;

public class DSPStreamMonitor implements IFlushableStreamMonitor {

	private final ListenerList<IStreamListener> listeners = new ListenerList<>();
	private final StringBuilder stream = new StringBuilder();
	private boolean buffer;

	@Override
	public String getContents() {
		return stream.toString();
	}

	@Override
	public void addListener(IStreamListener listener) {
		listeners.add(listener);
	}

	@Override
	public void removeListener(IStreamListener listener) {
		listeners.remove(listener);
	}

	public void append(String text) {
		if (buffer && text != null) {
			stream.append(text);
		}
		notifyAppend(text);
	}

	public void notifyAppend(String text) {
		if (text == null) {
			return;
		}
		for (IStreamListener listener : listeners) {
			SafeRunner.run(() -> listener.streamAppended(text, this));
		}
	}

	@Override
	public void flushContents() {
		stream.setLength(0);
		stream.trimToSize();
	}

	@Override
	public void setBuffered(boolean buffer) {
		this.buffer = buffer;
	}

	@Override
	public boolean isBuffered() {
		return buffer;
	}
}
