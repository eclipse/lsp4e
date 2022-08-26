/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.debug.breakpoints;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.eclipse.core.runtime.Adapters;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IAdapterFactory;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IValue;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.lsp4e.debug.DSPPlugin;
import org.eclipse.lsp4e.debug.debugmodel.DSPDebugTarget;
import org.eclipse.lsp4e.debug.debugmodel.DSPStackFrame;

public class TextSelectionToIVariable implements IAdapterFactory {

	@Override
	public <T> T getAdapter(Object adaptableObject, Class<T> adapterType) {
		if (adaptableObject instanceof TextSelection textSelection && IVariable.class.isAssignableFrom(adapterType)) {
			return (T) getVariableFor(textSelection);
		}
		return null;
	}

	@Override
	public Class<?>[] getAdapterList() {
		return new Class<?>[] { IVariable.class };
	}

	private IVariable getVariableFor(TextSelection selection) {
		if (!hasDAPTarget()) {
			return null;
		}
		IDocument document = getDocument(selection);
		if (document == null) {
			return null;
		}
		DSPStackFrame frame = getFrame();
		if (frame == null || !match(document, frame)) {
			return null;
		}
		String variableName = null;
		try {
			variableName = document.get(selection.getOffset(), selection.getLength());
			if (variableName.isEmpty()) {
				variableName = findVariableName(document, selection.getOffset());
			}
		} catch (BadLocationException e) {
			DSPPlugin.logError(e);
		}
		if (variableName == null || variableName.isEmpty()
				|| !Character.isJavaIdentifierStart(variableName.charAt(0))) {
			return null;
		}

		try {
			for (IVariable scopeVariable : frame.getVariables()) {
				IValue scope = scopeVariable.getValue();
				if (scope != null) {
					IVariable[] vars = scope.getVariables();
					for (IVariable var : vars) {
						if (var.getName().equals(variableName)) {
							return var;
						}
					}
				}
			}
		} catch (DebugException de) {
			DSPPlugin.logError(de);
		}

		if (Boolean.TRUE.equals(frame.getDebugTarget().getCapabilities().getSupportsEvaluateForHovers())) {
			try {
				// ok to call get as it should be a different thread.
				return frame.evaluate(variableName).get();
			} catch (ExecutionException e) {
				// can happen in normal execution when trying to evaluate some token that's not
				// a variable
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				DSPPlugin.logError(e);
				// will fall back by looking by looking up in current frame
			}
		}
		return null;
	}

	private boolean hasDAPTarget() {
		return Stream.of(DebugPlugin.getDefault().getLaunchManager().getLaunches()) //
				.filter(Predicate.not(ILaunch::isTerminated)) //
				.filter(launch -> ILaunchManager.DEBUG_MODE.equals(launch.getLaunchMode())) //
				.flatMap(launch -> Stream.of(launch.getDebugTargets())) //
				.anyMatch(DSPDebugTarget.class::isInstance);
	}

	private boolean match(IDocument document, DSPStackFrame frame) {
		Object sourceElement = frame.getLaunch().getSourceLocator().getSourceElement(frame);
		if (sourceElement instanceof String) {
			return Objects.equals(DocumentUtils.toUri(document).getPath(), sourceElement);
		}
		return false;
	}

	private String findVariableName(IDocument document, int offset) {
		try {
			if (!Character.isJavaIdentifierPart(document.getChar(offset))) {
				return null;
			}
			int startOffset = offset;
			while (startOffset - 1 >= 0 && Character.isJavaIdentifierPart(document.getChar(startOffset - 1)))
				startOffset--;
			int endOffset = offset;
			while (endOffset + 1 < document.getLength()
					&& Character.isJavaIdentifierPart(document.getChar(endOffset + 1)))
				endOffset++;
			return document.get(startOffset, endOffset - startOffset + 1);
		} catch (BadLocationException ex) {
			DSPPlugin.logError(ex);
			return null;
		}
	}

	protected DSPStackFrame getFrame() {
		IAdaptable adaptable = DebugUITools.getDebugContext();
		if (adaptable != null) {
			return Adapters.adapt(adaptable, DSPStackFrame.class);
		}
		return null;
	}

	private IDocument getDocument(TextSelection sel) {
		try {
			Method documentMethod = TextSelection.class.getDeclaredMethod("getDocument"); //$NON-NLS-1$
			documentMethod.setAccessible(true);
			return (IDocument) documentMethod.invoke(sel);
		} catch (NoSuchMethodException | IllegalArgumentException | IllegalAccessException
				| InvocationTargetException e) {
			DSPPlugin.logError(e);
			return null;
		}
	}

}
