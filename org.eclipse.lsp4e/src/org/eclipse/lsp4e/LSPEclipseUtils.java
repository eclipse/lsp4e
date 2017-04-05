/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Michał Niewrzał (Rogue Wave Software Inc.)
 *******************************************************************************/
package org.eclipse.lsp4e;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.List;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.undo.DocumentUndoManagerRegistry;
import org.eclipse.text.undo.IDocumentUndoManager;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * Some utility methods to convert between Eclipse and LS-API types
 */
public class LSPEclipseUtils {

	public static Position toPosition(int offset, IDocument document) throws BadLocationException {
		Position res = new Position();
		res.setLine(document.getLineOfOffset(offset));
		res.setCharacter(offset - document.getLineInformationOfOffset(offset).getOffset());
		return res;
	}

	public static int toOffset(Position position, IDocument document) throws BadLocationException {
		return document.getLineInformation(position.getLine()).getOffset() + position.getCharacter();
	}

	public static TextDocumentPositionParams toTextDocumentPosistionParams(URI fileUri, int offset, IDocument document)
			throws BadLocationException {
		Position start = toPosition(offset, document);
		TextDocumentPositionParams param = new TextDocumentPositionParams();
		param.setPosition(start);
		param.setUri(fileUri.toString());
		TextDocumentIdentifier id = new TextDocumentIdentifier();
		id.setUri(fileUri.toString());
		param.setTextDocument(id);
		return param;
	}

	public static int toEclipseMarkerSeverity(DiagnosticSeverity lspSeverity) {
		switch (lspSeverity) {
		case Error: return IMarker.SEVERITY_ERROR;
		case Warning: return IMarker.SEVERITY_WARNING;
		default:
			return IMarker.SEVERITY_INFO;
		}
	}

	public static IResource findResourceFor(String uri) {
		uri = uri.replace("file:///", "file:/");  //$NON-NLS-1$//$NON-NLS-2$
		uri = uri.replace("file://", "file:/");  //$NON-NLS-1$//$NON-NLS-2$
		IPath path = Path.fromOSString(new File(URI.create(uri)).getAbsolutePath());
		IProject project = null;
		for (IProject aProject : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
			IPath location = aProject.getLocation();
			if (location != null && location.isPrefixOf(path) && (project == null || project.getLocation().segmentCount() < location.segmentCount())) {
				project = aProject;
			}
		}
		if (project == null) {
			return null;
		}
		IPath projectRelativePath = path.removeFirstSegments(project.getLocation().segmentCount());
		if (projectRelativePath.isEmpty()) {
			return project;
		} else {
			return project.findMember(projectRelativePath);
		}
	}

	public static void applyEdit(TextEdit textEdit, IDocument document) throws BadLocationException {
		document.replace(
				LSPEclipseUtils.toOffset(textEdit.getRange().getStart(), document),
				LSPEclipseUtils.toOffset(textEdit.getRange().getEnd(), document) - LSPEclipseUtils.toOffset(textEdit.getRange().getStart(), document),
				textEdit.getNewText());
	}

	/**
	 * Method will apply all edits to document as single modification. Needs to
	 * be executed in UI thread.
	 *
	 * @param document
	 *            document to modify
	 * @param edits
	 *            list of LSP TextEdits
	 */
	public static void applyEdits(IDocument document, List<? extends TextEdit> edits) {
		if (document == null || edits.isEmpty()) {
			return;
		}

		IDocumentUndoManager manager = DocumentUndoManagerRegistry.getDocumentUndoManager(document);
		if (manager != null) {
			manager.beginCompoundChange();
		}

		MultiTextEdit edit = new MultiTextEdit();
		for (TextEdit textEdit : edits) {
			if (textEdit != null) {
				try {
					int offset = LSPEclipseUtils.toOffset(textEdit.getRange().getStart(), document);
					int length = LSPEclipseUtils.toOffset(textEdit.getRange().getEnd(), document) - offset;
					edit.addChild(new ReplaceEdit(offset, length, textEdit.getNewText()));
				} catch (BadLocationException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		try {
			edit.apply(document);
		} catch (MalformedTreeException | BadLocationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (manager != null) {
			manager.endCompoundChange();
		}
	}

	public static IDocument getDocument(IResource resource) {
		if (resource == null) {
			return null;
		}

		ITextFileBufferManager bufferManager = FileBuffers.getTextFileBufferManager();
		IDocument document = null;
		ITextFileBuffer buffer = bufferManager.getTextFileBuffer(resource.getFullPath(), LocationKind.IFILE);
		if (buffer != null) {
			document = buffer.getDocument();
		} else if (resource.getType() == IResource.FILE) {
			try {
				bufferManager.connect(resource.getFullPath(), LocationKind.IFILE, new NullProgressMonitor());
			} catch (CoreException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return document;
			}
			buffer = bufferManager.getTextFileBuffer(resource.getFullPath(), LocationKind.IFILE);
			if (buffer != null) {
				document = buffer.getDocument();
			}
		}
		return document;
	}

	public static void openInEditor(Location location, IWorkbenchPage page) {
		IEditorPart part = null;
		IDocument targetDocument = null;
		IResource targetResource = LSPEclipseUtils.findResourceFor(location.getUri());
		try {
			if (targetResource != null && targetResource.getType() == IResource.FILE) {
				part = IDE.openEditor(page, (IFile) targetResource);
				targetDocument = FileBuffers.getTextFileBufferManager()
				        .getTextFileBuffer(targetResource.getFullPath(), LocationKind.IFILE).getDocument();
			} else {
				URI fileUri = URI.create(location.getUri());
				part = IDE.openEditor(page, fileUri, null, true);
				targetDocument = FileBuffers.getTextFileBufferManager()
				        .getTextFileBuffer(new Path(fileUri.getPath()), LocationKind.LOCATION).getDocument();
			}
		} catch (PartInitException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			if (part instanceof AbstractTextEditor) {
				AbstractTextEditor editor = (AbstractTextEditor) part;
				int offset = LSPEclipseUtils.toOffset(location.getRange().getStart(), targetDocument);
				int endOffset = LSPEclipseUtils.toOffset(location.getRange().getEnd(), targetDocument);
				editor.getSelectionProvider()
				        .setSelection(new TextSelection(offset, endOffset > offset ? endOffset - offset : 0));
			}
		} catch (BadLocationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static IDocument getDocument(ITextEditor editor) {
		try {
			Method getSourceViewerMethod= AbstractTextEditor.class.getDeclaredMethod("getSourceViewer"); //$NON-NLS-1$
			getSourceViewerMethod.setAccessible(true);
			ITextViewer viewer = (ITextViewer) getSourceViewerMethod.invoke(editor);
			return viewer.getDocument();
		} catch (Exception ex) {
			LanguageServerPlugin.logError(ex);
			return null;
		}
	}

	public static void applyWorkspaceEdit(WorkspaceEdit wsEdit) {
		for (java.util.Map.Entry<String, List<TextEdit>> edit : wsEdit.getChanges().entrySet()) {
			String uri = edit.getKey();
			IDocument document = LSPEclipseUtils.getDocument(LSPEclipseUtils.findResourceFor(uri));
			LSPEclipseUtils.applyEdits(document, edit.getValue());
		}
	}

	public static String toUri(IPath absolutePath) {
		return toUri(absolutePath.toFile());
	}

	public static String toUri(IResource resource) {
		return toUri(resource.getLocation());
	}

	public static String toUri(File file) {
		// URI scheme specified by language server protocol and LSP
		return "file://" + file.getAbsoluteFile().toURI().getPath(); //$NON-NLS-1$
	}

}
