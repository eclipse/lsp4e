/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Alex Boyko (Pivotal Software Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.refactoring;

import java.net.URI;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.DocumentChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.ltk.internal.core.refactoring.Changes;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.UndoEdit;

@SuppressWarnings("restriction")
public class LSPTextChange extends TextChange {

	private final @NonNull URI fileUri;
	private final @NonNull TextEdit textEdit;

	private Either<IFile, IFileStore> file;
	private int fAcquireCount;
	private ITextFileBuffer fBuffer;

	private TextChange delegate;

	public LSPTextChange(@NonNull String name, @NonNull URI fileUri, @NonNull TextEdit textEdit) {
		super(name);
		this.fileUri = fileUri;
		this.textEdit = textEdit;
	}

	@Override
	protected IDocument acquireDocument(IProgressMonitor pm) throws CoreException {
		fAcquireCount++;
		if (fAcquireCount > 1) {
			return fBuffer.getDocument();
		}

		IFile iFile = LSPEclipseUtils.getFileHandle(this.fileUri.toString());
		if (iFile != null) {
			this.file = Either.forLeft(iFile);
		} else {
			this.file = Either.forRight(EFS.getStore(this.fileUri));
		}

		ITextFileBufferManager manager = FileBuffers.getTextFileBufferManager();
		if (this.file.isLeft()) {
			this.fBuffer = manager.getTextFileBuffer(this.file.getLeft().getFullPath(), LocationKind.IFILE);
		} else {
			this.fBuffer = manager.getFileStoreTextFileBuffer(this.file.getRight());
		}
		if (this.fBuffer != null) {
			fAcquireCount++; // allows to mark open editor dirty instead of saving
		} else {
			if (this.file.isLeft()) {
				manager.connect(this.file.getLeft().getFullPath(), LocationKind.IFILE, pm);
				this.fBuffer = manager.getTextFileBuffer(this.file.getLeft().getFullPath(), LocationKind.IFILE);
			} else {
				manager.connectFileStore(this.file.getRight(), pm);
				this.fBuffer = manager.getFileStoreTextFileBuffer(this.file.getRight());
			}
		}
		return fBuffer.getDocument();
	}

	@Override
	protected void commit(IDocument document, IProgressMonitor pm) throws CoreException {
		this.fBuffer.commit(pm, true);
	}

	@Override
	protected void releaseDocument(IDocument document, IProgressMonitor pm) throws CoreException {
		Assert.isTrue(fAcquireCount > 0);
		if (fAcquireCount == 1) {
			ITextFileBufferManager manager = FileBuffers.getTextFileBufferManager();
			this.fBuffer.commit(pm, true);
			if (this.file.isLeft()) {
				manager.disconnect(this.file.getLeft().getFullPath(), LocationKind.IFILE, pm);
			} else {
				manager.disconnectFileStore(this.file.getRight(), pm);
			}
		}
		fAcquireCount--;
	}

	@Override
	protected Change createUndoChange(UndoEdit edit) {
		throw new UnsupportedOperationException("Should not be called!"); //$NON-NLS-1$
	}

	@Override
	public void initializeValidationData(IProgressMonitor pm) {
		// nothing to do yet, comment requested by sonar
	}

	@Override
	public RefactoringStatus isValid(IProgressMonitor pm) throws CoreException {
		return RefactoringStatus.create(Status.OK_STATUS);
	}

	@Override
	public Object getModifiedElement() {
		IFile file = LSPEclipseUtils.getFileHandle(this.fileUri.toString());
		if (file != null) {
			return file;
		}
		if (this.fBuffer != null) {
			return this.fBuffer.getDocument();
		}
		return null;
	}

	@Override
	public Change perform(IProgressMonitor pm) throws CoreException {
		pm.beginTask("", 3); //$NON-NLS-1$
		IDocument document = null;

		try {
			document = acquireDocument(SubMonitor.convert(pm, 1));

			int offset = 0;
			int length = document.getLength();
			if (textEdit.getRange() != null) {
				offset = LSPEclipseUtils.toOffset(textEdit.getRange().getStart(), document);
				length = LSPEclipseUtils.toOffset(textEdit.getRange().getEnd(), document) - offset;
			}
			if (this.file.isRight()) {
				delegate = new DocumentChange("Change in document " + fileUri.getPath(), document); //$NON-NLS-1$
			} else {
				delegate = new TextFileChange("Change in file " + this.file.getLeft().getName(), this.file.getLeft()) { //$NON-NLS-1$
					@Override
					protected boolean needsSaving() {
						return fAcquireCount == 1;
					}
				};
			}
			delegate.initializeValidationData(new NullProgressMonitor());
			delegate.setEdit(new ReplaceEdit(offset, length, textEdit.getNewText()));

			return delegate.perform(pm);

		} catch (BadLocationException e) {
			throw Changes.asCoreException(e);
		} catch (MalformedTreeException e) {
			throw Changes.asCoreException(e);
		} finally {
			if (document != null) {
				releaseDocument(document, SubMonitor.convert(pm, 1));
			}
			pm.done();
		}
	}

}
