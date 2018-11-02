/*******************************************************************************
 * Copyright (c) 2000, 2016 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.lsp4e.refactoring;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileInfo;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.resource.DeleteResourceChange;
import org.eclipse.ltk.core.refactoring.resource.ResourceChange;
import org.eclipse.osgi.util.NLS;

public class CreateFileChange extends ResourceChange {

	private IPath fPath;
	private String fSource;
	private String fEncoding;
	private boolean fExplicitEncoding;
	private long fStampToRestore;
	private List<IFolder> foldersToCreate;

	public CreateFileChange(IPath path, String source, String encoding) {
		this(path, source, encoding, IResource.NULL_STAMP);
	}

	public CreateFileChange(IPath path, String source, String encoding, long stampToRestore) {
		Assert.isNotNull(path, "path"); //$NON-NLS-1$
		Assert.isNotNull(source, "source"); //$NON-NLS-1$
		fPath= path;
		fSource= source;
		fEncoding= encoding;
		fExplicitEncoding= fEncoding != null;
		fStampToRestore= stampToRestore;
	}

	private CreateFileChange(IPath path, String source, String encoding, long stampToRestore, boolean explicit) {
		Assert.isNotNull(path, "path"); //$NON-NLS-1$
		Assert.isNotNull(source, "source"); //$NON-NLS-1$
		Assert.isNotNull(encoding, "encoding"); //$NON-NLS-1$
		fPath= path;
		fSource= source;
		fEncoding= encoding;
		fStampToRestore= stampToRestore;
		fExplicitEncoding= explicit;
	}

	protected void setEncoding(String encoding, boolean explicit) {
		Assert.isNotNull(encoding, "encoding"); //$NON-NLS-1$
		fEncoding= encoding;
		fExplicitEncoding= explicit;
	}

	@Override
	public String getName() {
		return NLS.bind(Messages.edit_CreateFile, this.fPath);
	}

	protected String getSource() {
		return fSource;
	}

	protected IPath getPath() {
		return fPath;
	}

	@Override
	protected IResource getModifiedResource() {
		return ResourcesPlugin.getWorkspace().getRoot().getFile(fPath);
	}

	@Override
	public RefactoringStatus isValid(IProgressMonitor pm) throws CoreException {
		RefactoringStatus result= new RefactoringStatus();
		IFile file= ResourcesPlugin.getWorkspace().getRoot().getFile(fPath);

		URI location= file.getLocationURI();
		if (location == null) {
			result.addFatalError("Unknown location for file " + file.getFullPath()); //$NON-NLS-1$
			return result;
		}

		IFileInfo jFile= EFS.getStore(location).fetchInfo();
		if (jFile.exists()) {
			result.addFatalError("File " + location + " already exists"); //$NON-NLS-1$ //$NON-NLS-2$
			return result;
		}
		return result;
	}

	@Override
	public Change perform(IProgressMonitor pm) throws CoreException, OperationCanceledException {

		InputStream is= null;
		try {
			pm.beginTask(NLS.bind(Messages.edit_CreateFile, fPath), 3);

			initializeEncoding();
			IFile file= getOldFile(new SubProgressMonitor(pm, 1));

			try {
				foldersToCreate = new ArrayList<>();
				IContainer parent = file.getParent();
				while (!parent.exists() && parent.getType() == IResource.FOLDER) {
					foldersToCreate.add((IFolder) parent);
					parent = parent.getParent();
				}
				Collections.reverse(foldersToCreate);
				for (IFolder folder : foldersToCreate) {
					folder.create(true, false, pm);
				}
				is = new ByteArrayInputStream(fSource.getBytes(fEncoding));
				file.create(is, false, new SubProgressMonitor(pm, 1));
				if (fStampToRestore != IResource.NULL_STAMP) {
					file.revertModificationStamp(fStampToRestore);
				}
				if (fExplicitEncoding) {
					file.setCharset(fEncoding, new SubProgressMonitor(pm, 1));
				} else {
					pm.worked(1);
				}
				if (foldersToCreate.isEmpty()) {
					return new DeleteResourceChange(file.getFullPath(), true);
				} else {
					CompositeChange undoChange = new CompositeChange("Undo " + getName()); //$NON-NLS-1$
					undoChange.add(new DeleteResourceChange(file.getFullPath(), true));
					Collections.reverse(foldersToCreate);
					for (IFolder folder : foldersToCreate) {
						new DeleteResourceChange(folder.getFullPath(), true);
					}
					return undoChange;
				}
			} catch (UnsupportedEncodingException e) {
				throw new CoreException(new Status(IStatus.ERROR, LanguageServerPlugin.PLUGIN_ID, e.getMessage(), e));
			}
		} finally {
			try {
				if (is != null)
					is.close();
			} catch (IOException ioe) {
				throw new CoreException(
						new Status(IStatus.ERROR, LanguageServerPlugin.PLUGIN_ID, ioe.getMessage(), ioe));
			} finally {
				pm.done();
			}
		}
	}

	protected IFile getOldFile(IProgressMonitor pm) throws OperationCanceledException {
		pm.beginTask("", 1); //$NON-NLS-1$
		try {
			return ResourcesPlugin.getWorkspace().getRoot().getFile(fPath);
		} finally {
			pm.done();
		}
	}

	private void initializeEncoding() {
		if (fEncoding == null) {
			fExplicitEncoding= false;
			IFile file= ResourcesPlugin.getWorkspace().getRoot().getFile(fPath);
			if (file != null) {
				try {
					if (file.exists()) {
						fEncoding= file.getCharset(false);
						if (fEncoding == null) {
							fEncoding= file.getCharset(true);
						} else {
							fExplicitEncoding= true;
						}
					} else {
						IContentType contentType= Platform.getContentTypeManager().findContentTypeFor(file.getName());
						if (contentType != null)
							fEncoding= contentType.getDefaultCharset();
						if (fEncoding == null)
							fEncoding= file.getCharset(true);
					}
				} catch (CoreException e) {
					fEncoding= ResourcesPlugin.getEncoding();
					fExplicitEncoding= true;
				}
			} else {
				fEncoding= ResourcesPlugin.getEncoding();
				fExplicitEncoding= true;
			}
		}
		Assert.isNotNull(fEncoding);
	}
}
