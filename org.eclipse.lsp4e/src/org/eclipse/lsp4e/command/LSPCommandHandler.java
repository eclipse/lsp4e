/*******************************************************************************
 * Copyright (c) 2019 Fraunhofer FOKUS and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Max Bureck (Fraunhofer FOKUS) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.command;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.lsp4j.Command;
import org.eclipse.ui.handlers.IHandlerService;

/**
 * This class provides a way to handle LSP {@link Command}s on client side, if
 * the server cannot handle a command. Sub-classes of this class can be
 * registered using the {@code org.eclipse.ui.handlers} extension point or
 * registered programmatically via {@link IHandlerService}. The handler has to
 * be registered under the {@code commandId} that equals the
 * {@link Command#getCommand()} string.</br>
 * </br>
 * This class is a shortcut for implementing an {@link IHandler} and in the
 * {@link IHandler#execute(ExecutionEvent)} method get the Command by calling:
 * {@code (Command)event.getObjectParameterForExecution(LSPCommandHandler.LSP_COMMAND_PARAMETER_ID)} and
 * {@code (IPath)event.getObjectParameterForExecution(LSPCommandHandler.LSP_PATH_PARAMETER_ID)}.
 */
public abstract class LSPCommandHandler extends AbstractHandler {

	/**
	 * ID of the {@link Command} parameter in a handled {@link ExecutionEvent}. Can
	 * be used to access a Command via
	 * {@link ExecutionEvent#getObjectParameterForExecution(String)}.
	 */
	public static final String LSP_COMMAND_PARAMETER_ID = "org.eclipse.lsp4e.command.param"; //$NON-NLS-1$

	/**
	 * ID of the {@link IPath} parameter in a handled {@linkplain ExecutionEvent}.
	 * Can be used to access the
	 * {@link ExecutionEvent#getObjectParameterForExecution(String)}.
	 */
	public static final String LSP_PATH_PARAMETER_ID = "org.eclipse.lsp4e.path.param"; //$NON-NLS-1$

	@Override
	public final Object execute(ExecutionEvent event) throws ExecutionException {
		Command command = (Command) event.getObjectParameterForExecution(LSP_COMMAND_PARAMETER_ID);
		if (command == null) {
			return null;
		}
		IPath path = (IPath) event.getObjectParameterForExecution(LSP_PATH_PARAMETER_ID);
		if (path == null) {
			return null;
		}
		return execute(event, command, path);
	}

	/**
	 * Can be overridden to execute {@code Command}s on the client side.
	 *
	 * @param event
	 *            An event containing all the information about the current state of
	 *            the application; must not be null.
	 * @param command
	 *            The Command to be executed on client side.
	 * @param path
	 *            The path to a resource that is the context for the command to be
	 *            executed. This can either be a resource in the workspace or a file
	 *            system path.
	 * @return The result of the execution. Reserved for future use, must be null.
	 * @throws ExecutionException
	 *             if an exception occurred during execution.
	 */
	public abstract Object execute(ExecutionEvent event, @NonNull Command command, IPath path)
			throws ExecutionException;
}
