/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Mickael Istria (Red Hat Inc.) - initial implementation
 *   Sebastian Thomschke - re-implement StreamProxyInputStream for better performance
 *******************************************************************************/
package org.eclipse.lsp4e;

import static org.eclipse.lsp4e.internal.NullSafetyHelper.castNonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.IStreamListener;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IStreamMonitor;
import org.eclipse.debug.core.model.RuntimeProcess;
import org.eclipse.debug.internal.core.IInternalDebugCoreConstants;
import org.eclipse.debug.internal.core.Preferences;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4e.server.StreamConnectionProvider;

/**
 * Access and control IO streams from a Launch Configuration to connect them to
 * language server protocol client.
 */
public class LaunchConfigurationStreamProvider implements StreamConnectionProvider, IAdaptable {

	private @Nullable StreamProxyInputStream inputStream;
	private @Nullable StreamProxyInputStream errorStream;
	private @Nullable OutputStream outputStream;
	private @Nullable ILaunch launch;
	private @Nullable IProcess process;
	private final ILaunchConfiguration launchConfiguration;
	private Set<String> launchModes;

	protected static class StreamProxyInputStream extends InputStream implements IStreamListener {

		private static final int EOF = -1;
		private static final byte[] NO_DATA = new byte[0];

		private final ConcurrentLinkedQueue<byte[]> queue = new ConcurrentLinkedQueue<>();
		private final IProcess process;
		private byte[] currentBuffer = NO_DATA;
		private int currentBufferPos = 0;

		public StreamProxyInputStream(IProcess process) {
			this.process = process;
		}

		@Override
		public void streamAppended(final String text, final IStreamMonitor monitor) {
			final byte[] bytes = text.getBytes();
			if (bytes.length > 0) {
				queue.offer(bytes);
			}
		}

		@Override
		public int read() throws IOException {
			if (currentBufferPos >= currentBuffer.length && !fillCurrentBuffer()) {
				return EOF;
			}
			return currentBuffer[currentBufferPos++] & 0xFF;
		}

		@Override
		public int read(final byte[] buf, final int off, final int len) throws IOException {
			Objects.checkFromIndexSize(off, len, buf.length);
			if (len == 0) {
				return 0;
			}

			int totalBytesRead = 0;
			while (totalBytesRead < len) {
				if (currentBufferPos >= currentBuffer.length && !fillCurrentBuffer()) {
					return totalBytesRead == 0 ? EOF : totalBytesRead;
				}

				final int bytesToRead = Math.min(len - totalBytesRead, currentBuffer.length - currentBufferPos);
				System.arraycopy(currentBuffer, currentBufferPos, buf, off + totalBytesRead, bytesToRead);
				currentBufferPos += bytesToRead;
				totalBytesRead += bytesToRead;
			}
			return totalBytesRead;
		}

		@Override
		public int available() throws IOException {
			return (currentBuffer.length - currentBufferPos) + queue.stream().mapToInt(arr -> arr.length).sum();
		}

		private boolean fillCurrentBuffer() throws IOException {
			try {
				while (queue.isEmpty()) {
					if (process.isTerminated()) {
						return false;
					}
					Thread.sleep(5);
				}
				currentBuffer = queue.remove();
				currentBufferPos = 0;
				return currentBuffer.length > 0;
			} catch (final InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new IOException("Thread interrupted while reading.", ex); //$NON-NLS-1$
			}
		}
	}

	public LaunchConfigurationStreamProvider(ILaunchConfiguration launchConfig, @Nullable Set<String> launchModes) {
		super();
		Assert.isNotNull(launchConfig);
		this.launchConfiguration = launchConfig;
		if (launchModes != null) {
			this.launchModes = launchModes;
		} else {
			this.launchModes = Collections.singleton(ILaunchManager.RUN_MODE);
		}
	}

	@Override
	public boolean equals(@Nullable Object obj) {
		if (obj == this) {
			return true;
		}
		return obj instanceof LaunchConfigurationStreamProvider other && //
				this.launchConfiguration.equals(other.launchConfiguration) && //
				this.launchModes.equals(other.launchModes);
	}

	@Override
	public int hashCode() {
		return this.launchConfiguration.hashCode() ^ this.launchModes.hashCode();
	}

