/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.codeactions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.IMarkerResolution;
import org.eclipse.ui.views.markers.WorkbenchMarkerResolution;

import com.google.gson.Gson;

public class CodeActionMarkerResolution extends WorkbenchMarkerResolution implements IMarkerResolution {

	private @NonNull Command command;

	public CodeActionMarkerResolution(@NonNull Command command) {
		this.command = command;
	}

	@Override
	public String getLabel() {
		return this.command.getTitle();
	}

	@Override
	public void run(IMarker marker) {
		try {
			// This is a *client-side* command, no need to go through workspace/executeCommand operation
			// TODO? Consider binding LS commands to Eclipse commands and handlers???
			if (command.getArguments() != null) {
				WorkspaceEdit edit = createWorkspaceEdit(command.getArguments(), marker.getResource());
				new WorkspaceJob(command.getTitle()) {
					@Override
					public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
						LSPEclipseUtils.applyWorkspaceEdit(edit);
						return Status.OK_STATUS;
					}
				}.schedule();
			}
		} catch (Exception e) {
			LanguageServerPlugin.logError(e);
		}
	}

	private static final class Pair<K, V> {
		K key;
		V value;
		Pair(K key,V value) {
			this.key = key;
			this.value = value;
		}
	}

	/**
	 * Very empirical and unsafe heuristic to turn unknown command arguments
	 * into a workspace edit...
	 */
	private WorkspaceEdit createWorkspaceEdit(List<Object> arguments, IResource initialResource) {
		WorkspaceEdit res = new WorkspaceEdit();
		Map<String, List<TextEdit>> changes = new HashMap<>();
		res.setChanges(changes);
		Pair<IResource, List<TextEdit>> currentEntry = new Pair<>(initialResource, new ArrayList<>());
		arguments.stream().flatMap(item -> {
			if (item instanceof List) {
				return ((List<?>)item).stream();
			} else {
				return Collections.singleton(item).stream();
			}
		}).forEach(arg -> {
			if (arg instanceof String) {
				changes.put(currentEntry.key.getLocationURI().toString(), currentEntry.value);
				IResource resource = LSPEclipseUtils.findResourceFor((String)arg);
				if (resource != null) {
					currentEntry.key = resource;
					currentEntry.value = new ArrayList<>();
				}
			} else if (arg instanceof WorkspaceEdit) {
				changes.putAll(((WorkspaceEdit)arg).getChanges());
			} else if (arg instanceof TextEdit) {
				currentEntry.value.add((TextEdit)arg);
			} else if (arg instanceof Map) {
				Gson gson = new Gson(); // TODO? retrieve the GSon used by LS
				TextEdit edit = gson.fromJson(gson.toJson(arg), TextEdit.class);
				if (edit != null) {
					currentEntry.value.add(edit);
				}
			}
		});
		changes.put(currentEntry.key.getLocationURI().toString(), currentEntry.value);
		return res;
	}

	@Override
	public String getDescription() {
		return command.getTitle();
	}

	@Override
	public Image getImage() {
		return null;
	}

	@Override
	public IMarker[] findOtherMarkers(IMarker[] markers) {
		// TODO Auto-generated method stub
		return new IMarker[0];
	}

}
