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
 *******************************************************************************/
package org.eclipse.lsp4e;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
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
import org.eclipse.lsp4e.server.StreamConnectionProvider;

/**
 * Access and control IO streams from a Launch Configuration to connect
 * them to language server protocol client.
 */
public class LaunchConfigurationStreamProvider implements StreamConnectionProvider  {

	private StreamProxyInputStream inputStream;
	private StreamProxyInputStream errorStream;
	private OutputStream outputStream;
	private ILaunch launch;
	private IProcess process;
	private final ILaunchConfiguration launchConfiguration;
	private Set<String> launchModes;

	protected static class StreamProxyInputStream extends InputStream implements IStreamListener {

		private final ConcurrentLinkedQueue<Byte> queue = new ConcurrentLinkedQueue<>();
		private final IProcess process;

		public StreamProxyInputStream(IProcess process) {
			this.process = process;
		}

		@Override
		public void streamAppended(String text, IStreamMonitor monitor) {
			byte[] bytes = text.getBytes(Charset.defaultCharset());
			List<Byte> bytesAsList = new ArrayList<>(bytes.length);
			for (byte b : bytes) {
				bytesAsList.add(b);
			}
			queue.addAll(bytesAsList);
		}

		@Override
		public int read() throws IOException {
			while (queue.isEmpty()) {
				if (this.process.isTerminated()) {
					return -1;
				}
				try {
					Thread.sleep(5, 0);
				} catch (InterruptedException e) {
					LanguageServerPlugin.logError(e);
					Thread.currentThread().interrupt();
				}
			}
			return queue.poll();
		}

		@Override
		public int available() throws IOException {
			return queue.size();
		}

	}

	public LaunchConfigurationStreamProvider(ILaunchConfiguration launchConfig, Set<String> launchModes) {
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
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof LaunchConfigurationStreamProvider)) {
			return false;
		}
		LaunchConfigurationStreamProvider other = (LaunchConfigurationStreamProvider)obj;
		return this.launchConfiguration.equals(other.launchConfiguration) && this.launchModes.equals(other.launchModes);
	}

	@Override
	public int hashCode() {
		return this.launchConfiguration.hashCode() ^ this.launchModes.hashCode();
	}

	public static ILaunchConfiguration findLaunchConfiguration(String typeId, String name) {
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
			this.launch = this.launchConfiguration.launch(this.launchModes.iterator().next(), new NullProgressMonitor(),
					false);
			long initialTimestamp = System.currentTimeMillis();
			while (this.launch.getProcesses().length == 0 && System.currentTimeMillis() - initialTimestamp < 5000) {
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
					LanguageServerPlugin.logError(e);
					Thread.currentThread().interrupt();
				}
			}
			if (this.launch.getProcesses().length > 0) {
				this.process = this.launch.getProcesses()[0];
				this.inputStream = new StreamProxyInputStream(process);
				process.getStreamsProxy().getOutputStreamMonitor().addListener(this.inputStream);
				// TODO: Ugly hack, find something better to retrieve stream!
				try {
					Method systemProcessGetter = RuntimeProcess.class.getDeclaredMethod("getSystemProcess"); //$NON-NLS-1$
					systemProcessGetter.setAccessible(true);
					Process systemProcess = (Process) systemProcessGetter.invoke(process);
					this.outputStream = systemProcess.getOutputStream();
				} catch (ReflectiveOperationException ex) {
					LanguageServerPlugin.logError(ex);
				}
				this.errorStream = new StreamProxyInputStream(process);
				process.getStreamsProxy().getErrorStreamMonitor().addListener(this.errorStream);
			}
		} catch (Exception e) {
			LanguageServerPlugin.logError(e);
		}
		finally {
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
	public InputStream getInputStream() {
		return this.inputStream;
	}

	@Override
	public OutputStream getOutputStream() {
		return this.outputStream;
	}

	@Override
	public InputStream getErrorStream() {
		return this.errorStream;
	}

	@Override
	public void stop() {
		if (this.launch == null) {
			return;
		}
		try {
			this.launch.terminate();
			for (IProcess p : this.launch.getProcesses()) {
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