	public static @Nullable ILaunchConfiguration findLaunchConfiguration(String typeId, String name) {
		ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
		ILaunchConfigurationType type = manager.getLaunchConfigurationType(typeId);
		ILaunchConfiguration res = null;
		try {
			for (ILaunchConfiguration launch : manager.getLaunchConfigurations(type)) {
				if (launch.getName().equals(name)) {
					res = launch;
				}
			}
		} catch (CoreException e) {
			LanguageServerPlugin.logError(e);
		}
		return res;
	}

	@Override
	public void start() throws IOException {
		// Disable status handler to avoid starting launch in an UI Thread which freezes
		// the IDE when Outline is displayed.
		boolean statusHandlerToUpdate = disableStatusHandler();
		try {
			final var launch = this.launch = this.launchConfiguration.launch(this.launchModes.iterator().next(),
					new NullProgressMonitor(), false);
			long initialTimestamp = System.currentTimeMillis();
			while (launch.getProcesses().length == 0 && System.currentTimeMillis() - initialTimestamp < 5000) {
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
					LanguageServerPlugin.logError(e);
					Thread.currentThread().interrupt();
				}
			}
			if (launch.getProcesses().length > 0) {
				final var process = this.process = launch.getProcesses()[0];
				final var inputStream = this.inputStream = new StreamProxyInputStream(process);
				final var proxy = process.getStreamsProxy();
				if (proxy != null) {
					final var mon = proxy.getOutputStreamMonitor();
					if (mon != null) {
						mon.addListener(inputStream);
					}
				}
				// TODO: Ugly hack, find something better to retrieve stream!
				try {
					Method systemProcessGetter = RuntimeProcess.class.getDeclaredMethod("getSystemProcess"); //$NON-NLS-1$
					systemProcessGetter.setAccessible(true);
					final var systemProcess = (Process) systemProcessGetter.invoke(process);
					this.outputStream = castNonNull(systemProcess).getOutputStream();
				} catch (ReflectiveOperationException ex) {
					LanguageServerPlugin.logError(ex);
				}
				final var errorStream = this.errorStream = new StreamProxyInputStream(process);
				if (proxy != null) {
					final var mon = proxy.getErrorStreamMonitor();
					if (mon != null) {
						mon.addListener(errorStream);
					}
				}
			}
		} catch (Exception e) {
			LanguageServerPlugin.logError(e);
		} finally {
			if (statusHandlerToUpdate) {
				setStatusHandler(true);
			}
		}
	}

	/**
	 * Disable status handler while launch is done.
	 *
	 * @return true if the status handler preferences was <code>enabled</code> and
	 *         <code>false</code> otherwise.
	 */
	private boolean disableStatusHandler() {
		boolean enabled = Platform.getPreferencesService().getBoolean(DebugPlugin.getUniqueIdentifier(),
				IInternalDebugCoreConstants.PREF_ENABLE_STATUS_HANDLERS, false, null);
		if (enabled) {
			setStatusHandler(false);
		}
		return enabled;
	}

	/**
	 * Update the the status handler preferences
	 *
	 * @param enabled
	 *            the status handler preferences
	 */
	private void setStatusHandler(boolean enabled) {
		Preferences.setBoolean(DebugPlugin.getUniqueIdentifier(),
				IInternalDebugCoreConstants.PREF_ENABLE_STATUS_HANDLERS, enabled, null);
	}

	@Override
	public <T> @Nullable T getAdapter(@Nullable Class<T> adapter) {
		if (adapter == ProcessHandle.class && process != null) {
			return process.getAdapter(adapter);
		}
		return null;
	}

	@Override
	public @Nullable InputStream getInputStream() {
		return this.inputStream;
	}

	@Override
	public @Nullable OutputStream getOutputStream() {
		return this.outputStream;
	}

	@Override
	public @Nullable InputStream getErrorStream() {
		return this.errorStream;
	}

	@Override
	public void stop() {
		final var launch = this.launch;
		if (launch == null) {
			return;
		}
		try {
			launch.terminate();
			for (IProcess p : launch.getProcesses()) {
				p.terminate();
			}
		} catch (DebugException e1) {
			LanguageServerPlugin.logError(e1);
		}
		this.launch = null;
		this.process = null;
		try {
			if (this.inputStream != null) {
				this.inputStream.close();
			}
		} catch (IOException e) {
			LanguageServerPlugin.logError(e);
		}
		try {
			if (this.errorStream != null) {
				this.errorStream.close();
			}
		} catch (IOException e) {
			LanguageServerPlugin.logError(e);
		}
		this.inputStream = null;
		this.outputStream = null;
		this.errorStream = null;
	}
}
