/*******************************************************************************
 * Copyright (c) 2017, 2019 Kichwa Coders Ltd. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.debug.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.ILaunchConfigurationDelegate;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.lsp4e.debug.DSPPlugin;
import org.eclipse.lsp4e.debug.debugmodel.DSPDebugTarget;
import org.eclipse.osgi.util.NLS;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class DSPLaunchDelegate implements ILaunchConfigurationDelegate {

	/**
	 * Structured arguments for the
	 * {@link DSPLaunchDelegate#launch(DSPLaunchDelegateLaunchBuilder) method.
	 *
	 * Use this class to simplify calling the launch method and make the source code
	 * of consumers a little easier to read.
	 */
	public static class DSPLaunchDelegateLaunchBuilder {
		ILaunchConfiguration configuration;
		String mode;
		ILaunch launch;
		IProgressMonitor monitor;
		boolean launchNotConnect;
		String debugCmd;
		List<String> debugCmdArgs;
		String[] debugCmdEnvVars;
		boolean monitorDebugAdapter;
		String server;
		int port;
		Map<String, Object> dspParameters;

		private DSPLaunchDelegateLaunchBuilder(DSPLaunchDelegateLaunchBuilder other) {
			this.configuration = other.configuration;
			this.mode = other.mode;
			this.launch = other.launch;
			this.monitor = other.monitor;

			this.launchNotConnect = other.launchNotConnect;

			this.debugCmd = other.debugCmd;
			this.debugCmdArgs = other.debugCmdArgs == null ? null : new ArrayList<>(other.debugCmdArgs);
			this.monitorDebugAdapter = other.monitorDebugAdapter;

			this.server = other.server;
			this.port = other.port;

			this.dspParameters = new HashMap<>(other.dspParameters);
		}

		/**
		 * Construct a builder using the normal arguments to
		 * {@link ILaunchConfigurationDelegate#launch(ILaunchConfiguration, String, ILaunch, IProgressMonitor)}
		 *
		 * @param configuration
		 * @param mode
		 * @param launch
		 * @param monitor
		 */
		public DSPLaunchDelegateLaunchBuilder(ILaunchConfiguration configuration, String mode, ILaunch launch,
				IProgressMonitor monitor) {
			this.configuration = configuration;
			this.mode = mode;
			this.launch = launch;
			this.monitor = monitor;
		}

		public DSPLaunchDelegateLaunchBuilder setLaunchDebugAdapter(String debugCmd, List<String> debugCmdArgs) {
			return setLaunchDebugAdapter(debugCmd, debugCmdArgs, (String[]) null);
		}

		public DSPLaunchDelegateLaunchBuilder setLaunchDebugAdapter(String debugCmd, List<String> debugCmdArgs,
				Map<String, String> envVars) {
			return setLaunchDebugAdapter(debugCmd, debugCmdArgs, envVars == null ? null
					: envVars.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toArray(String[]::new));
		}

		public DSPLaunchDelegateLaunchBuilder setLaunchDebugAdapter(String debugCmd, List<String> debugCmdArgs,
				String[] envVars) {
			this.launchNotConnect = true;
			this.debugCmd = debugCmd;
			this.debugCmdArgs = debugCmdArgs;
			this.debugCmdEnvVars = envVars;
			return this;
		}

		public DSPLaunchDelegateLaunchBuilder setAttachDebugAdapter(String server, int port) {
			this.launchNotConnect = false;
			this.server = server;
			this.port = port;
			return this;
		}

		public DSPLaunchDelegateLaunchBuilder setMonitorDebugAdapter(boolean monitorDebugAdapter) {
			this.monitorDebugAdapter = monitorDebugAdapter;
			return this;
		}

		public DSPLaunchDelegateLaunchBuilder setDspParameters(Map<String, Object> dspParameters) {
			this.dspParameters = dspParameters;
			return this;
		}

		public ILaunchConfiguration getConfiguration() {
			return configuration;
		}

		public String getMode() {
			return mode;
		}

		public ILaunch getLaunch() {
			return launch;
		}

		public IProgressMonitor getMonitor() {
			return monitor;
		}

		public boolean isLaunchNotConnect() {
			return launchNotConnect;
		}

		public String getDebugCmd() {
			return debugCmd;
		}

		public List<String> getDebugCmdArgs() {
			return debugCmdArgs;
		}

		public String[] getDebugCmdEnvVars() {
			return debugCmdEnvVars;
		}

		public boolean isMonitorDebugAdapter() {
			return monitorDebugAdapter;
		}

		public String getServer() {
			return server;
		}

		public int getPort() {
			return port;
		}

		public Map<String, Object> getDspParameters() {
			return dspParameters;
		}

		@Override
		public String toString() {
			return "DSPLaunchDelegateLaunchBuilder [configuration=" + configuration + ", mode=" + mode + ", launch="
					+ launch + ", monitor=" + monitor + ", launchNotConnect=" + launchNotConnect + ", debugCmd="
					+ debugCmd + ", debugCmdArgs=" + debugCmdArgs + ", debugCmdEnvVars=" + List.of(debugCmdEnvVars)
					+ ", monitorDebugAdapter=" + monitorDebugAdapter + ", server=" + server + ", port=" + port
					+ ", dspParameters=" + dspParameters + "]";
		}
	}

	@Override
	public void launch(ILaunchConfiguration configuration, String mode, ILaunch launch, IProgressMonitor monitor)
			throws CoreException {
		final var builder = new DSPLaunchDelegateLaunchBuilder(configuration, mode, launch, monitor);
		builder.launchNotConnect = DSPPlugin.DSP_MODE_LAUNCH
				.equals(configuration.getAttribute(DSPPlugin.ATTR_DSP_MODE, DSPPlugin.DSP_MODE_LAUNCH));
		builder.debugCmd = configuration.getAttribute(DSPPlugin.ATTR_DSP_CMD, (String) null);
		builder.debugCmdArgs = configuration.getAttribute(DSPPlugin.ATTR_DSP_ARGS, (List<String>) null);
		builder.debugCmdEnvVars = DebugPlugin.getDefault().getLaunchManager().getEnvironment(configuration);
		builder.monitorDebugAdapter = configuration.getAttribute(DSPPlugin.ATTR_DSP_MONITOR_DEBUG_ADAPTER, false);
		builder.server = configuration.getAttribute(DSPPlugin.ATTR_DSP_SERVER_HOST, (String) null);
		builder.port = configuration.getAttribute(DSPPlugin.ATTR_DSP_SERVER_PORT, 0);

		String dspParametersJson = configuration.getAttribute(DSPPlugin.ATTR_DSP_PARAM, (String) null);
		builder.dspParameters = jsonToMap(dspParametersJson);

		launch(builder);
	}

	private Map<String, Object> jsonToMap(String dspParametersJson) {
		final var gson = new Gson();
		final var type = new TypeToken<Map<String, Object>>() {
		}.getType();
		Map<String, Object> dspParameters = gson.fromJson(dspParametersJson, type);
		if (dspParameters == null) {
			dspParameters = new HashMap<>();
		}
		return dspParameters;
	}

	public void launch(DSPLaunchDelegateLaunchBuilder builder) throws CoreException {
		// Make a copy so we can modify locally as needed.
		builder = new DSPLaunchDelegateLaunchBuilder(builder);
		final var subMonitor = SubMonitor.convert(builder.monitor, 100);
		builder.dspParameters = new HashMap<>(builder.dspParameters);

		boolean customDebugAdapter = builder.configuration.getAttribute(DSPPlugin.ATTR_CUSTOM_DEBUG_ADAPTER, false);
		boolean customLaunchParameters = builder.configuration.getAttribute(DSPPlugin.ATTR_CUSTOM_LAUNCH_PARAMS, false);

		if (customDebugAdapter) {
			builder.launchNotConnect = DSPPlugin.DSP_MODE_LAUNCH
					.equals(builder.configuration.getAttribute(DSPPlugin.ATTR_DSP_MODE, DSPPlugin.DSP_MODE_LAUNCH));
			builder.debugCmd = builder.configuration.getAttribute(DSPPlugin.ATTR_DSP_CMD, (String) null);
			builder.debugCmdArgs = builder.configuration.getAttribute(DSPPlugin.ATTR_DSP_ARGS, (List<String>) null);
			builder.debugCmdEnvVars = DebugPlugin.getDefault().getLaunchManager().getEnvironment(builder.configuration);
			builder.monitorDebugAdapter = builder.configuration.getAttribute(DSPPlugin.ATTR_DSP_MONITOR_DEBUG_ADAPTER,
					false);
			builder.server = builder.configuration.getAttribute(DSPPlugin.ATTR_DSP_SERVER_HOST, (String) null);
			builder.port = builder.configuration.getAttribute(DSPPlugin.ATTR_DSP_SERVER_PORT, 0);
		}
		if (customLaunchParameters) {
			String dspParametersJson = builder.configuration.getAttribute(DSPPlugin.ATTR_DSP_PARAM, (String) null);
			Map<String, Object> customParams = jsonToMap(dspParametersJson);
			builder.dspParameters.putAll(customParams);
		}

		// DSP supports run/debug as a simple flag to the debug server.
		// See LaunchRequestArguments.noDebug
		if (ILaunchManager.DEBUG_MODE.equals(builder.mode)) {
			subMonitor.setTaskName("Starting debug session");
			builder.dspParameters.put("noDebug", false);
		} else if (ILaunchManager.RUN_MODE.equals(builder.mode)) {
			subMonitor.setTaskName("Starting run session");
			builder.dspParameters.put("noDebug", true);
		} else {
			abort(NLS.bind("Unsupported launch mode '{0}'.", builder.mode), null);
		}

		Runnable cleanup;
		InputStream inputStream;
		OutputStream outputStream;
		try {

			if (builder.launchNotConnect) {
				final var command = new ArrayList<String>();

				if (builder.debugCmd == null) {
					abort("Debug command unspecified.", null); //$NON-NLS-1$
				}
				command.add(builder.debugCmd);
				if (builder.debugCmdArgs != null && !builder.debugCmdArgs.isEmpty()) {
					command.addAll(builder.debugCmdArgs);
				}

				subMonitor
						.subTask(NLS.bind("Launching debug adapter: {0}", "\"" + String.join("\" \"", command) + "\""));
				Process debugAdapterProcess = DebugPlugin.exec(command.toArray(String[]::new), null,
						builder.debugCmdEnvVars);
				if (builder.monitorDebugAdapter) {
					// Uglish workaround: should instead have a dedicated ProcessFactory reading
					// process attribute rather than launch one
					String initialCaptureOutput = builder.launch.getAttribute(DebugPlugin.ATTR_CAPTURE_OUTPUT);
					builder.launch.setAttribute(DebugPlugin.ATTR_CAPTURE_OUTPUT, Boolean.toString(true));
					IProcess debugAdapterIProcess = DebugPlugin.newProcess(builder.launch, debugAdapterProcess,
							"Debug Adapter");
					builder.launch.setAttribute(DebugPlugin.ATTR_CAPTURE_OUTPUT, initialCaptureOutput);

					final var bytes = Collections.synchronizedList(new LinkedList<Byte>());
					inputStream = new InputStream() {
						@Override
						public int read() throws IOException {
							while (debugAdapterProcess.isAlive()) {
								if (!bytes.isEmpty()) {
									return bytes.remove(0);
								} else {
									try {
										Thread.sleep(50);
									} catch (InterruptedException e) {
										Thread.currentThread().interrupt();
										DSPPlugin.logError(e);
									}
								}
							}
							return -1;
						}
					};
					DSPLaunchDelegateLaunchBuilder finalBuilder = builder;
					debugAdapterIProcess.getStreamsProxy().getOutputStreamMonitor().addListener((text, monitor) -> {
						try {
							for (byte b : text
									.getBytes(finalBuilder.launch.getAttribute(DebugPlugin.ATTR_CONSOLE_ENCODING))) {
								bytes.add(b);
							}
						} catch (IOException e) {
							DSPPlugin.logError(e);
						}
					});
					outputStream = new OutputStream() {
						@Override
						public void write(int b) throws IOException {
							debugAdapterIProcess.getStreamsProxy().write(new String(new byte[] { (byte) b }));
						}
					};
					cleanup = () -> {
						try {
							debugAdapterIProcess.terminate();
							debugAdapterProcess.destroy();
						} catch (DebugException e) {
							DSPPlugin.logError(e);
						}
					};
				} else {
					inputStream = debugAdapterProcess.getInputStream();
					outputStream = debugAdapterProcess.getOutputStream();
					cleanup = debugAdapterProcess::destroyForcibly;
				}
			} else {

				if (builder.server == null) {
					abort("Debug server host unspecified.", null); //$NON-NLS-1$
				}

				if (builder.port < 1 || builder.port > 65535) {
					abort("Debug server port unspecified or out of range 1-65535.", null); //$NON-NLS-1$
				}
				subMonitor.subTask(NLS.bind("Connecting to debug adapter: {0}:{1}", builder.server, builder.port));
				Socket socket = new Socket(builder.server, builder.port);
				inputStream = socket.getInputStream();
				outputStream = socket.getOutputStream();
				cleanup = () -> {
					try {
						socket.close();
					} catch (IOException e) {
						// ignore inner resource exception
					}
				};
			}

			subMonitor.setWorkRemaining(80);

			ILaunch launch = builder.launch;
			Map<String, Object> dspParameters = builder.dspParameters;
			IDebugTarget target = createDebugTarget(subMonitor, cleanup, inputStream, outputStream, launch,
					dspParameters);
			builder.launch.addDebugTarget(target);
		} catch (IOException | OperationCanceledException e1) {
			abort("Failed to start debugging", e1);
		} finally {
			subMonitor.done();
		}
	}

	/**
	 * For extenders/consumers of {@link DSPLaunchDelegate} who want to provide
	 * customization of the IDebugTarget, this method allows extenders to hook in a
	 * custom debug target implementation. The debug target is normally a subclass
	 * of {@link DSPDebugTarget}, but does not have to be. The arguments to this
	 * method are normally just passed to {@link DSPDebugTarget} constructor.
	 */
	protected IDebugTarget createDebugTarget(SubMonitor subMonitor, Runnable cleanup, InputStream inputStream,
			OutputStream outputStream, ILaunch launch, Map<String, Object> dspParameters) throws CoreException {
		final var target = new DSPDebugTarget(launch, cleanup, inputStream, outputStream, dspParameters);
		target.initialize(subMonitor.split(80));
		return target;
	}

	/**
	 * Throws an exception with a new status containing the given message and
	 * optional exception.
	 *
	 * @param message error message
	 * @param e       underlying exception
	 * @throws CoreException
	 */
	private void abort(String message, Throwable e) throws CoreException {
		throw new CoreException(new Status(IStatus.ERROR, DSPPlugin.PLUGIN_ID, 0, message, e));
	}
}
