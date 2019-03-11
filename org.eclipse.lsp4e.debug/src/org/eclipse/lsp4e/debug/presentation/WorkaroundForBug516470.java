package org.eclipse.lsp4e.debug.presentation;

import java.io.IOException;
import java.io.InputStream;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IEditorRegistry;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.FileStoreEditorInput;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.ide.IUnassociatedEditorStrategy;
import org.eclipse.ui.internal.ide.IDEWorkbenchMessages;
import org.eclipse.ui.internal.ide.IDEWorkbenchPlugin;
import org.eclipse.ui.internal.ide.registry.SystemEditorOrTextEditorStrategy;
import org.eclipse.ui.internal.ide.registry.UnassociatedEditorStrategyRegistry;

/**
 * This class contains copy of code that is currently private in {@link IDE}.
 * Bug 516470 is to workaround this issue.
 */
public class WorkaroundForBug516470 {

	private WorkaroundForBug516470() {
		// private constructor to avoid instances, requested by sonar
	}

	/**
	 * Returns an editor id appropriate for opening the given file store.
	 * <p>
	 * The editor descriptor is determined using a multi-step process. This method
	 * will attempt to resolve the editor based on content-type bindings as well as
	 * traditional name/extension bindings.
	 * </p>
	 * <ol>
	 * <li>The workbench editor registry is consulted to determine if an editor
	 * extension has been registered for the file type. If so, an instance of the
	 * editor extension is opened on the file. See
	 * <code>IEditorRegistry.getDefaultEditor(String)</code>.</li>
	 * <li>The operating system is consulted to determine if an in-place component
	 * editor is available (e.g. OLE editor on Win32 platforms).</li>
	 * <li>The operating system is consulted to determine if an external editor is
	 * available.</li>
	 * <li>The workbench editor registry is consulted to determine if the default
	 * text editor is available.</li>
	 * </ol>
	 * </p>
	 *
	 * @param fileStore the file store
	 * @return the id of an editor, appropriate for opening the file
	 * @throws PartInitException if no editor can be found
	 * @todo The IDE class has this method as a private, copied here so that it can
	 *       be exposed. See https://bugs.eclipse.org/bugs/show_bug.cgi?id=516470
	 * @deprecated Deprecated on creation as this is waiting for Bug 516470 to be
	 *             resolved
	 */
	@Deprecated
	public static String getEditorId(IFileStore fileStore, boolean allowInteractive) throws PartInitException {
		String name = fileStore.fetchInfo().getName();
		if (name == null) {
			throw new IllegalArgumentException();
		}

		IContentType contentType = null;
		try {
			InputStream is = null;
			try {
				is = fileStore.openInputStream(EFS.NONE, null);
				contentType = Platform.getContentTypeManager().findContentTypeFor(is, name);
			} finally {
				if (is != null) {
					is.close();
				}
			}
		} catch (CoreException ex) {
			// continue without content type
		} catch (IOException ex) {
			// continue without content type
		}

		IEditorRegistry editorReg = PlatformUI.getWorkbench().getEditorRegistry();

		IEditorDescriptor defaultEditor = editorReg.getDefaultEditor(name, contentType);
		defaultEditor = IDE.overrideDefaultEditorAssociation(new FileStoreEditorInput(fileStore), contentType,
				defaultEditor);
		return getEditorDescriptor(name, editorReg, defaultEditor, allowInteractive).getId();
	}

	/**
	 * Get the editor descriptor for a given name using the editorDescriptor passed
	 * in as a default as a starting point.
	 *
	 * @param name              The name of the element to open.
	 * @param editorReg         The editor registry to do the lookups from.
	 * @param defaultDescriptor IEditorDescriptor or <code>null</code>
	 * @return IEditorDescriptor
	 * @throws PartInitException if no valid editor can be found
	 *
	 * @todo The IDE class has this method as a private, copied here so that it can
	 *       be exposed via getEditorId. See
	 *       https://bugs.eclipse.org/bugs/show_bug.cgi?id=516470
	 * @deprecated Deprecated on creation as this is waiting for Bug 516470 to be
	 *             resolved
	 */
	@Deprecated
	private static IEditorDescriptor getEditorDescriptor(String name, IEditorRegistry editorReg,
			IEditorDescriptor defaultDescriptor, boolean allowInteractive) throws PartInitException {

		if (defaultDescriptor != null) {
			return defaultDescriptor;
		}

		IUnassociatedEditorStrategy strategy = getUnassociatedEditorStrategy(allowInteractive);
		IEditorDescriptor editorDesc;
		try {
			editorDesc = strategy.getEditorDescriptor(name, editorReg);
		} catch (CoreException e) {
			throw new PartInitException(IDEWorkbenchMessages.IDE_noFileEditorFound, e);
		}

		// if no valid editor found, bail out
		if (editorDesc == null) {
			throw new PartInitException(IDEWorkbenchMessages.IDE_noFileEditorFound);
		}

		return editorDesc;
	}

	/**
	 * @param allowInteractive Whether interactive strategies are considered
	 * @return The strategy to use in order to open unknown file. Either as set by
	 *         preference, or a {@link SystemEditorOrTextEditorStrategy} if none is
	 *         explicitly configured. Never returns {@code null}.
	 *
	 * @todo The IDE class has this method as a private, copied here so that it can
	 *       be exposed via getEditorId. See
	 *       https://bugs.eclipse.org/bugs/show_bug.cgi?id=516470
	 * @deprecated Deprecated on creation as this is waiting for Bug 516470 to be
	 *             resolved
	 */
	@Deprecated
	private static IUnassociatedEditorStrategy getUnassociatedEditorStrategy(boolean allowInteractive) {
		String preferedStrategy = IDEWorkbenchPlugin.getDefault().getPreferenceStore()
				.getString(IDE.UNASSOCIATED_EDITOR_STRATEGY_PREFERENCE_KEY);
		IUnassociatedEditorStrategy res = null;
		UnassociatedEditorStrategyRegistry registry = IDEWorkbenchPlugin.getDefault()
				.getUnassociatedEditorStrategyRegistry();
		if (allowInteractive || !registry.isInteractive(preferedStrategy)) {
			res = registry.getStrategy(preferedStrategy);
		}
		if (res == null) {
			res = new SystemEditorOrTextEditorStrategy();
		}
		return res;
	}
}
