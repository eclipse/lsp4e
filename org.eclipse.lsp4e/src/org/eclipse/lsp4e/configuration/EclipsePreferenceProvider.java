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

import java.util.HashMap;
import java.util.function.Function;

import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.PreferenceChangeEvent;
import org.eclipse.core.runtime.preferences.IPreferencesService;
import org.eclipse.core.runtime.preferences.IScopeContext;
import org.eclipse.core.runtime.preferences.InstanceScope;

public class EclipsePreferenceProvider implements IConfigurationProvider {

	private HashMap<String, Function<? super String, ? extends Object>> supported;

	private IScopeContext[] sources;
	private String rootPath;

	private IPreferencesService preferenceService;

	public EclipsePreferenceProvider(String rootPath) {
		this.supported = new HashMap<>();
		this.sources = new IScopeContext[] { InstanceScope.INSTANCE, DefaultScope.INSTANCE };
		this.rootPath = rootPath;
		this.preferenceService = Platform.getPreferencesService();

		InstanceScope.INSTANCE.getNode(rootPath).addPreferenceChangeListener(new IPreferenceChangeListener() {

			@Override
			public void preferenceChange(PreferenceChangeEvent event) {
				if (supported.containsKey(event.getKey())) {
					ConfigurationRegistry.getInstance().notifyChange(event.getKey());
				}
			}
		});
	}

	protected void add(String path) {
		supported.put(path, Function.identity());
	}

	protected void addInt(String path) {
		supported.put(path, Integer::valueOf);
	}

	protected void addBool(String path) {
		supported.put(path, Boolean::valueOf);
	}

	protected void addFloat(String path) {
		supported.put(path, Float::valueOf);
	}

	protected void addLong(String path) {
		supported.put(path, Long::valueOf);
	}

	protected void add(String path, Function<? super String, ? extends Object> transformer) {
		supported.put(path, transformer);
	}

	@Override
	public Object valueOf(String path) {
		String val = preferenceService.getString(rootPath, path, null, sources);
		if (val != null) {
			return supported.getOrDefault(path, Function.identity()).apply(val);
		}
		return null;
	}

	@Override
	public String[] support() {
		return supported.keySet().toArray(new String[0]);
	}

}
