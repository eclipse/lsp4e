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
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
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

	private boolean allowCustomSettingsCheckbox;
	private Button customDebugAdapterCheckbox;
	private Group debugAdapterSettingsGroup;
	private Composite launchParametersGroup;
	private Button customLaunchParametersCheckbox;

	public DSPMainTab() {
		this(false);
	}

	public DSPMainTab(boolean allowCustomSettingsCheckbox) {
		this.allowCustomSettingsCheckbox = allowCustomSettingsCheckbox;
	}

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
		if (allowCustomSettingsCheckbox) {
			customDebugAdapterCheckbox = new Button(parent, SWT.CHECK);
			customDebugAdapterCheckbox.setText("Override defaults and launch with these debug adapters settings.");
			customDebugAdapterCheckbox.setLayoutData(GridDataFactory.fillDefaults().span(1, 1).create());
			customDebugAdapterCheckbox
					.addSelectionListener(widgetSelectedAdapter(e -> updateLaunchConfigurationDialog()));
		}

		debugAdapterSettingsGroup = new Group(parent, SWT.NONE);
		debugAdapterSettingsGroup.setText("Debug Adapter Settings");
		debugAdapterSettingsGroup.setLayout(new GridLayout(2, false));
		debugAdapterSettingsGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		Label debugText = new Label(debugAdapterSettingsGroup, SWT.NONE);
		debugText.setText("Launch specific debug adapters using these settings.");
		debugText.setLayoutData(GridDataFactory.fillDefaults().span(2, 1).create());

		launchDebugServer = new Button(debugAdapterSettingsGroup, SWT.RADIO);
		launchDebugServer.setText("&Launch a Debug Server using the following arguments:");
		launchDebugServer.addSelectionListener(widgetSelectedAdapter(e -> updateLaunchConfigurationDialog()));
		launchDebugServer.setLayoutData(GridDataFactory.fillDefaults().span(2, 1).create());
		launchDebugServer.setSelection(true);

		Label programLabel = new Label(debugAdapterSettingsGroup, SWT.NONE);
		programLabel.setText("&Command:");
		programLabel.setLayoutData(new GridData(GridData.BEGINNING));
		debugCommandText = new Text(debugAdapterSettingsGroup, SWT.SINGLE | SWT.BORDER);
		debugCommandText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		debugCommandText.addModifyListener(e -> updateLaunchConfigurationDialog());
		Label argsLabel = new Label(debugAdapterSettingsGroup, SWT.NONE);
		argsLabel.setText("&Arguments:");
		argsLabel.setLayoutData(new GridData(GridData.BEGINNING));

		debugArgsText = new Text(debugAdapterSettingsGroup, SWT.SINGLE | SWT.BORDER);
		debugArgsText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		debugArgsText.addModifyListener(e -> updateLaunchConfigurationDialog());

		Composite filler = new Composite(debugAdapterSettingsGroup, SWT.NONE);
		filler.setLayoutData(new GridData(0, 0));
		monitorAdapterLauncherProcessCheckbox = new Button(debugAdapterSettingsGroup, SWT.CHECK);
		GridData layoutData = new GridData(SWT.LEFT, SWT.DEFAULT, true, false);
		monitorAdapterLauncherProcessCheckbox.setLayoutData(layoutData);
		monitorAdapterLauncherProcessCheckbox
				.addSelectionListener(widgetSelectedAdapter(e -> updateLaunchConfigurationDialog()));
		monitorAdapterLauncherProcessCheckbox.setText("Monitor Debug Adapter launcher process");

		connectDebugServer = new Button(debugAdapterSettingsGroup, SWT.RADIO);
		connectDebugServer.setText("Connect to &running Debug Server using the following arguments:");
		connectDebugServer.addSelectionListener(widgetSelectedAdapter(e -> updateLaunchConfigurationDialog()));
		connectDebugServer.setLayoutData(GridDataFactory.fillDefaults().span(2, 1).create());

		Label serverHostLabel = new Label(debugAdapterSettingsGroup, SWT.NONE);
		serverHostLabel.setText("Server &Host:");
		serverHostLabel.setLayoutData(new GridData(GridData.BEGINNING));
		serverHost = new Text(debugAdapterSettingsGroup, SWT.SINGLE | SWT.BORDER);
		serverHost.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		serverHost.addModifyListener(e -> updateLaunchConfigurationDialog());

		Label serverPortLabel = new Label(debugAdapterSettingsGroup, SWT.NONE);
		serverPortLabel.setText("Server &Port:");
		serverPortLabel.setLayoutData(new GridData(GridData.BEGINNING));
		serverPort = new Text(debugAdapterSettingsGroup, SWT.SINGLE | SWT.BORDER);
		serverPort.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		serverPort.addModifyListener(e -> updateLaunchConfigurationDialog());

	}

	private void createDebugJSonComponent(Composite parent) {
		if (allowCustomSettingsCheckbox) {
			customLaunchParametersCheckbox = new Button(parent, SWT.CHECK);
			customLaunchParametersCheckbox.setText("Enable additional Json settings for this launch.");
			customLaunchParametersCheckbox.setLayoutData(GridDataFactory.fillDefaults().span(1, 1).create());
			customLaunchParametersCheckbox
					.addSelectionListener(widgetSelectedAdapter(e -> updateLaunchConfigurationDialog()));
		}

		launchParametersGroup = new Group(parent, SWT.NONE);
		launchParametersGroup.setLayout(new GridLayout());
		launchParametersGroup.setLayoutData(new GridData(GridData.FILL_BOTH));

		Label jsonLabel = new Label(launchParametersGroup, SWT.NONE);
		jsonLabel.setText("Launch &Parameters (Json):");
		jsonLabel.setLayoutData(new GridData(GridData.BEGINNING));

		jsonText = new Text(launchParametersGroup, SWT.MULTI | SWT.WRAP | SWT.BORDER | SWT.V_SCROLL);
		jsonText.setLayoutData(new GridData(GridData.FILL_BOTH));
		jsonText.addModifyListener(e -> updateLaunchConfigurationDialog());

	}

	private void setEnabled(Composite composite, boolean enabled) {
		for (Control control : composite.getChildren()) {
			if (control instanceof Composite) {
				setEnabled((Composite) control, enabled);
			} else {
				control.setEnabled(enabled);
			}
		}
		composite.setEnabled(enabled);
	}

	@Override
	protected void updateLaunchConfigurationDialog() {
		boolean enableDebugAdapterSettings = customDebugAdapterCheckbox == null
				|| customDebugAdapterCheckbox.getSelection();
		boolean launch = launchDebugServer.getSelection();

		setEnabled(debugAdapterSettingsGroup, enableDebugAdapterSettings);
		debugCommandText.setEnabled(launch && enableDebugAdapterSettings);
		debugArgsText.setEnabled(launch && enableDebugAdapterSettings);
		monitorAdapterLauncherProcessCheckbox.setEnabled(launch && enableDebugAdapterSettings);
		serverHost.setEnabled(!launch && enableDebugAdapterSettings);
		serverPort.setEnabled(!launch && enableDebugAdapterSettings);

		boolean enableLaunchParameterSettings = customLaunchParametersCheckbox == null
				|| customLaunchParametersCheckbox.getSelection();
		setEnabled(launchParametersGroup, enableLaunchParameterSettings);

		super.updateLaunchConfigurationDialog();
	}

	@Override
	public void setDefaults(ILaunchConfigurationWorkingCopy configuration) {
		configuration.setAttribute(DSPPlugin.ATTR_CUSTOM_DEBUG_ADAPTER, false);
		configuration.setAttribute(DSPPlugin.ATTR_CUSTOM_LAUNCH_PARAMS, false);

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
			if (allowCustomSettingsCheckbox) {
				customDebugAdapterCheckbox
						.setSelection(configuration.getAttribute(DSPPlugin.ATTR_CUSTOM_DEBUG_ADAPTER, false));
				customLaunchParametersCheckbox
						.setSelection(configuration.getAttribute(DSPPlugin.ATTR_CUSTOM_LAUNCH_PARAMS, false));
			}

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
		if (allowCustomSettingsCheckbox) {
			configuration.setAttribute(DSPPlugin.ATTR_CUSTOM_DEBUG_ADAPTER, customDebugAdapterCheckbox.getSelection());
			configuration.setAttribute(DSPPlugin.ATTR_CUSTOM_LAUNCH_PARAMS,
					customLaunchParametersCheckbox.getSelection());
		}

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

		boolean enableDebugAdapterSettings = customDebugAdapterCheckbox == null
				|| customDebugAdapterCheckbox.getSelection();
		if (enableDebugAdapterSettings) {
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
		}

		boolean enableLaunchParameterSettings = customLaunchParametersCheckbox == null
				|| customLaunchParametersCheckbox.getSelection();
		if (enableLaunchParameterSettings) {
			// We don't check anything here yet. It would be good to check JSON is valid.
		}

		return true;
	}

}
