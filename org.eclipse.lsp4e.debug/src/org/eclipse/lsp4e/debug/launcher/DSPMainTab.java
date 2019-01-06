/*******************************************************************************
 * Copyright (c) 2017, 2019 Kichwa Coders Ltd. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.debug.launcher;

import static org.eclipse.swt.events.SelectionListener.widgetSelectedAdapter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.ui.AbstractLaunchConfigurationTab;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.lsp4e.debug.DSPImages;
import org.eclipse.lsp4e.debug.DSPPlugin;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;

public class DSPMainTab extends AbstractLaunchConfigurationTab {

	private static final int DEFAULT_PORT = 4711;
	private static final String DEFAULT_SERVER = "127.0.0.1";
	private Text debugCommandText;
	// TODO the arguments in the UI should be some sort of a list to match what is
	// stored
	private Text debugArgsText;
	private Text jsonText;

	private Button launchDebugServer;
	private Button monitorAdapterLauncherProcessCheckbox;
	private Button connectDebugServer;
	private Text serverHost;
	private Text serverPort;

	@Override
	public void createControl(Composite parent) {
		Composite comp = new Composite(parent, SWT.NONE);
		setControl(comp);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(getControl(), getHelpContextId());
		comp.setLayout(new GridLayout(1, true));
		comp.setFont(parent.getFont());

		createVerticalSpacer(comp, 3);
		createDebugAdapterComponent(comp);
		createDebugJSonComponent(comp);

	}

	private void createDebugAdapterComponent(Composite parent) {
		Group group = new Group(parent, SWT.NONE);
		group.setText("Debug Adapter Settings");
		group.setLayout(new GridLayout(2, false));
		group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		Label debugText = new Label(group, SWT.NONE);
		debugText.setText(
				"We launch specific debug adapters using these settings. In future this could be handled by an extension point.");
		debugText.setLayoutData(GridDataFactory.fillDefaults().span(2, 1).create());

		launchDebugServer = new Button(group, SWT.RADIO);
		launchDebugServer.setText("&Launch a Debug Server using the following arguments:");
		launchDebugServer.addSelectionListener(widgetSelectedAdapter(e -> updateLaunchConfigurationDialog()));
		launchDebugServer.setLayoutData(GridDataFactory.fillDefaults().span(2, 1).create());
		launchDebugServer.setSelection(true);

		Label programLabel = new Label(group, SWT.NONE);
		programLabel.setText("&Command:");
		programLabel.setLayoutData(new GridData(GridData.BEGINNING));
		debugCommandText = new Text(group, SWT.SINGLE | SWT.BORDER);
		debugCommandText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		debugCommandText.addModifyListener(e -> updateLaunchConfigurationDialog());
		Label argsLabel = new Label(group, SWT.NONE);
		argsLabel.setText("&Arguments:");
		argsLabel.setLayoutData(new GridData(GridData.BEGINNING));

		debugArgsText = new Text(group, SWT.SINGLE | SWT.BORDER);
		debugArgsText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		debugArgsText.addModifyListener(e -> updateLaunchConfigurationDialog());

		Composite filler = new Composite(group, SWT.NONE);
		filler.setLayoutData(new GridData(0, 0));
		monitorAdapterLauncherProcessCheckbox = new Button(group, SWT.CHECK);
		GridData layoutData = new GridData(SWT.LEFT, SWT.DEFAULT, true, false);
		monitorAdapterLauncherProcessCheckbox.setLayoutData(layoutData);
		monitorAdapterLauncherProcessCheckbox
				.addSelectionListener(widgetSelectedAdapter(e -> updateLaunchConfigurationDialog()));
		monitorAdapterLauncherProcessCheckbox.setText("Monitor Debug Adapter launcher process");

		connectDebugServer = new Button(group, SWT.RADIO);
		connectDebugServer.setText("Connect to &running Debug Server using the following arguments:");
		connectDebugServer.addSelectionListener(widgetSelectedAdapter(e -> updateLaunchConfigurationDialog()));
		connectDebugServer.setLayoutData(GridDataFactory.fillDefaults().span(2, 1).create());

		Label serverHostLabel = new Label(group, SWT.NONE);
		serverHostLabel.setText("Server &Host:");
		serverHostLabel.setLayoutData(new GridData(GridData.BEGINNING));
		serverHost = new Text(group, SWT.SINGLE | SWT.BORDER);
		serverHost.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		serverHost.addModifyListener(e -> updateLaunchConfigurationDialog());

		Label serverPortLabel = new Label(group, SWT.NONE);
		serverPortLabel.setText("Server &Port:");
		serverPortLabel.setLayoutData(new GridData(GridData.BEGINNING));
		serverPort = new Text(group, SWT.SINGLE | SWT.BORDER);
		serverPort.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		serverPort.addModifyListener(e -> updateLaunchConfigurationDialog());

	}

	private void createDebugJSonComponent(Composite parent) {
		Composite comp = new Group(parent, SWT.NONE);
		comp.setLayout(new GridLayout());
		comp.setLayoutData(new GridData(GridData.FILL_BOTH));

		Label jsonLabel = new Label(comp, SWT.NONE);
		jsonLabel.setText("Launch &Parameters (Json):");
		jsonLabel.setLayoutData(new GridData(GridData.BEGINNING));

		jsonText = new Text(comp, SWT.MULTI | SWT.WRAP | SWT.BORDER | SWT.V_SCROLL);
		jsonText.setLayoutData(new GridData(GridData.FILL_BOTH));
		jsonText.addModifyListener(new ModifyListener() {

			@Override
			public void modifyText(ModifyEvent e) {
				updateLaunchConfigurationDialog();
			}
		});

	}

	@Override
	protected void updateLaunchConfigurationDialog() {
		boolean launch = launchDebugServer.getSelection();
		debugCommandText.setEnabled(launch);
		debugArgsText.setEnabled(launch);
		monitorAdapterLauncherProcessCheckbox.setEnabled(launch);
		serverHost.setEnabled(!launch);
		serverPort.setEnabled(!launch);

		super.updateLaunchConfigurationDialog();
	}

	@Override
	public void setDefaults(ILaunchConfigurationWorkingCopy configuration) {
		configuration.setAttribute(DSPPlugin.ATTR_DSP_MODE, DSPPlugin.DSP_MODE_LAUNCH);
		configuration.setAttribute(DSPPlugin.ATTR_DSP_CMD, "");
		configuration.setAttribute(DSPPlugin.ATTR_DSP_ARGS, Collections.emptyList());
		configuration.setAttribute(DSPPlugin.ATTR_DSP_SERVER_HOST, DEFAULT_SERVER);
		configuration.setAttribute(DSPPlugin.ATTR_DSP_SERVER_PORT, DEFAULT_PORT);
		configuration.setAttribute(DSPPlugin.ATTR_DSP_PARAM, "");
		configuration.setAttribute(DSPPlugin.ATTR_DSP_MONITOR_DEBUG_ADAPTER, false);
	}

	@Override
	public void initializeFrom(ILaunchConfiguration configuration) {
		try {
			boolean launch = DSPPlugin.DSP_MODE_LAUNCH
					.equals(configuration.getAttribute(DSPPlugin.ATTR_DSP_MODE, DSPPlugin.DSP_MODE_LAUNCH));
			launchDebugServer.setSelection(launch);
			connectDebugServer.setSelection(!launch);
			debugCommandText.setText(configuration.getAttribute(DSPPlugin.ATTR_DSP_CMD, ""));
			List<String> args = configuration.getAttribute(DSPPlugin.ATTR_DSP_ARGS, Collections.emptyList());
			if (args.size() == 0) {
				debugArgsText.setText("");
			} else if (args.size() == 1) {
				debugArgsText.setText(args.get(0));
			} else {
				debugArgsText.setText(String.join(" ", args.toArray(new String[args.size()])));
			}
			monitorAdapterLauncherProcessCheckbox
					.setSelection(configuration.getAttribute(DSPPlugin.ATTR_DSP_MONITOR_DEBUG_ADAPTER, false));
			serverHost.setText(configuration.getAttribute(DSPPlugin.ATTR_DSP_SERVER_HOST, DEFAULT_SERVER));
			serverPort.setText(
					Integer.toString(configuration.getAttribute(DSPPlugin.ATTR_DSP_SERVER_PORT, DEFAULT_PORT)));
			jsonText.setText(configuration.getAttribute(DSPPlugin.ATTR_DSP_PARAM, ""));
		} catch (CoreException e) {
			setErrorMessage(e.getMessage());
		}

	}

	@Override
	public void performApply(ILaunchConfigurationWorkingCopy configuration) {
		boolean launch = launchDebugServer.getSelection();
		if (launch) {
			configuration.setAttribute(DSPPlugin.ATTR_DSP_MODE, DSPPlugin.DSP_MODE_LAUNCH);
		} else {
			configuration.setAttribute(DSPPlugin.ATTR_DSP_MODE, DSPPlugin.DSP_MODE_CONNECT);
		}
		configuration.setAttribute(DSPPlugin.ATTR_DSP_CMD, getAttributeValueFrom(debugCommandText));
		String arg = getAttributeValueFrom(debugArgsText);
		if (arg == null) {
			configuration.setAttribute(DSPPlugin.ATTR_DSP_ARGS, (String) null);
		} else {
			configuration.setAttribute(DSPPlugin.ATTR_DSP_ARGS, Arrays.asList(arg.split("\\s+"))); //$NON-NLS-1$
		}
		configuration.setAttribute(DSPPlugin.ATTR_DSP_MONITOR_DEBUG_ADAPTER,
				monitorAdapterLauncherProcessCheckbox.getSelection());
		configuration.setAttribute(DSPPlugin.ATTR_DSP_SERVER_HOST, getAttributeValueFrom(serverHost));
		String portString = getAttributeValueFrom(serverPort);
		int port = DEFAULT_PORT;
		try {
			port = Integer.parseInt(portString);
		} catch (NumberFormatException e) {
			// handled in error checking already
		}
		configuration.setAttribute(DSPPlugin.ATTR_DSP_SERVER_PORT, port);
		configuration.setAttribute(DSPPlugin.ATTR_DSP_PARAM, getAttributeValueFrom(jsonText));

	}

	/**
	 * Returns the string in the text widget, or <code>null</code> if empty.
	 *
	 * @return text or <code>null</code>
	 */
	protected String getAttributeValueFrom(Text text) {
		String value = text.getText().trim();
		if (!value.isEmpty()) {
			return value;
		}
		return null;
	}

	@Override
	public String getName() {
		return "Debug Adapter";
	}

	@Override
	public String getId() {
		return "org.eclipse.lsp4e.debug.launcher.DSPMainTab";
	}

	@Override
	public Image getImage() {
		return DSPImages.get(DSPImages.IMG_VIEW_DEBUGGER_TAB);
	}

	@Override
	public boolean isValid(ILaunchConfiguration launchConfig) {
		setErrorMessage(null);
		setMessage(null);
		boolean launch = launchDebugServer.getSelection();
		if (launch) {
			if (getAttributeValueFrom(debugCommandText) == null) {
				setMessage("Specify a debug adapter command");
				return false;
			}
		} else {
			if (getAttributeValueFrom(serverHost) == null) {
				setMessage("Specify a server host");
				return false;
			}

			try {
				int port = Integer.parseInt(getAttributeValueFrom(serverPort));
				if (port < 1 || port > 65535) {
					throw new NumberFormatException();
				}
			} catch (NumberFormatException e) {
				setMessage("Specify a port as an integer in the range 1-65535");
				return false;
			}
		}

		return true;
	}

}
