/*******************************************************************************
 * Copyright (c) 2019 Fraunhofer FOKUS and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.command.internal;

import static org.eclipse.lsp4e.command.LSPCommandHandler.LSP_COMMAND_PARAMETER_ID;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServersRegistry;
import org.eclipse.lsp4e.LanguageServersRegistry.LanguageServerDefinition;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.command.LSPCommandHandler;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.ExecuteCommandParams;
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
 */
public class CommandExecutor {

	private static final String LSP_COMMAND_CATEGORY_ID = "org.eclipse.lsp4e.commandCategory"; //$NON-NLS-1$
	private static final String LSP_COMMAND_PARAMETER_TYPE_ID = "org.eclipse.lsp4e.commandParameterType"; //$NON-NLS-1$

	/**
	 * Will execute the given {@code command} either on a language server,
	 * supporting the command, or on the client, if an {@link IHandler} is
	 * registered for the ID of the command (see {@link LSPCommandHandler}). If
	 * {@code command} is {@code null}, then this method will do nothing. If neither
	 * the server, nor the client are able to handle the command explicitly, a
	 * heuristic method will try to interpret the command locally.
	 *
	 * @param command
	 *            the LSP Command to be executed. If {@code null} this method will
	 *            do nothing.
	 * @param document
	 *            the document for which the command was created
	 * @param languageServerId
	 *            the ID of the language server for which the {@code command} is
	 *            applicable. If {@code null}, the command will not be executed on
	 *            the language server.
	 */
	public static void executeCommand(@Nullable Command command, @NonNull IDocument document,
			@Nullable String languageServerId) {
		if (command == null) {
			return;
		}
		if (executeCommandServerSide(command, languageServerId, document)) {
			return;
		}
		if (executeCommandClientSide(command, document)) {
			return;
		}
		// tentative fallback
		if (command.getArguments() != null) {
			WorkspaceEdit edit = createWorkspaceEdit(command.getArguments(), document);
			LSPEclipseUtils.applyWorkspaceEdit(edit);
		}
	}

	private static boolean executeCommandServerSide(@NonNull Command command, @Nullable String languageServerId,
			@NonNull IDocument document) {
		if (languageServerId == null) {
			return false;
		}
		LanguageServerDefinition languageServerDefinition = LanguageServersRegistry.getInstance()
				.getDefinition(languageServerId);
		if (languageServerDefinition == null) {
			return false;
		}

		try {
			CompletableFuture<LanguageServer> languageServerFuture = getLanguageServerForCommand(command, document,
					languageServerDefinition);
			if (languageServerFuture == null) {
				return false;
			}
			// Server can handle command
			languageServerFuture.thenAcceptAsync(server -> {
				ExecuteCommandParams params = new ExecuteCommandParams();
				params.setCommand(command.getCommand());
				params.setArguments(command.getArguments());
				server.getWorkspaceService().executeCommand(params);
			});
			return true;
		} catch (IOException e) {
			// log and let the code fall through for LSPEclipseUtils to handle
			LanguageServerPlugin.logError(e);
			return false;
		}

	}

	private static CompletableFuture<LanguageServer> getLanguageServerForCommand(@NonNull Command command,
			@NonNull IDocument document, @NonNull LanguageServerDefinition languageServerDefinition) throws IOException {
		CompletableFuture<LanguageServer> languageServerFuture = LanguageServiceAccessor
				.getInitializedLanguageServer(document, languageServerDefinition, serverCapabilities -> {
					ExecuteCommandOptions provider = serverCapabilities.getExecuteCommandProvider();
					return provider != null && provider.getCommands().contains(command.getCommand());
				});
		return languageServerFuture;
	}

	@SuppressWarnings("unused") // ECJ compiler for some reason thinks handlerService == null is always false
	private static boolean executeCommandClientSide(@NonNull Command command, @NonNull IDocument document) {
		IWorkbench workbench = PlatformUI.getWorkbench();
		if (workbench == null) {
			return false;
		}
		ParameterizedCommand parameterizedCommand = createEclipseCoreCommand(command, workbench);
		if (parameterizedCommand == null) {
			return false;
		}
		@Nullable
		IHandlerService handlerService = workbench.getService(IHandlerService.class);
		if (handlerService == null) {
			return false;
		}
		try {
			handlerService.executeCommand(parameterizedCommand, null);
		} catch (ExecutionException | NotDefinedException e) {
			LanguageServerPlugin.logError(e);
			return false;
		} catch (NotEnabledException | NotHandledException e2) {
			return false;
		}
		return true;
	}

