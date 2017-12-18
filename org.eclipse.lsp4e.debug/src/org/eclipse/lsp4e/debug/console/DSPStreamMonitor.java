/*******************************************************************************
 * Copyright (c) 2017 Kichwa Coders Ltd. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.lsp4e.debug.console;

import org.eclipse.core.runtime.ListenerList;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.debug.core.IStreamListener;
import org.eclipse.debug.core.model.IFlushableStreamMonitor;

public class DSPStreamMonitor implements IFlushableStreamMonitor {

	private ListenerList<IStreamListener> listeners = new ListenerList<>();
	private StringBuffer stream = new StringBuffer();
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
		if (buffer) {
			if (text != null) {
				stream.append(text);
			}
		}
		notifyAppend(text);
	}

	public void notifyAppend(String text) {
		if (text == null) {
			return;
		}
		for (IStreamListener listener : listeners) {
			SafeRunner.run(() -> {
				listener.streamAppended(text, this);
			});
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
