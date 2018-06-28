/*******************************************************************************
 * Copyright (c) 2017, 2018 Kichwa Coders Ltd. and others.
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
import java.lang.reflect.Type;
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
import org.eclipse.debug.core.IStreamListener;
import org.eclipse.debug.core.model.ILaunchConfigurationDelegate;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IStreamMonitor;
import org.eclipse.lsp4e.debug.DSPPlugin;
import org.eclipse.lsp4e.debug.debugmodel.DSPDebugTarget;
import org.eclipse.osgi.util.NLS;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class DSPLaunchDelegate implements ILaunchConfigurationDelegate {

	@Override
	public void launch(ILaunchConfiguration configuration, String mode, ILaunch launch, IProgressMonitor monitor)
			throws CoreException {
		SubMonitor subMonitor = SubMonitor.convert(monitor, 100);
		boolean launchNotConnect = DSPPlugin.DSP_MODE_LAUNCH
				.equals(configuration.getAttribute(DSPPlugin.ATTR_DSP_MODE, DSPPlugin.DSP_MODE_LAUNCH));

		String dspParametersJson = configuration.getAttribute(DSPPlugin.ATTR_DSP_PARAM, (String) null);
		Gson gson = new Gson();
		Type type = new TypeToken<Map<String, Object>>() {
		}.getType();
		Map<String, Object> dspParameters = gson.fromJson(dspParametersJson, type);
		if (dspParameters == null) {
			dspParameters = new HashMap<>();
		}

		// DSP supports run/debug as a simple flag to the debug server.
		// See LaunchRequestArguments.noDebug
		if (ILaunchManager.DEBUG_MODE.equals(mode)) {
			subMonitor.setTaskName("Starting debug session");
			dspParameters.put("noDebug", false);
		} else if (ILaunchManager.RUN_MODE.equals(mode)) {
			subMonitor.setTaskName("Starting run session");
			dspParameters.put("noDebug", true);
		} else {
			abort(NLS.bind("Unsupported launch mode '{0}'.", mode), null);
		}

		Runnable cleanup;
		InputStream inputStream;
		OutputStream outputStream;
		try {

			if (launchNotConnect) {
				List<String> command = new ArrayList<>();
				String debugCmd = configuration.getAttribute(DSPPlugin.ATTR_DSP_CMD, (String) null);

				if (debugCmd == null) {
					abort("Debug command unspecified.", null); //$NON-NLS-1$
				}
				command.add(debugCmd);
				List<String> debugArgs = configuration.getAttribute(DSPPlugin.ATTR_DSP_ARGS, (List<String>) null);
				if (debugArgs != null && !debugArgs.isEmpty()) {
					command.addAll(debugArgs);
				}

				ProcessBuilder processBuilder = new ProcessBuilder(command);
				subMonitor
						.subTask(NLS.bind("Launching debug adapter: {0}", "\"" + String.join("\" \"", command) + "\""));
				Process debugAdapterProcess = processBuilder.start();
				if (launch.getLaunchConfiguration().getAttribute(DSPPlugin.ATTR_DSP_MONITOR_DEBUG_ADAPTER, false)) {
					// Uglish workaround: should instead have a dedicated ProcessFactory reading process attribute rather than launch one
					String initialCaptureOutput = launch.getAttribute(DebugPlugin.ATTR_CAPTURE_OUTPUT);
					launch.setAttribute(DebugPlugin.ATTR_CAPTURE_OUTPUT, Boolean.toString(true));
					IProcess debugAdapterIProcess = DebugPlugin.newProcess(launch, debugAdapterProcess, "Debug Adapter");
					launch.setAttribute(DebugPlugin.ATTR_CAPTURE_OUTPUT, initialCaptureOutput);

					List<Byte> bytes = Collections.synchronizedList(new LinkedList<>());
					inputStream = new InputStream() {
						@Override public int read() throws IOException {
							while (debugAdapterProcess.isAlive()) {
								if (!bytes.isEmpty()) {
									return bytes.remove(0);
								} else {
									try {
										Thread.sleep(50);
									} catch (InterruptedException e) {
										DSPPlugin.logError(e);
									}
								}
							}
							return -1;
						}
					};
					debugAdapterIProcess.getStreamsProxy().getOutputStreamMonitor().addListener(new IStreamListener() {
						@Override public void streamAppended(String text, IStreamMonitor monitor) {
							try {
								for (byte b : text.getBytes(launch.getAttribute(DebugPlugin.ATTR_CONSOLE_ENCODING))) {
									bytes.add(Byte.valueOf(b));
								}
							} catch (IOException e) {
								DSPPlugin.logError(e);
							}
						}
					});
					outputStream = new OutputStream() {
						@Override public void write(int b) throws IOException {
							debugAdapterIProcess.getStreamsProxy().write(new String(new byte[] { (byte)b }));
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
					cleanup = () -> debugAdapterProcess.destroy();
				}
			} else {
				String server = configuration.getAttribute(DSPPlugin.ATTR_DSP_SERVER_HOST, (String) null);

				if (server == null) {
					abort("Debug server host unspecified.", null); //$NON-NLS-1$
				}
				int port = configuration.getAttribute(DSPPlugin.ATTR_DSP_SERVER_PORT, 0);

				if (port < 1 || port > 65535) {
					abort("Debug server port unspecified or out of range 1-65535.", null); //$NON-NLS-1$
				}
				subMonitor.subTask(NLS.bind("Connecting to debug adapter: {0}:{1}", server, port));
				Socket socket = new Socket(server, port);
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

			DSPDebugTarget target = new DSPDebugTarget(launch, cleanup, inputStream, outputStream, dspParameters);
			target.initialize(subMonitor.split(80));
			launch.addDebugTarget(target);
		} catch (IOException | OperationCanceledException e1) {
			abort("Failed to start debugging", e1);
		} finally {
			subMonitor.done();
		}
	}

	/**
	 * Throws an exception with a new status containing the given message and
	 * optional exception.
	 *
	 * @param message
	 *            error message
	 * @param e
	 *            underlying exception
	 * @throws CoreException
	 */
	private void abort(String message, Throwable e) throws CoreException {
		throw new CoreException(new Status(IStatus.ERROR, DSPPlugin.PLUGIN_ID, 0, message, e));
	}

}
