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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
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
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.resource.DeleteResourceChange;
import org.eclipse.ltk.core.refactoring.resource.ResourceChange;
import org.eclipse.osgi.util.NLS;

public class CreateFileChange extends ResourceChange {

	private final URI uri;
	private final String fSource;
	private String fEncoding;
	private boolean fExplicitEncoding;
	private final long fStampToRestore;

	public CreateFileChange(URI uri, String source, String encoding) {
		this(uri, source, encoding, IResource.NULL_STAMP);
	}

	public CreateFileChange(URI uri, String source, String encoding, long stampToRestore) {
		Assert.isNotNull(uri, "uri"); //$NON-NLS-1$
		Assert.isNotNull(source, "source"); //$NON-NLS-1$
		this.uri = uri;
		fSource= source;
		fEncoding= encoding;
		fExplicitEncoding= fEncoding != null;
		fStampToRestore= stampToRestore;
	}

	protected void setEncoding(String encoding, boolean explicit) {
		Assert.isNotNull(encoding, "encoding"); //$NON-NLS-1$
		fEncoding= encoding;
		fExplicitEncoding= explicit;
	}

	@Override
	public String getName() {
		return NLS.bind(Messages.edit_CreateFile, this.uri);
	}

	@Override
	protected IFile getModifiedResource() {
		return LSPEclipseUtils.getFileHandle(this.uri.toString());
	}

	@Override
	public RefactoringStatus isValid(IProgressMonitor pm) throws CoreException {
		RefactoringStatus result= new RefactoringStatus();

		IFileInfo jFile = EFS.getStore(this.uri).fetchInfo();
		if (jFile.exists()) {
			result.addFatalError("File " + this.uri + " already exists"); //$NON-NLS-1$ //$NON-NLS-2$
			return result;
		}
		return result;
	}

	@Override
	public Change perform(IProgressMonitor pm) throws CoreException {
		pm.beginTask(NLS.bind(Messages.edit_CreateFile, uri), 3);

		initializeEncoding();

		try (InputStream is= new ByteArrayInputStream(fSource.getBytes(fEncoding))) {

			IFile ifile = LSPEclipseUtils.getFileHandle(this.uri.toString());

			if (ifile != null) {
				List<IFolder> foldersToCreate = new ArrayList<>();
				IContainer parent = ifile.getParent();
				while (!parent.exists() && parent.getType() == IResource.FOLDER) {
					foldersToCreate.add((IFolder) parent);
					parent = parent.getParent();
				}
				Collections.reverse(foldersToCreate);
				for (IFolder folder : foldersToCreate) {
					folder.create(true, false, pm);
				}

				ifile.create(is, false, SubMonitor.convert(pm, 1));
				if (fStampToRestore != IResource.NULL_STAMP) {
					ifile.revertModificationStamp(fStampToRestore);
				}
				if (fExplicitEncoding) {
					ifile.setCharset(fEncoding, SubMonitor.convert(pm, 1));
				} else {
					pm.worked(1);
				}
				if (foldersToCreate.isEmpty()) {
					return new DeleteResourceChange(ifile.getFullPath(), true);
				} else {
					CompositeChange undoChange = new CompositeChange("Undo " + getName()); //$NON-NLS-1$
					undoChange.add(new DeleteResourceChange(ifile.getFullPath(), true));
					Collections.reverse(foldersToCreate);
					for (IFolder folder : foldersToCreate) {
						new DeleteResourceChange(folder.getFullPath(), true);
					}
					return undoChange;
				}
			} else {
				File file = new File(this.uri);
				Files.createDirectories(file.getParentFile().toPath());
				if (!file.createNewFile()) {
					throw new IOException(String.format("Failed to create file '%s'",file.toString())); //$NON-NLS-1$
				}
			}
		} catch (Exception e) {
			LanguageServerPlugin.logError(e);
			throw new CoreException(new Status(IStatus.ERROR, LanguageServerPlugin.PLUGIN_ID, e.getMessage(), e));
		} finally {
			pm.done();
		}
		return null;
	}

	private void initializeEncoding() {
		if (fEncoding == null) {
			fExplicitEncoding= false;
			IFile ifile = getModifiedResource();
			if (ifile != null) {
				try {
					if (ifile.exists()) {
						fEncoding = ifile.getCharset(false);
						if (fEncoding == null) {
							fEncoding = ifile.getCharset(true);
						} else {
							fExplicitEncoding= true;
						}
					} else {
						IContentType contentType = Platform.getContentTypeManager().findContentTypeFor(ifile.getName());
						if (contentType != null)
							fEncoding= contentType.getDefaultCharset();
						if (fEncoding == null)
							fEncoding = ifile.getCharset(true);
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
