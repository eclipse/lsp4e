package org.eclipse.lsp4e;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.plugin.AbstractUIPlugin;

/**
 * Configuration handler that enables to send and update
 * the configuration of the language server.
 * It also contains a default implementation that can be instantiated.
 */
public interface IConfigurationHandler {

	/**
	 * Returns a {@link Map} containing all preference names and their values.
	 * Usually used during the initialization of the language server to send
	 * all the configuration at once.
	 * @return
	 */
	@SuppressWarnings("null")
	default @NonNull Map<String, Object> getConfiguration() {
		return Collections.emptyMap();
	}

	/**
	 * Returns a {@link Map} containing all preference names and their values.
	 * Used to send multiple configuration change to the language server at once.
	 * @return
	 */
	@SuppressWarnings("null")
	default @NonNull Map<String, Object> getConfiguration(final List<String> prefList) {
		return Collections.emptyMap();
	}

	/**
	 * Returns a {@link Map} containing the requested preference name and its value,
	 * usually used by a preference change listener.
	 * The Map will be converted to JSON by LSP4J before the transmission.
	 * @param preferenceName name of preference
	 * @return {@link Map} containing the requested preference name and its value
	 */
	@SuppressWarnings("null")
	default @NonNull Map<String, Object> getConfiguration(final String prefName) {
		return Collections.emptyMap();
	}

	/**
	 * Returns the preference store containing the configuration of the language server.
	 * This must be overridden by the plugin developer, usually with the actual
	 * {@code Activator.getDefault().getPreferenceStore()}
	 * @see AbstractUIPlugin#getPreferenceStore()
	 * @return the preference store
	 */
	public @NonNull IPreferenceStore getPreferenceStore();

	/**
	 * Default implementation that can be instantiated
	 */
	final static class DefaultConfigurationHandler implements IConfigurationHandler {
		public DefaultConfigurationHandler() {
			// Do nothing
		}

		@SuppressWarnings("null")
		@Override
		public @NonNull IPreferenceStore getPreferenceStore() {
			return LanguageServerPlugin.getDefault().getPreferenceStore();
		}
	}
}
