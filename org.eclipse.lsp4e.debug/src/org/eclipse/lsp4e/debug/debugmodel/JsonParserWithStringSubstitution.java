/*******************************************************************************
 * Copyright (c) 2023 HIS Hochschul-Informations-System eG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.debug.debugmodel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

public class JsonParserWithStringSubstitution {

	private final IStringVariableManager stringVariableManager;

	/**
	 * @param stringVariableManager that should be used to substitute variables in
	 *                              strings.
	 */
	public JsonParserWithStringSubstitution(IStringVariableManager stringVariableManager) {
		this.stringVariableManager = stringVariableManager;
	}

	/**
	 * Substitutes variables in all string values within the given json.
	 *
	 * @param json as {@link String}
	 * @return Returns json object as a {@link Map}. Keys are of type
	 *         {@link String}, values are of type {@link Object}.
	 * @throws IllegalStateException is thrown if top level element is not a
	 *                               {@link JsonObject}.
	 * @throws CoreException         is thrown if undefined variable was referenced
	 *                               in json.
	 */
	public Map<String, @Nullable Object> parseJsonObject(final String json)
			throws IllegalStateException, CoreException {
		JsonElement jsonElement = JsonParser.parseString(json);
		JsonObject jsonObject = jsonElement.getAsJsonObject();
		return processJsonObject(jsonObject);
	}

	@SuppressWarnings("unchecked")
	public Map<String, Object> parseJsonObjectAndRemoveNulls(final String json)
			throws IllegalStateException, CoreException {
		Map<String, @Nullable Object> map = parseJsonObject(json);
		map.values().removeIf(Objects::isNull);
		return (Map<String, Object>) (Map<String, ?>) map;
	}

	private Map<String, @Nullable Object> processJsonObject(JsonObject jsonObject) throws CoreException {
		final var resultMap = new LinkedHashMap<String, @Nullable Object>();
		for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
			String key = entry.getKey();
			JsonElement value = entry.getValue();
			resultMap.put(key, handleJsonElement(value));
		}
		return resultMap;
	}

	private @Nullable Object handleJsonElement(JsonElement value) throws CoreException {
		if (value.isJsonObject()) {
			return processJsonObject(value.getAsJsonObject());
		}

		if (value.isJsonArray()) {
			return processJsonArray(value.getAsJsonArray());
		}

		if (value.isJsonPrimitive()) {
			return handleJsonPrimitive(value.getAsJsonPrimitive());
		}

		// Must be null.
		return null;
	}

	private Object handleJsonPrimitive(JsonPrimitive primitive) throws CoreException {
		if (primitive.isString()) {
			return stringVariableManager.performStringSubstitution(primitive.getAsString());
		}
		return primitive;
	}

	private Object processJsonArray(JsonArray array) throws CoreException {
		final var resultArray = new ArrayList<@Nullable Object>();
		for (JsonElement element : array) {
			resultArray.add(handleJsonElement(element));
		}
		return resultArray;
	}

}
