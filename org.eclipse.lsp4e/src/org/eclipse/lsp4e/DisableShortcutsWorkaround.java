/*******************************************************************************
 * Copyright (c) 2017 Pivotal Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Martin Lippert (Pivotal Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.bindings.Binding;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.keys.IBindingService;
import org.osgi.framework.Bundle;
import org.osgi.framework.Version;

/**
 * this is a startup participant that disables the "open symbol in workspace" key binding for older platform versions
 * to avoid the conflict with the overall "open type" key binding.
 */
public class DisableShortcutsWorkaround implements IStartup {

	private static final String LSP4E_COMMAND_ID_PREFIX = "org.eclipse.lsp4e"; //$NON-NLS-1$

	@Override
	@SuppressWarnings("null")
	public void earlyStartup() {

		if (isPlatformKeybindingBug517068Fixed()) {
			return;
		}

		IBindingService service = PlatformUI.getWorkbench().getService(IBindingService.class);
		if (service != null) {
			List<Binding> newBindings = new ArrayList<>();
			Binding[] bindings = service.getBindings();

			for (Binding binding : bindings) {
				String commandId = null;

				if (binding != null && binding.getParameterizedCommand() != null && binding.getParameterizedCommand().getCommand() != null) {
					commandId = binding.getParameterizedCommand().getCommand().getId();

					if (commandId == null) {
						newBindings.add(binding);
					}
					else if (!commandId.startsWith(LSP4E_COMMAND_ID_PREFIX)) {
						newBindings.add(binding);
					}
					else {
						Collection<?> conflicts = service.getConflictsFor(binding.getTriggerSequence());
						if (conflicts == null || conflicts.isEmpty()) {
							newBindings.add(binding);
						}
					}
				}
				else {
					newBindings.add(binding);
				}
			}

			PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
				try {
					service.savePreferences(service.getActiveScheme(),
							newBindings.toArray(new Binding[newBindings.size()]));
				} catch (IOException e) {
					LanguageServerPlugin.logError(e);
				}
			});
		}
	}

	/**
	 * check for bugfix 517068
	 */
	private boolean isPlatformKeybindingBug517068Fixed() {
		Bundle bundle = Platform.getBundle("org.eclipse.e4.ui.bindings"); //$NON-NLS-1$
		Version currentVersion = bundle.getVersion();
		Version fixVersion = new Version(0, 12, 1);
		return currentVersion.compareTo(fixVersion) >= 0;
	}

}
