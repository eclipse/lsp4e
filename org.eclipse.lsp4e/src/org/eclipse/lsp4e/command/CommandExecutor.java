/*******************************************************************************
 * Copyright (c) 2019, 2023 Fraunhofer FOKUS and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.command;

import static org.eclipse.lsp4e.command.LSPCommandHandler.LSP_COMMAND_PARAMETER_ID;
import static org.eclipse.lsp4e.command.LSPCommandHandler.LSP_PATH_PARAMETER_ID;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.commands.Category;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IParameter;
import org.eclipse.core.commands.NotEnabledException;
import org.eclipse.core.commands.NotHandledException;
import org.eclipse.core.commands.ParameterType;
import org.eclipse.core.commands.ParameterizedCommand;
import org.eclipse.core.commands.common.NotDefinedException;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.command.internal.CommandEventParameter;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.handlers.IHandlerService;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * This class provides methods to execute {@link Command} instances.
 * <p>
 * This class is deprecated and will likely be removed in the future,
 * when the LSP protocol provides standardized support for `client/executeCommand`
 * messages. See https://github.com/microsoft/language-server-protocol/pull/1119
 */
@Deprecated
public class CommandExecutor {

	private static final String LSP_COMMAND_CATEGORY_ID = "org.eclipse.lsp4e.commandCategory"; //$NON-NLS-1$
	private static final String LSP_COMMAND_PARAMETER_TYPE_ID = "org.eclipse.lsp4e.commandParameterType"; //$NON-NLS-1$
	private static final String LSP_PATH_PARAMETER_TYPE_ID = "org.eclipse.lsp4e.pathParameterType"; //$NON-NLS-1$

	/**
	 * @param languageServerId unused
	 * @deprecated use {@link #executeCommandClientSide(Command, IDocument)}
	 */
	@Deprecated(forRemoval = true)
	public static CompletableFuture<Object> executeCommand(@Nullable Command command, @Nullable IDocument document, @Nullable String languageServerId) {
		if (command != null && document != null) {
			return executeCommandClientSide(command, document);
		}
		return CompletableFuture.completedFuture(null);
	}

	public static CompletableFuture<Object> executeCommandClientSide(@NonNull Command command, @NonNull IDocument document) {
		IPath path = LSPEclipseUtils.toPath(document);
		if (path == null) {
			path = ResourcesPlugin.getWorkspace().getRoot().getLocation();
		}
		CompletableFuture<Object> r = executeCommandClientSide(command, path);
		if (r != null) {
			return r;
		}

		URI uri = LSPEclipseUtils.toUri(document);
		if (uri != null) {
			return CommandExecutor.executeFallbackClientSide(command, uri);
		}
		return CompletableFuture.completedFuture(null);
	}

	public static CompletableFuture<Object> executeCommandClientSide(@NonNull Command command, @NonNull IResource resource) {
		CompletableFuture<Object> r = executeCommandClientSide(command, resource.getFullPath());
		if (r != null) {
			return r;
		}
		URI uri = LSPEclipseUtils.toUri(resource);
		if (uri != null) {
			return executeFallbackClientSide(command, uri);
		}
		return CompletableFuture.completedFuture(null);
	}

	@SuppressWarnings("unused") // ECJ compiler handlerService cannot be null because getService is declared as
	// <T> T getService(Class<T> api), it infers the input is Class<@NonNull IHandlerService> and the output
	// @NonNull IHandlerService, as it takes over the @NonNull annotation when inferring the return type, which
	// is a bug in its implementation
	private static CompletableFuture<Object> executeCommandClientSide(@NonNull Command command, @Nullable IPath path) {
		IWorkbench workbench = PlatformUI.getWorkbench();
		if (workbench == null) {
			return null;
		}

		ParameterizedCommand parameterizedCommand = createEclipseCoreCommand(command, path, workbench);
		if (parameterizedCommand == null) {
			return null;
		}
		@Nullable
		IHandlerService handlerService = workbench.getService(IHandlerService.class);
		if (handlerService == null) {
			return null;
		}
		try {
			CompletableFuture<Object> r = CompletableFuture.completedFuture(handlerService.executeCommand(parameterizedCommand, null));
			if (r != null) {
				return r;
			}
		} catch (ExecutionException | NotDefinedException e) {
			LanguageServerPlugin.logError(e);
		} catch (NotEnabledException | NotHandledException e2) {
		}
		return null;
	}

	// tentative fallback
	private static CompletableFuture<Object> executeFallbackClientSide(@NonNull Command command, @NonNull URI initialUri) {
		if (command.getArguments() != null) {
			WorkspaceEdit edit = createWorkspaceEdit(command.getArguments(), initialUri);
			LSPEclipseUtils.applyWorkspaceEdit(edit, command.getTitle());
			return CompletableFuture.completedFuture(null);
		}
		return null;
	}

