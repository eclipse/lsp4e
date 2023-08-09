/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e;


import java.util.WeakHashMap;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.lsp4e.ui.LSPImages;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IPartService;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.contexts.IContextActivation;
import org.eclipse.ui.contexts.IContextService;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

public class LanguageServerPlugin extends AbstractUIPlugin {

	public static final String PLUGIN_ID = "org.eclipse.lsp4e"; //$NON-NLS-1$

	public static final String CONTEXT_ID = "org.eclipse.lsp4e.context"; //$NON-NLS-1$

	public static final boolean DEBUG = Boolean.parseBoolean(Platform.getDebugOption("org.eclipse.lsp4e/debug")); //$NON-NLS-1$

	// The shared instance
	private static LanguageServerPlugin plugin;

	public LanguageServerPlugin() {
	}

	public static class WindowListener implements IPartListener2 {
		private final IContextService service;

		private final WeakHashMap<IWorkbenchPart, IContextActivation> activations = new WeakHashMap<>();
		public WindowListener(IContextService service) {
			this.service = service;
			}

		@Override public void
		partActivated(IWorkbenchPartReference partRef) {
			IWorkbenchPart part = partRef.getPart(false);
			if (part instanceof IEditorPart) {
				IEditorPart editorPart = (IEditorPart)part;
				if (LanguageServersRegistry.getInstance().canUseLanguageServer(editorPart.getEditorInput())) {
					IContextActivation activation = this.service.activateContext(CONTEXT_ID);
					this.activations.put(editorPart, activation);
				}
			}

		}

		@Override public void
		partDeactivated(IWorkbenchPartReference partRef) {
			IWorkbenchPart part = partRef.getPart(false);

			if (part instanceof IEditorPart) {
				IEditorPart editorPart = (IEditorPart)part;
				IContextActivation activation = this.activations.remove(editorPart);
				if (activation != null) {
					this.service.deactivateContext(activation);
				}
			}
		}
	}

	@Override
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		IPartService partService = window.getPartService();
		IContextService contextService = PlatformUI.getWorkbench()
				.getService(IContextService.class);
		IPartListener2 listener = new WindowListener(contextService);
		partService.addPartListener(listener);


	}

	@Override
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		LanguageServiceAccessor.shutdownAllDispatchers();
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static LanguageServerPlugin getDefault() {
		return plugin;
	}

	@Override
	protected void initializeImageRegistry(ImageRegistry registry) {
		LSPImages.initalize(registry);
	}

	/**
	 * Utility method to log errors.
	 *
	 * @param thr
	 *            The exception through which we noticed the error
	 */
	public static void logError(final Throwable thr) {
		getDefault().getLog().log(new Status(IStatus.ERROR, PLUGIN_ID, 0, thr.getMessage(), thr));
	}

	/**
	 * Utility method to log errors.
	 *
	 * @param message
	 *            User comprehensible message
	 * @param thr
	 *            The exception through which we noticed the error
	 */
	public static void logError(final String message, final Throwable thr) {
		getDefault().getLog().log(new Status(IStatus.ERROR, PLUGIN_ID, 0, message, thr));
	}

	/**
	 * Log an info message for this plug-in
	 *
	 * @param message
	 */
	public static void logInfo(final String message) {
		getDefault().getLog().log(new Status(IStatus.INFO, PLUGIN_ID, 0, message, null));
	}

	/**
	 * Utility method to log warnings for this plug-in.
	 *
	 * @param message
	 *            User comprehensible message
	 * @param thr
	 *            The exception through which we noticed the warning
	 */
	public static void logWarning(final String message, final Throwable thr) {
		getDefault().getLog().log(new Status(IStatus.WARNING, PLUGIN_ID, 0, message, thr));
	}

}