	private static ParameterizedCommand createEclipseCoreCommand(@NonNull Command command,
			@NonNull IWorkbench workbench) {
		// Usually commands are defined via extension point, but we synthesize one on
		// the fly for the command ID, since we do not want downstream users
		// having to define them.
		String commandId = command.getCommand();
		@Nullable
		ICommandService commandService = workbench.getService(ICommandService.class);
		org.eclipse.core.commands.Command coreCommand = commandService.getCommand(commandId);
		if (!coreCommand.isDefined()) {
			ParameterType paramType = commandService.getParameterType(LSP_COMMAND_PARAMETER_TYPE_ID);
			Category category = commandService.getCategory(LSP_COMMAND_CATEGORY_ID);
			IParameter[] parameters = {
					new CommandEventParameter(paramType, command.getTitle(), LSP_COMMAND_PARAMETER_ID) };
			coreCommand.define(commandId, null, category, parameters);
		}

		Map<Object, Object> parameters = new HashMap<>();
		parameters.put(LSP_COMMAND_PARAMETER_ID, command);
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
		WorkspaceEdit res = new WorkspaceEdit();
		Map<String, List<TextEdit>> changes = new HashMap<>();
		res.setChanges(changes);
		URI initialUri = LSPEclipseUtils.toUri(document);
		Pair<URI, List<TextEdit>> currentEntry = new Pair<>(initialUri, new ArrayList<>());
		commandArguments.stream().flatMap(item -> {
			if (item instanceof List) {
				return ((List<?>) item).stream();
			} else {
				return Collections.singleton(item).stream();
			}
		}).forEach(arg -> {
			if (arg instanceof String) {
				changes.put(currentEntry.key.toString(), currentEntry.value);
				IResource resource = LSPEclipseUtils.findResourceFor((String) arg);
				if (resource != null) {
					currentEntry.key = resource.getLocationURI();
					currentEntry.value = new ArrayList<>();
				}
			} else if (arg instanceof WorkspaceEdit) {
				changes.putAll(((WorkspaceEdit) arg).getChanges());
			} else if (arg instanceof TextEdit) {
				currentEntry.value.add((TextEdit) arg);
			} else if (arg instanceof Map) {
				Gson gson = new Gson(); // TODO? retrieve the GSon used by LS
				TextEdit edit = gson.fromJson(gson.toJson(arg), TextEdit.class);
				if (edit != null) {
					currentEntry.value.add(edit);
				}
			} else if (arg instanceof JsonPrimitive) {
				JsonPrimitive json = (JsonPrimitive) arg;
				if (json.isString()) {
					changes.put(currentEntry.key.toString(), currentEntry.value);
					IResource resource = LSPEclipseUtils.findResourceFor(json.getAsString());
					if (resource != null) {
						currentEntry.key = resource.getLocationURI();
						currentEntry.value = new ArrayList<>();
					}
				}
			} else if (arg instanceof JsonArray) {
				Gson gson = new Gson(); // TODO? retrieve the GSon used by LS
				JsonArray array = (JsonArray) arg;
				array.forEach(elt -> {
					TextEdit edit = gson.fromJson(gson.toJson(elt), TextEdit.class);
					if (edit != null) {
						currentEntry.value.add(edit);
					}
				});
			} else if (arg instanceof JsonObject) {
				Gson gson = new Gson(); // TODO? retrieve the GSon used by LS
				WorkspaceEdit wEdit = gson.fromJson((JsonObject) arg, WorkspaceEdit.class);
				Map<String, List<TextEdit>> entries = wEdit.getChanges();
				if (wEdit != null && !entries.isEmpty()) {
					changes.putAll(entries);
				} else {
					TextEdit edit = gson.fromJson((JsonObject) arg, TextEdit.class);
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
