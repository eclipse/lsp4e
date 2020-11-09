/*******************************************************************************
 * Copyright (c) 2017 Kichwa Coders Ltd. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.debug.debugmodel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IMarkerDelta;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.debug.core.IBreakpointListener;
import org.eclipse.debug.core.IBreakpointManager;
import org.eclipse.debug.core.IBreakpointManagerListener;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.ILineBreakpoint;
import org.eclipse.lsp4e.debug.DSPPlugin;
import org.eclipse.lsp4j.debug.BreakpointEventArguments;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsResponse;
import org.eclipse.lsp4j.debug.Source;
import org.eclipse.lsp4j.debug.SourceBreakpoint;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;

/**
 * TODO The DSP breakpoint manager is a minimal effort so far. It does not
 * really work as there isn't a two way mapping of debug adapter setting. Big
 * TODOs:
 * <ul>
 * <li>Function breakpoints
 * <li>Event points
 * <li>Update platform breakpoints based on adapter events and responses
 * <li>Support for line breakpoints not on IResource. For example CDT has
 * additional fields in the marker to specify full path
 */
public class DSPBreakpointManager implements IBreakpointManagerListener, IBreakpointListener {
	private Map<Source, List<SourceBreakpoint>> targetBreakpoints = new HashMap<>();
	private IDebugProtocolServer debugProtocolServer;
	private IBreakpointManager platformBreakpointManager;
	private Capabilities capabilities;

	public DSPBreakpointManager(IBreakpointManager platformBreakpointManager, IDebugProtocolServer debugProtocolServer,
			Capabilities capabilities) {
		this.debugProtocolServer = debugProtocolServer;
		this.platformBreakpointManager = platformBreakpointManager;
		this.capabilities = capabilities;
	}

	/**
	 * Initialize the manager and send all platform breakpoints to the debug
	 * adapter.
	 *
	 * @return the completeable future to signify when the breakpoints are all sent.
	 */
	public CompletableFuture<Void> initialize() {
		platformBreakpointManager.addBreakpointListener(this);
		platformBreakpointManager.addBreakpointManagerListener(this);
		return resendAllTargetBreakpoints(platformBreakpointManager.isEnabled());
	}

	/**
	 * Called when the debug manager is no longer needed/debug session is shut down.
	 */
	public void shutdown() {
		platformBreakpointManager.removeBreakpointListener(this);
		platformBreakpointManager.removeBreakpointManagerListener(this);
	}

	/**
	 * Returns whether this target can install the given breakpoint.
	 *
	 * @param breakpoint breakpoint to consider
	 * @return whether this target can install the given breakpoint
	 */
	public boolean supportsBreakpoint(IBreakpoint breakpoint) {
		return breakpoint instanceof ILineBreakpoint;
	}

	/**
	 * When the breakpoint manager disables, remove all registered breakpoints
	 * requests from the VM. When it enables, reinstall them.
	 */
	@Override
	public void breakpointManagerEnablementChanged(boolean enabled) {
		resendAllTargetBreakpoints(enabled);
	}

	private CompletableFuture<Void> resendAllTargetBreakpoints(boolean enabled) {
		IBreakpoint[] breakpoints = platformBreakpointManager.getBreakpoints();
		for (IBreakpoint breakpoint : breakpoints) {
			if (supportsBreakpoint(breakpoint)) {
				if (enabled) {
					addBreakpointToMap(breakpoint);
				} else {
					deleteBreakpointFromMap(breakpoint);
				}
			}
		}
		return sendBreakpoints();
	}

	@Override
	public void breakpointAdded(IBreakpoint breakpoint) {
		if (supportsBreakpoint(breakpoint)) {
			try {
				if ((breakpoint.isEnabled() && platformBreakpointManager.isEnabled()) || !breakpoint.isRegistered()) {
					addBreakpointToMap(breakpoint);
					sendBreakpoints();
				}
			} catch (CoreException e) {
				DSPPlugin.logError(e);
			}
		}
	}

	@Override
	public void breakpointRemoved(IBreakpoint breakpoint, IMarkerDelta delta) {
		if (supportsBreakpoint(breakpoint)) {
			deleteBreakpointFromMap(breakpoint);
			sendBreakpoints();
		}
	}

