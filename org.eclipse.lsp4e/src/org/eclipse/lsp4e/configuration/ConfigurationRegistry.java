/*******************************************************************************
 * Copyright (c) 2023 Dawid Pakuła and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Dawid Pakuła - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.ui.statushandlers.StatusManager;

public class ConfigurationRegistry {

	private static final String EXTENSION_POINT_ID = LanguageServerPlugin.PLUGIN_ID + ".languageServerConfiguration"; //$NON-NLS-1$
	private static final String ALIAS_ELEMENT = "alias"; //$NON-NLS-1$
	private static final String PROVIDER_ELEMENT = "provider"; //$NON-NLS-1$

	private static final String CLASS_ATTR = "class"; //$NON-NLS-1$

	private static final String SOURCE_ATTR = "source"; //$NON-NLS-1$

	private static final String TARGET_ATTR = "target"; //$NON-NLS-1$

	private static final String DOT = "."; //$NON-NLS-1$

	private static final String DOT_REGEX = "[.]"; //$NON-NLS-1$

	private static final class LazyHolder {
		static final ConfigurationRegistry INSTANCE = new ConfigurationRegistry();
	}

	public static ConfigurationRegistry getInstance() {
		return LazyHolder.INSTANCE;
	}

	private Map<String, Object> tree;

	private Throttler throtler;

	private Map<String, String> aliases;

	private ConfigurationRegistry() {
		initialize();
	}

	private class Throttler {
		List<String> paths = new ArrayList<>();
		Timer timer = new Timer();

		public void queue(String path) {
			synchronized(paths) {
				paths.add(path);
			}
			timer.schedule(new TimerTask() {

				@Override
				public void run() {
					String[] list;
					synchronized(paths) {
						list = paths.toArray(new String[0]);
						paths.clear();
					}
					if (list.length != 0) {
						settingsChanged(list);
					}

				}
			}, 100);
		}

	}

	class AliasProvider implements IConfigurationProvider {
		private String source;
		private String target;
		private IConfigurationProvider parentProvider;

		public AliasProvider(IConfigurationProvider parentProvider, String source, String target) {
			this.source = source;
			this.target = target;
			this.parentProvider = parentProvider;
		}

		@Override
		public Object valueOf(String path) {
			return parentProvider.valueOf(source);
		}

		@Override
		public String[] support() {
			return new String[] { target };
		}

	}

	private void initialize() {
		tree = new HashMap<>();
		throtler = new Throttler();
		IConfigurationElement[] extensions = Platform.getExtensionRegistry()
				.getConfigurationElementsFor(EXTENSION_POINT_ID);
		for (IConfigurationElement providerConfiguration : extensions) {
			if (!providerConfiguration.getName().equals(PROVIDER_ELEMENT)) {
				continue;
			}
			try {
				IConfigurationProvider provider = (IConfigurationProvider) providerConfiguration
						.createExecutableExtension(CLASS_ATTR);
				for (String path : provider.support()) {
					register(provider, path);
				}
			} catch (CoreException e) {
				StatusManager.getManager().handle(e, LanguageServerPlugin.PLUGIN_ID);
			}
		}
		aliases = new HashMap<>();
		for (IConfigurationElement alias : extensions) {
			if (!alias.getName().equals(ALIAS_ELEMENT)) {
				continue;
			}
			String source = alias.getAttribute(SOURCE_ATTR);
			String target = alias.getAttribute(TARGET_ATTR);
			Object resolve = resolveTree(source);
			if (resolve instanceof IConfigurationProvider) {
				register(new AliasProvider((IConfigurationProvider) resolve, source, target),
						alias.getAttribute(TARGET_ATTR));
				aliases.put(source, target);
			} else {
				LanguageServerPlugin.logError("Invalid alias: " + target, null); //$NON-NLS-1$
			}
		}

	}

	@SuppressWarnings("unchecked")
	private Object resolveTree(String path) {
		String[] parts = path.split(DOT_REGEX);

		Map<String, Object> local = tree;
		for (String part : parts) {
			if (!local.containsKey(part)) {
				return null;
			}
			Object sub = local.get(part);
			if (sub instanceof Map) {
				local = (Map<String, Object>) sub;
			} else if (sub instanceof IConfigurationProvider) {
				return sub;
			}
		}

		return local;

	}

	@SuppressWarnings("unchecked")
	private void register(IConfigurationProvider provider, String path) {
		Map<String, Object> local = tree;
		String[] parts = path.split(DOT_REGEX);
		for (int index = 0; index < parts.length; index++) {
			if (index == parts.length - 1) {
				local.put(parts[index], provider);
			} else {
				if (!local.containsKey(parts[index])) {
					local.put(parts[index], new HashMap<>());
				}
				Object sub = local.get(parts[index]);
				if (sub instanceof Map) {
					local = (Map<String, Object>) sub;
				} else {
					LanguageServerPlugin.logError("Invalid configuration path: " + path, null); //$NON-NLS-1$
					break;
				}
			}
		}
	}

	void settingsChanged(String[] paths) {
		LanguageServiceAccessor.getStartedWrappers(null, null, false).forEach(lsw -> {
			lsw.configurationChanged(paths);
		});

	}

	public Object resolve(String path) {
		return collect(resolveTree(path), path);
	}

	private Object collect(Object resolved, String prefix) {
		if (resolved instanceof IConfigurationProvider) {
			return ((IConfigurationProvider) resolved).valueOf(prefix);
		} else if (resolved instanceof Map) {
			Map<String, Object> result = new TreeMap<>();
			for (Entry<String, Object> entry : ((Map<String, Object>) resolved).entrySet()) {
				result.put(entry.getKey(),
						collect(entry.getValue(), prefix.isEmpty() ? entry.getKey() : prefix + DOT + entry.getKey()));
			}

			return result;
		}

		return null;
	}

	public void notifyChange(String path) {
		this.throtler.queue(path);
		if (aliases.containsKey(path)) {
			this.throtler.queue(aliases.get(path));
		}
	}
}
