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

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.IFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4j.TextEdit;
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

	private IFile file;
	private TextEdit textEdit;

	private TextChange delegate;

	private int fAcquireCount;
	private ITextFileBuffer fBuffer;

	public LSPTextChange(String name, IFile file, TextEdit textEdit) {
		super(name);
		this.file = file;
		this.textEdit = textEdit;
	}

	@Override
	protected IDocument acquireDocument(IProgressMonitor pm) throws CoreException {
		if (!file.exists()) {
			throw new IllegalArgumentException("Expected existing file."); //$NON-NLS-1$
		}
		fAcquireCount++;
		if (fAcquireCount > 1)
			return fBuffer.getDocument();

		ITextFileBufferManager manager = FileBuffers.getTextFileBufferManager();
		IPath path = file.getFullPath();
		manager.connect(path, LocationKind.IFILE, pm);
		fBuffer = manager.getTextFileBuffer(path, LocationKind.IFILE);
		IDocument result = fBuffer.getDocument();
		return result;
	}

	@Override
	protected void commit(IDocument document, IProgressMonitor pm) throws CoreException {

	}

	@Override
	protected void releaseDocument(IDocument document, IProgressMonitor pm) throws CoreException {
		Assert.isTrue(fAcquireCount > 0);
		if (fAcquireCount == 1) {
			ITextFileBufferManager manager = FileBuffers.getTextFileBufferManager();
			manager.disconnect(file.getFullPath(), LocationKind.IFILE, pm);
		}
		fAcquireCount--;
	}

	@Override
	protected Change createUndoChange(UndoEdit edit) {
		throw new UnsupportedOperationException("Should not be called!"); //$NON-NLS-1$
	}

	@Override
	public void initializeValidationData(IProgressMonitor pm) {

	}

	@Override
	public RefactoringStatus isValid(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		return RefactoringStatus.create(Status.OK_STATUS);
	}

	@Override
	public Object getModifiedElement() {
		return delegate.getModifiedElement();
	}

	@Override
	public Change perform(IProgressMonitor pm) throws CoreException {
		pm.beginTask("", 3); //$NON-NLS-1$
		IDocument document = null;

		try {
			IFileBuffer buffer = FileBuffers.getTextFileBufferManager().getFileBuffer(file.getFullPath(),
					LocationKind.IFILE);

			document = acquireDocument(SubMonitor.convert(pm, 1));

			int offset = LSPEclipseUtils.toOffset(textEdit.getRange().getStart(), document);
			int length = LSPEclipseUtils.toOffset(textEdit.getRange().getEnd(), document) - offset;
			if (buffer != null) {
				delegate = new DocumentChange("Change in document " + file, document); //$NON-NLS-1$
			} else {
				delegate = new TextFileChange("Change in file " + file.getName(), file); //$NON-NLS-1$
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