	@Override
	public void breakpointChanged(IBreakpoint breakpoint, IMarkerDelta delta) {
		if (supportsBreakpoint(breakpoint)) {
			try {
				if (breakpoint.isEnabled() && platformBreakpointManager.isEnabled()) {
					breakpointAdded(breakpoint);
				} else {
					breakpointRemoved(breakpoint, null);
				}
			} catch (CoreException e) {
			}
		}
	}

	private void addBreakpointToMap(IBreakpoint breakpoint) {
		Assert.isTrue(supportsBreakpoint(breakpoint) && breakpoint instanceof ILineBreakpoint);
		if (breakpoint instanceof ILineBreakpoint) {
			ILineBreakpoint lineBreakpoint = (ILineBreakpoint) breakpoint;
			IMarker marker = lineBreakpoint.getMarker();
			IResource resource = marker.getResource();
			IPath location = resource.getLocation();
			String path = location.toOSString();
			String name = location.lastSegment();
			int lineNumber;
			try {
				lineNumber = lineBreakpoint.getLineNumber();
			} catch (CoreException e) {
				lineNumber = -1;
			}

			Source source = new Source();
			source.setName(name);
			source.setPath(path);

			List<SourceBreakpoint> sourceBreakpoints = targetBreakpoints.computeIfAbsent(source,
					s -> new ArrayList<>());
			SourceBreakpoint sourceBreakpoint = new SourceBreakpoint();
			sourceBreakpoint.setLine(lineNumber);
			sourceBreakpoints.add(sourceBreakpoint);
		}
	}

	private void deleteBreakpointFromMap(IBreakpoint breakpoint) {
		Assert.isTrue(supportsBreakpoint(breakpoint) && breakpoint instanceof ILineBreakpoint);
		if (breakpoint instanceof ILineBreakpoint) {
			ILineBreakpoint lineBreakpoint = (ILineBreakpoint) breakpoint;
			IResource resource = lineBreakpoint.getMarker().getResource();
			IPath location = resource.getLocation();
			String path = location.toOSString();
			String name = location.lastSegment();
			int lineNumber;
			try {
				lineNumber = lineBreakpoint.getLineNumber();
			} catch (CoreException e) {
				lineNumber = -1;
			}
			for (Entry<Source, List<SourceBreakpoint>> entry : targetBreakpoints.entrySet()) {
				Source source = entry.getKey();
				if (Objects.equals(name, source.getName()) && Objects.equals(path, source.getPath())) {
					List<SourceBreakpoint> bps = entry.getValue();
					for (Iterator<SourceBreakpoint> iterator = bps.iterator(); iterator.hasNext();) {
						SourceBreakpoint sourceBreakpoint = iterator.next();
						if (Objects.equals(lineNumber, sourceBreakpoint.getLine())) {
							iterator.remove();
						}
					}
				}
			}
		}
	}

	private CompletableFuture<Void> sendBreakpoints() {
		List<CompletableFuture<Void>> all = new ArrayList<>();
		for (Iterator<Entry<Source, List<SourceBreakpoint>>> iterator = targetBreakpoints.entrySet()
				.iterator(); iterator.hasNext();) {
			Entry<Source, List<SourceBreakpoint>> entry = iterator.next();

			Source source = entry.getKey();
			List<SourceBreakpoint> bps = entry.getValue();
			int[] lines = bps.stream().mapToInt(SourceBreakpoint::getLine).toArray();
			SourceBreakpoint[] sourceBps = bps.toArray(new SourceBreakpoint[bps.size()]);

			SetBreakpointsArguments arguments = new SetBreakpointsArguments();
			arguments.setSource(source);
			arguments.setLines(lines);
			arguments.setBreakpoints(sourceBps);
			arguments.setSourceModified(false);
			CompletableFuture<SetBreakpointsResponse> future = debugProtocolServer.setBreakpoints(arguments);
			CompletableFuture<Void> future2 = future.thenAccept((SetBreakpointsResponse bpResponse) -> {
				// TODO update platform breakpoint with new info
			});
			all.add(future2);

			// Once we told adapter there are no breakpoints for a source file, we can stop
			// tracking that file
			if (bps.isEmpty()) {
				iterator.remove();
			}
		}
		return CompletableFuture.allOf(all.toArray(new CompletableFuture[all.size()]));
	}

	public void breakpointEvent(BreakpointEventArguments args) {
		// TODO Implement updates to breakpoints that come from the server (e.g.
		// breakpoints inserted/modified/removed from the CLI)
	}

}