	@SuppressWarnings("unused") // ECJ compiler thinks commandService cannot be null (see above)
	private static ParameterizedCommand createEclipseCoreCommand(@NonNull Command command, IPath context,
			@NonNull IWorkbench workbench) {
		// Usually commands are defined via extension point, but we synthesize one on
		// the fly for the command ID, since we do not want downstream users
		// having to define them.
		String commandId = command.getCommand();
		@Nullable
		ICommandService commandService = workbench.getService(ICommandService.class);
		if (commandService == null) {
			return null;
		}
		org.eclipse.core.commands.Command coreCommand = commandService.getCommand(commandId);
		if (!coreCommand.isDefined()) {
			ParameterType commandParamType = commandService.getParameterType(LSP_COMMAND_PARAMETER_TYPE_ID);
			ParameterType pathParamType = commandService.getParameterType(LSP_PATH_PARAMETER_TYPE_ID);
			Category category = commandService.getCategory(LSP_COMMAND_CATEGORY_ID);
			IParameter[] parameters = {
					new CommandEventParameter(commandParamType, command.getTitle(), LSP_COMMAND_PARAMETER_ID),
					new CommandEventParameter(pathParamType, command.getTitle(), LSP_PATH_PARAMETER_ID)};
			coreCommand.define(commandId, null, category, parameters);
		}

		final var parameters = new HashMap<Object, Object>();
		parameters.put(LSP_COMMAND_PARAMETER_ID, command);
		parameters.put(LSP_PATH_PARAMETER_ID, context);
		ParameterizedCommand parameterizedCommand = ParameterizedCommand.generateCommand(coreCommand, parameters);
		return parameterizedCommand;
	}

	// TODO consider using Entry/SimpleEntry instead
	private static final class Pair<K, V> {
		K key;
		V value;

		Pair(K key, V value) {
			this.key = key;
			this.value = value;
		}
	}

	// this method may be turned public if needed elsewhere
	/**
	 * Very empirical and unsafe heuristic to turn unknown command arguments into a
	 * workspace edit...
	 */
	private static WorkspaceEdit createWorkspaceEdit(List<Object> commandArguments, @NonNull URI initialUri) {
		final var workspaceEdit = new WorkspaceEdit();
		final var changes = new HashMap<String, List<TextEdit>>();
		workspaceEdit.setChanges(changes);
		final var currentEntry = new Pair<URI, List<TextEdit>>(initialUri, new ArrayList<>());
		commandArguments.stream().flatMap(item -> {
			if (item instanceof List<?> list) {
				return list.stream();
			} else {
				return Collections.singleton(item).stream();
			}
		}).forEach(arg -> {
			if (arg instanceof String argString) {
				changes.put(currentEntry.key.toString(), currentEntry.value);
				IResource res = LSPEclipseUtils.findResourceFor(argString);
				if (res != null) {
					currentEntry.key = res.getLocationURI();
					currentEntry.value = new ArrayList<>();
				}
			} else if (arg instanceof WorkspaceEdit wsEdit) {
				changes.putAll(wsEdit.getChanges());
			} else if (arg instanceof TextEdit textEdit) {
				currentEntry.value.add(textEdit);
			} else if (arg instanceof Map) {
				final var gson = new Gson(); // TODO? retrieve the GSon used by LS
				TextEdit edit = gson.fromJson(gson.toJson(arg), TextEdit.class);
				if (edit != null) {
					currentEntry.value.add(edit);
				}
			} else if (arg instanceof JsonPrimitive json) {
				if (json.isString()) {
					changes.put(currentEntry.key.toString(), currentEntry.value);
					IResource res = LSPEclipseUtils.findResourceFor(json.getAsString());
					if (res != null) {
						currentEntry.key = res.getLocationURI();
						currentEntry.value = new ArrayList<>();
					}
				}
			} else if (arg instanceof JsonArray jsonArray) {
				final var gson = new Gson(); // TODO? retrieve the GSon used by LS
				jsonArray.forEach(elt -> {
					TextEdit edit = gson.fromJson(gson.toJson(elt), TextEdit.class);
					if (edit != null) {
						currentEntry.value.add(edit);
					}
				});
			} else if (arg instanceof JsonObject jsonObject) {
				final var gson = new Gson(); // TODO? retrieve the GSon used by LS
				WorkspaceEdit wEdit = gson.fromJson(jsonObject, WorkspaceEdit.class);
				Map<String, List<TextEdit>> entries = wEdit.getChanges();
				if (wEdit != null && !entries.isEmpty()) {
					changes.putAll(entries);
				} else {
					TextEdit edit = gson.fromJson(jsonObject, TextEdit.class);
					if (edit != null && edit.getRange() != null) {
						currentEntry.value.add(edit);
					}
				}
			}
		});
		if (!currentEntry.value.isEmpty()) {
			changes.put(currentEntry.key.toASCIIString(), currentEntry.value);
		}
		return workspaceEdit;
	}
}
