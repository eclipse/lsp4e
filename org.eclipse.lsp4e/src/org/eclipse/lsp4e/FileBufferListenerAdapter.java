/*******************************************************************************
 * Copyright (c) 2016 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e;

import org.eclipse.core.filebuffers.IFileBuffer;
import org.eclipse.core.filebuffers.IFileBufferListener;
import org.eclipse.core.runtime.IPath;

class FileBufferListenerAdapter implements IFileBufferListener {

	@Override
	public void bufferCreated(IFileBuffer buffer) {
	}

	@Override
	public void bufferDisposed(IFileBuffer buffer) {
	}

	@Override
	public void bufferContentAboutToBeReplaced(IFileBuffer buffer) {
	}

	@Override
	public void bufferContentReplaced(IFileBuffer buffer) {
	}

	@Override
	public void stateChanging(IFileBuffer buffer) {
	}

	@Override
	public void dirtyStateChanged(IFileBuffer buffer, boolean isDirty) {
	}

	@Override
	public void stateValidationChanged(IFileBuffer buffer, boolean isStateValidated) {
	}

	@Override
	public void underlyingFileMoved(IFileBuffer buffer, IPath path) {
	}

	@Override
	public void underlyingFileDeleted(IFileBuffer buffer) {
	}

	@Override
	public void stateChangeFailed(IFileBuffer buffer) {
	}

}
