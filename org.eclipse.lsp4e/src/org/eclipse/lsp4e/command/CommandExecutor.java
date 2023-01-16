/*******************************************************************************
 * Copyright (c) 2019, 2022 Fraunhofer FOKUS and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.command;

import static org.eclipse.lsp4e.command.LSPCommandHandler.LSP_COMMAND_PARAMETER_ID;
import static org.eclipse.lsp4e.command.LSPCommandHandler.LSP_PATH_PARAMETER_ID;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import org.eclipse.core.commands.Category;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
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
import org.eclipse.lsp4e.LanguageServersRegistry;
import org.eclipse.lsp4e.LanguageServersRegistry.LanguageServerDefinition;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.command.internal.CommandEventParameter;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.services.LanguageServer;
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
	 * Will execute the given {@code command} either on a language server,
	 * supporting the command, or on the client, if an {@link IHandler} is
	 * registered for the ID of the command (see {@link LSPCommandHandler}). If
	 * {@code command} is {@code null}, then this method will do nothing (returning null).
	 * If neither the server, nor the client are able to handle the command explicitly, a
	 * heuristic method will try to interpret the command locally.
	 *
	 * @param command
	 *            the LSP Command to be executed. If {@code null} this method will
	 *            do nothing.
	 * @param document
	 *            optional document for which the command was created
	 * @param languageServerId
	 *            the ID of the language server for which the {@code command} is
	 *            applicable. If {@code null}, the command will not be executed on
	 *            the language server.
	 * @return A CompletableFuture<Object> or null. A null return value means that
	 *      'there is no known way to handle the command'. A non-null value means 'the command
	 *      is being handled'. Therefore it is possible for a caller to determine synchronously
	 *      whether the callee is handling the command or not (by checking whether the return value is not null).
	 */
	public static CompletableFuture<Object> executeCommand(@Nullable Command command, @Nullable IDocument document,
			@Nullable String languageServerId) {
		if (command == null) {
			return null;
		}
		CompletableFuture<Object> r = executeCommandServerSide(command, languageServerId, document);
		if (r!=null) {
			return r;
		}
		r = executeCommandClientSide(command, document);
		if (r!=null) {
			return r;
		}
		// tentative fallback
		if (command.getArguments() != null) {
			WorkspaceEdit edit = createWorkspaceEdit(command.getArguments(), document);
			LSPEclipseUtils.applyWorkspaceEdit(edit, command.getTitle());
			return CompletableFuture.completedFuture(null);
		}
		return null;
	}

	private static CompletableFuture<Object> executeCommandServerSide(@NonNull Command command, @Nullable String languageServerId,
			@Nullable IDocument document) {
		@Nullable LanguageServerDefinition languageServerDefinition = languageServerId == null ? null : LanguageServersRegistry.getInstance()
				.getDefinition(languageServerId);
		try {
			CompletableFuture<LanguageServer> languageServerFuture = getLanguageServerForCommand(command, document,
					languageServerDefinition);
			if (languageServerFuture == null) {
				return null;
			}
			// Server can handle command
			return languageServerFuture.thenApplyAsync(server -> {
				final var params = new ExecuteCommandParams();
				params.setCommand(command.getCommand());
				params.setArguments(command.getArguments());
				return server.getWorkspaceService().executeCommand(params);
			});
		} catch (IOException e) {
			// log and let the code fall through for LSPEclipseUtils to handle
			LanguageServerPlugin.logError(e);
			return null;
		}
	}

	private static CompletableFuture<LanguageServer> getLanguageServerForCommand(@NonNull Command command,
			@Nullable IDocument document, @Nullable LanguageServerDefinition languageServerDefinition) throws IOException {
		if (document!=null && languageServerDefinition!=null) {
			return LanguageServiceAccessor
					.getInitializedLanguageServer(document, languageServerDefinition, serverCapabilities -> {
						ExecuteCommandOptions provider = serverCapabilities.getExecuteCommandProvider();
						return provider != null && provider.getCommands().contains(command.getCommand());
					});
		} else {
			String id = command.getCommand();
			List<LanguageServer> commandHandlers = LanguageServiceAccessor.getActiveLanguageServers(handlesCommand(id));
			if (commandHandlers != null && !commandHandlers.isEmpty()) {
				if (commandHandlers.size() == 1) {
					return CompletableFuture.completedFuture(commandHandlers.get(0));
				} else if (commandHandlers.size() > 1) {
					throw new IllegalStateException("Multiple language servers have registered to handle command '"+id+"'"); //$NON-NLS-1$ //$NON-NLS-2$
				}
			}
		}
		return null;
	}

	private static Predicate<ServerCapabilities> handlesCommand(String id) {
		return serverCaps -> {
			ExecuteCommandOptions executeCommandProvider = serverCaps.getExecuteCommandProvider();
			if (executeCommandProvider != null) {
				return executeCommandProvider.getCommands().contains(id);
			}
			return false;
		};
	}

	@SuppressWarnings("unused") // ECJ compiler for some reason thinks handlerService == null is always false
	private static CompletableFuture<Object> executeCommandClientSide(@NonNull Command command, @Nullable IDocument document) {
		IWorkbench workbench = PlatformUI.getWorkbench();
		if (workbench == null) {
			return null;
		}
		IPath context = document==null
				? ResourcesPlugin.getWorkspace().getRoot().getLocation()
				: LSPEclipseUtils.toPath(document);
		ParameterizedCommand parameterizedCommand = createEclipseCoreCommand(command, context, workbench);
		@Nullable
		IHandlerService handlerService = workbench.getService(IHandlerService.class);
		if (handlerService == null) {
			return null;
		}
		if (parameterizedCommand == null) {
			return null;
		}
		try {
			return CompletableFuture.completedFuture(handlerService.executeCommand(parameterizedCommand, null));
		} catch (ExecutionException | NotDefinedException e) {
			LanguageServerPlugin.logError(e);
			return null;
		} catch (NotEnabledException | NotHandledException e2) {
			return null;
		}
	}

	private static ParameterizedCommand createEclipseCoreCommand(@NonNull Command command, IPath context,
			@NonNull IWorkbench workbench) {
		// Usually commands are defined via extension point, but we synthesize one on
		// the fly for the command ID, since we do not want downstream users
		// having to define them.
		String commandId = command.getCommand();
		@Nullable
		ICommandService commandService = workbench.getService(ICommandService.class);
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
	private static WorkspaceEdit createWorkspaceEdit(List<Object> commandArguments, IDocument document) {
		final var res = new WorkspaceEdit();
		final var changes = new HashMap<String, List<TextEdit>>();
		res.setChanges(changes);
		URI initialUri = LSPEclipseUtils.toUri(document);
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
				IResource resource = LSPEclipseUtils.findResourceFor(argString);
				if (resource != null) {
					currentEntry.key = resource.getLocationURI();
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
					IResource resource = LSPEclipseUtils.findResourceFor(json.getAsString());
					if (resource != null) {
						currentEntry.key = resource.getLocationURI();
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
			changes.put(currentEntry.key.toString(), currentEntry.value);
		}
		return res;
	}
}
