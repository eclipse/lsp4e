/*******************************************************************************
 * Copyright (c) 2016, 2022 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Michał Niewrzał (Rogue Wave Software Inc.)
 *  Lucas Bullen (Red Hat Inc.) - Get IDocument from IEditorInput
 *  Angelo Zerr <angelo.zerr@gmail.com> - Bug 525400 - [rename] improve rename support with ltk UI
 *  Remy Suen <remy.suen@gmail.com> - Bug 520052 - Rename assumes that workspace edits are in reverse order
 *  Martin Lippert (Pivotal Inc.) - bug 531452, bug 532305
 *  Alex Boyko (Pivotal Inc.) - bug 543435 (WorkspaceEdit apply handling)
 *  Markus Ofterdinger (SAP SE) - Bug 552140 - NullPointerException in LSP4E
 *  Rubén Porras Campo (Avaloq) - Bug 576425 - Support Remote Files
 *  Pierre-Yves Bigourdan <pyvesdev@gmail.com> - Issue 29
 *******************************************************************************/
package org.eclipse.lsp4e;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.IFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileInfo;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.internal.utils.FileUtil;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.core.runtime.content.IContentTypeManager;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.RewriteSessionEditProcessor;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.lsp4e.operations.references.LSSearchQuery;
import org.eclipse.lsp4e.refactoring.CreateFileChange;
import org.eclipse.lsp4e.refactoring.DeleteExternalFile;
import org.eclipse.lsp4e.refactoring.LSPTextChange;
import org.eclipse.lsp4j.Color;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.CreateFile;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DeleteFile;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.LinkedEditingRangeParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RenameFile;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.TypeDefinitionParams;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.DocumentChange;
import org.eclipse.ltk.core.refactoring.PerformChangeOperation;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.eclipse.ltk.core.refactoring.resource.DeleteResourceChange;
import org.eclipse.mylyn.wikitext.markdown.MarkdownLanguage;
import org.eclipse.mylyn.wikitext.parser.MarkupParser;
import org.eclipse.search.ui.NewSearchUI;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.RGBA;
import org.eclipse.swt.widgets.Display;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.undo.DocumentUndoManagerRegistry;
import org.eclipse.text.undo.IDocumentUndoManager;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IPathEditorInput;
import org.eclipse.ui.IURIEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.browser.IWorkbenchBrowserSupport;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.intro.config.IIntroURL;
import org.eclipse.ui.intro.config.IntroURLFactory;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * Some utility methods to convert between Eclipse and LS-API types
 */
public class LSPEclipseUtils {

	private static final String DEFAULT_LABEL = "LSP Workspace Edit"; //$NON-NLS-1$
	public static final String HTTP = "http"; //$NON-NLS-1$
	public static final String INTRO_URL = "http://org.eclipse.ui.intro"; //$NON-NLS-1$
	public static final String FILE_URI = "file://"; //$NON-NLS-1$

	private static final String FILE_SCHEME = "file"; //$NON-NLS-1$
	private static final String FILE_SLASH = "file:/"; //$NON-NLS-1$
	private static final String HTML = "html"; //$NON-NLS-1$
	private static final String MARKDOWN = "markdown"; //$NON-NLS-1$
	private static final String MD = "md"; //$NON-NLS-1$
	private static final int MAX_BROWSER_NAME_LENGTH = 30;
	private static final MarkupParser MARKDOWN_PARSER = new MarkupParser(new MarkdownLanguage());

	private LSPEclipseUtils() {
		// this class shouldn't be instantiated
	}

	public static Position toPosition(int offset, IDocument document) throws BadLocationException {
		final var res = new Position();
		res.setLine(document.getLineOfOffset(offset));
		res.setCharacter(offset - document.getLineInformationOfOffset(offset).getOffset());
		return res;
	}

	public static int toOffset(Position position, IDocument document) throws BadLocationException {
		return document.getLineInformation(position.getLine()).getOffset() + position.getCharacter();
	}

	public static boolean isOffsetInRange(int offset, Range range, IDocument document) {
		try {
			return offset != -1 && offset >= toOffset(range.getStart(), document)
					&& offset <= toOffset(range.getEnd(), document);
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
			return false;
		}
	}

	public static CompletionParams toCompletionParams(URI fileUri, int offset, IDocument document)
			throws BadLocationException {
		Position start = toPosition(offset, document);
		final var param = new CompletionParams();
		param.setPosition(start);
		final var id = new TextDocumentIdentifier();
		id.setUri(fileUri.toString());
		param.setTextDocument(id);
		return param;
	}

	/**
	 * @param fileUri
	 * @param offset
	 * @param document
	 * @return
	 * @throws BadLocationException
	 * @deprecated Use {@link #toTextDocumentPosistionParams(int, IDocument)}
	 *             instead
	 */
	@Deprecated
	public static TextDocumentPositionParams toTextDocumentPosistionParams(URI fileUri, int offset, IDocument document)
			throws BadLocationException {
		Position start = toPosition(offset, document);
		final var param = new TextDocumentPositionParams();
		param.setPosition(start);
		final var id = new TextDocumentIdentifier();
		id.setUri(fileUri.toString());
		param.setTextDocument(id);
		return param;
	}

	private static <T extends TextDocumentPositionParams> T toTextDocumentPositionParamsCommon(@NonNull T param,  int offset, IDocument document)
			throws BadLocationException {
		URI uri = toUri(document);
		Position start = toPosition(offset, document);
		param.setPosition(start);
		final var id = new TextDocumentIdentifier();
		if (uri != null) {
			id.setUri(uri.toString());
		}
		param.setTextDocument(id);
		return param;
	}

	public static HoverParams toHoverParams(int offset, IDocument document) throws BadLocationException {
		return toTextDocumentPositionParamsCommon(new HoverParams(), offset, document);
	}

	public static SignatureHelpParams toSignatureHelpParams(int offset, IDocument document)
			throws BadLocationException {
		return toTextDocumentPositionParamsCommon(new SignatureHelpParams(), offset, document);
	}

	public static TextDocumentPositionParams toTextDocumentPosistionParams(int offset, IDocument document)
			throws BadLocationException {
		return toTextDocumentPositionParamsCommon(new TextDocumentPositionParams(), offset, document);
	}

	public static DefinitionParams toDefinitionParams(TextDocumentPositionParams params) {
		return toTextDocumentPositionParamsCommon(new DefinitionParams(), params);
	}

	public static TypeDefinitionParams toTypeDefinitionParams(TextDocumentPositionParams params) {
		return toTextDocumentPositionParamsCommon(new TypeDefinitionParams(), params);
	}

	public static LinkedEditingRangeParams toLinkedEditingRangeParams(TextDocumentPositionParams params) {
		return toTextDocumentPositionParamsCommon(new LinkedEditingRangeParams(), params);
	}

	/**
	 * Convert generic TextDocumentPositionParams to type specific version. Should
	 * only be used for T where T adds no new fields.
	 */
	private static <T extends TextDocumentPositionParams> T toTextDocumentPositionParamsCommon(
			@NonNull T specificParams, TextDocumentPositionParams genericParams) {
		if (genericParams.getPosition() != null) {
			specificParams.setPosition(genericParams.getPosition());
		}
		if (genericParams.getTextDocument() != null) {
			specificParams.setTextDocument(genericParams.getTextDocument());
		}
		return specificParams;
	}

	private static ITextFileBuffer toBuffer(IDocument document) {
		ITextFileBufferManager bufferManager = FileBuffers.getTextFileBufferManager();
		if (bufferManager == null)
			return null;
		return bufferManager.getTextFileBuffer(document);
	}

	public static URI toUri(IDocument document) {
		IFile file = getFile(document);
		if (file != null) {
			return toUri(file);
		} else {
			ITextFileBuffer buffer = toBuffer(document);
			if (buffer != null) {
				IPath path = toPath(buffer);
				if(path != null) {
					return toUri(path.toFile());
				} else {
					return buffer.getFileStore().toURI();
				}
			}
		}
		return null;
	}

	private static IPath toPath(IFileBuffer buffer) {
		if (buffer != null) {
			return buffer.getLocation();
		}
		return null;
	}

	public static IPath toPath(IDocument document) {
		return toPath(toBuffer(document));
	}

	public static int toEclipseMarkerSeverity(DiagnosticSeverity lspSeverity) {
		if (lspSeverity == null) {
			// if severity is empty it is up to the client to interpret diagnostics
			return IMarker.SEVERITY_ERROR;
		}
		return switch (lspSeverity) {
		case Error -> IMarker.SEVERITY_ERROR;
		case Warning -> IMarker.SEVERITY_WARNING;
		default -> IMarker.SEVERITY_INFO;
		};
	}

	@Nullable
	public static IFile getFileHandle(@Nullable URI uri) {
		if (uri == null) {
			return null;
		}
		if (FILE_SCHEME.equals(uri.getScheme())) {
			IWorkspaceRoot wsRoot = ResourcesPlugin.getWorkspace().getRoot();
			IFile[] files = wsRoot.findFilesForLocationURI(uri);
			if (files.length > 0) {
				return files[0];
			}
			return null;
		} else {
			return Adapters.adapt(uri.toString(), IFile.class, true);
		}
	}

	@Nullable
	public static IFile getFileHandle(@Nullable String uri) {
		if (uri == null || uri.isEmpty()) {
			return null;
		}
		if (uri.startsWith(FILE_SLASH)) {
			URI uriObj = URI.create(uri);
			return getFileHandle(uriObj);
		} else {
			return Adapters.adapt(uri, IFile.class, true);
		}
	}

	@Nullable
	public static IResource findResourceFor(@Nullable String uri) {
		if (uri == null || uri.isEmpty()) {
			return null;
		}
		if (uri.startsWith(FILE_SLASH)) {
			return findResourceFor(URI.create(uri));
		} else {
			return Adapters.adapt(uri, IResource.class, true);
		}
	}

	@Nullable
	public static IResource findResourceFor(@Nullable URI uri) {
		if (uri == null) {
			return null;
		}
		if (FILE_SCHEME.equals(uri.getScheme())) {
			IWorkspaceRoot wsRoot = ResourcesPlugin.getWorkspace().getRoot();

			IFile[] files = wsRoot.findFilesForLocationURI(uri);
			if (files.length > 0) {
				IFile file = findMostNested(files);
				if(file!=null) {
					return file;
				}
			}

			final IContainer[] containers = wsRoot.findContainersForLocationURI(uri);
			if (containers.length > 0) {
				return containers[0];
			}
			return null;
		} else {
			return Adapters.adapt(uri, IResource.class, true);
		}
	}

	public static IFile findMostNested(IFile[] files) {
		int shortestLen = Integer.MAX_VALUE;
		IFile shortest = null;
		for (IFile file : files) {
			/*
			 * IWorkspaceRoot#findFilesForLocationURI returns IFile objects for folders instead of null.
			 * IWorkspaceRoot#findContainersForLocationURI returns IFolder objects for regular files instead of null.
			 * Thus we have to manually check the file system entry to determine the correct type to return.
			 */
			if(!file.isVirtual() && !file.getLocation().toFile().isDirectory()) {
				IPath path = file.getFullPath();
				if (path.segmentCount() < shortestLen) {
					shortest = file;
					shortestLen = path.segmentCount();
				}
			}
		}
		return shortest;
	}

	public static void applyEdit(TextEdit textEdit, IDocument document) throws BadLocationException {
		document.replace(
				toOffset(textEdit.getRange().getStart(), document),
				toOffset(textEdit.getRange().getEnd(), document) - toOffset(textEdit.getRange().getStart(), document),
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
	 * @throws BadLocationException
	 */
	public static void applyEdits(IDocument document, List<? extends TextEdit> edits) throws BadLocationException {
		if (document == null || edits == null || edits.isEmpty()) {
			return;
		}

		final var edit = new MultiTextEdit();
		for (TextEdit textEdit : edits) {
			if (textEdit != null) {
				int offset = toOffset(textEdit.getRange().getStart(), document);
				int length = toOffset(textEdit.getRange().getEnd(), document) - offset;
				if (length < 0) {
					// Must be a bad location: we bail out to avoid corrupting the document.
					throw new BadLocationException("Invalid location information found applying edits"); //$NON-NLS-1$
				}
				edit.addChild(new ReplaceEdit(offset, length, textEdit.getNewText()));
			}
		}

		IDocumentUndoManager manager = DocumentUndoManagerRegistry.getDocumentUndoManager(document);
		if (manager != null) {
			manager.beginCompoundChange();
		}
		try {
			final var editProcessor = new RewriteSessionEditProcessor(document, edit,
					org.eclipse.text.edits.TextEdit.NONE);
			editProcessor.performEdits();
		} catch (MalformedTreeException | BadLocationException e) {
			LanguageServerPlugin.logError(e);
		}
		if (manager != null) {
			manager.endCompoundChange();
		}
	}

	@Nullable
	public static IDocument getDocument(@Nullable IResource resource) {
		if (resource == null) {
			return null;
		}

		IDocument document = getExistingDocument(resource);

		if (document == null && resource.getType() == IResource.FILE) {
			ITextFileBufferManager bufferManager = FileBuffers.getTextFileBufferManager();
			if (bufferManager == null)
				return document;
			try {
				bufferManager.connect(resource.getFullPath(), LocationKind.IFILE, new NullProgressMonitor());
			} catch (CoreException e) {
				LanguageServerPlugin.logError(e);
				return document;
			}

			ITextFileBuffer buffer = bufferManager.getTextFileBuffer(resource.getFullPath(), LocationKind.IFILE);
			if (buffer != null) {
				document = buffer.getDocument();
			}
		}

		return document;
	}

	@Nullable
	public static IDocument getExistingDocument(@Nullable IResource resource) {
		if (resource == null) {
			return null;
		}
		ITextFileBufferManager bufferManager = FileBuffers.getTextFileBufferManager();
		if (bufferManager == null)
			return null;
		ITextFileBuffer buffer = bufferManager.getTextFileBuffer(resource.getFullPath(), LocationKind.IFILE);
		if (buffer != null) {
			return buffer.getDocument();
		}
		else {
			return null;
		}
	}

	@Nullable
	private static IDocument getDocument(URI uri) {
		if (uri == null) {
			return null;
		}
		IResource resource = findResourceFor(uri);
		if (resource != null) {
			return getDocument(resource);
		}
		if (!fromUri(uri).isFile()) {
			return null;
		}


		IDocument document = null;
		IFileStore store = null;
		try {
			store = EFS.getStore(uri);
		} catch (CoreException e) {
			LanguageServerPlugin.logError(e);
			return null;
		}
		ITextFileBufferManager bufferManager = FileBuffers.getTextFileBufferManager();
		if (bufferManager == null)
			return null;
		ITextFileBuffer buffer = bufferManager.getFileStoreTextFileBuffer(store);
		if (buffer != null) {
			document = buffer.getDocument();
		} else {
			try {
				bufferManager.connectFileStore(store, new NullProgressMonitor());
			} catch (CoreException e) {
				LanguageServerPlugin.logError(e);
				return document;
			}
			buffer = bufferManager.getFileStoreTextFileBuffer(store);
			if (buffer != null) {
				document = buffer.getDocument();
			}
		}
		return document;
	}

	public static void openInEditor(Location location, IWorkbenchPage page) {
		open(location.getUri(), page, location.getRange());
	}

	public static void openInEditor(LocationLink link, IWorkbenchPage page) {
		open(link.getTargetUri(), page, link.getTargetSelectionRange());

	}

	public static void open(String uri, IWorkbenchPage page, Range optionalRange) {
		if (uri.startsWith(HTTP)) {
			if (uri.startsWith(INTRO_URL)) {
				openIntroURL(uri);
			} else {
				openHttpLocationInBrowser(uri, page);
			}
		} else {
			openFileLocationInEditor(uri, page, optionalRange);
		}
	}

	protected static void openIntroURL(final String uri) {
		IIntroURL introUrl = IntroURLFactory.createIntroURL(uri);
		if (introUrl != null) {
			try {
				if (!introUrl.execute()) {
					LanguageServerPlugin.logWarning("Failed to execute IntroURL: " + uri, null); //$NON-NLS-1$
				}
			} catch (Exception t) {
				LanguageServerPlugin.logWarning("Error executing IntroURL: " + uri, t); //$NON-NLS-1$
			}
		}
	}

	protected static void openHttpLocationInBrowser(final String uri, IWorkbenchPage page) {
		page.getWorkbenchWindow().getShell().getDisplay().asyncExec(() -> {
			try {
				final var url = new URL(uri);

				IWorkbenchBrowserSupport browserSupport = page.getWorkbenchWindow().getWorkbench()
						.getBrowserSupport();

				String browserName = uri;
				if (browserName.length() > MAX_BROWSER_NAME_LENGTH) {
					browserName = uri.substring(0, MAX_BROWSER_NAME_LENGTH - 1) + '\u2026';
				}

				browserSupport
						.createBrowser(IWorkbenchBrowserSupport.AS_EDITOR | IWorkbenchBrowserSupport.LOCATION_BAR
								| IWorkbenchBrowserSupport.NAVIGATION_BAR, "lsp4e-symbols", browserName, uri) //$NON-NLS-1$
						.openURL(url);

			} catch (Exception e) {
				LanguageServerPlugin.logError(e);
			}
		});
	}

	protected static void openFileLocationInEditor(String uri, IWorkbenchPage page, Range optionalRange) {
		IEditorPart part = null;
		IDocument targetDocument = null;
		IResource targetResource = findResourceFor(uri);
		try {
			if (targetResource != null && targetResource.getType() == IResource.FILE) {
				part = IDE.openEditor(page, (IFile) targetResource);
			} else {
				URI fileUri = URI.create(uri).normalize();
				IFileStore fileStore =  EFS.getLocalFileSystem().getStore(fileUri);
				IFileInfo fetchInfo = fileStore.fetchInfo();
				if (!fetchInfo.isDirectory() && fetchInfo.exists()) {
					part = IDE.openEditorOnFileStore(page, fileStore);
				}
			}


			ITextEditor textEditor = Adapters.adapt(part, ITextEditor.class);
			if (textEditor != null) {
				targetDocument = textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
			}

		} catch (PartInitException e) {
			LanguageServerPlugin.logError(e);
		}
		try {
			if (targetDocument != null
				&& part != null && part.getEditorSite() != null && part.getEditorSite().getSelectionProvider() != null
				&& optionalRange != null)
			{
				ISelectionProvider selectionProvider = part.getEditorSite().getSelectionProvider();

				int offset = toOffset(optionalRange.getStart(), targetDocument);
				int endOffset = toOffset(optionalRange.getEnd(), targetDocument);
				selectionProvider.setSelection(new TextSelection(offset, endOffset > offset ? endOffset - offset : 0));
			}

		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
		}
	}

	public static IDocument getDocument(ITextEditor editor) {
		if (editor == null)
			return null;
		final IEditorInput editorInput = editor.getEditorInput();
		if (editorInput != null) {
			final IDocumentProvider documentProvider = editor.getDocumentProvider();
			if (documentProvider != null) {
				final IDocument document = documentProvider.getDocument(editorInput);
				if (document != null)
					return document;
			}
			IDocument res = getDocument(editorInput);
			if (res != null) {
				return res;
			}
		}
		if (editor instanceof AbstractTextEditor) {
			try {
				Method getSourceViewerMethod= AbstractTextEditor.class.getDeclaredMethod("getSourceViewer"); //$NON-NLS-1$
				getSourceViewerMethod.setAccessible(true);
				ITextViewer viewer = (ITextViewer) getSourceViewerMethod.invoke(editor);
				return (viewer == null) ? null : viewer.getDocument();
			} catch (Exception ex) {
				LanguageServerPlugin.logError(ex);
			}
		}
		return null;
	}

	public static IDocument getDocument(IEditorInput editorInput) {
		if (!editorInput.exists()) {
			// Shouldn't happen too often, but happens rather a lot in testing when
			// teardown runs when there are document setup actions still pending
			return null;
		}
		if(editorInput instanceof IFileEditorInput fileEditorInput) {
			return getDocument(fileEditorInput.getFile());
		}else if(editorInput instanceof IPathEditorInput pathEditorInput) {
			return getDocument(ResourcesPlugin.getWorkspace().getRoot().getFile(pathEditorInput.getPath()));
		}else if(editorInput instanceof IURIEditorInput uriEditorInput) {
			IResource resource = findResourceFor(uriEditorInput.getURI());
			if (resource != null) {
				return getDocument(resource);
			} else {
				return getDocument(uriEditorInput.getURI());
			}
		}
		return null;
	}

	/**
	 * Applies a workspace edit. It does simply change the underlying documents.
	 *
	 * @param wsEdit
	 */
	public static void applyWorkspaceEdit(WorkspaceEdit wsEdit) {
		applyWorkspaceEdit(wsEdit, null);
	}

	/**
	 * Applies a workspace edit. It does simply change the underlying documents.
	 *
	 * @param wsEdit
	 * @param label
	 */
	public static void applyWorkspaceEdit(WorkspaceEdit wsEdit, String label) {
		String name = label == null ? DEFAULT_LABEL : label;
		CompositeChange change = toCompositeChange(wsEdit, name);
		final var changeOperation = new PerformChangeOperation(change);
		changeOperation.setUndoManager(RefactoringCore.getUndoManager(), name);
		try {
			ResourcesPlugin.getWorkspace().run(changeOperation, new NullProgressMonitor());
		} catch (CoreException e) {
			LanguageServerPlugin.logError(e);
		}
	}

	/**
	 * Returns a ltk {@link CompositeChange} from a lsp {@link WorkspaceEdit}.
	 *
	 * @param wsEdit
	 * @param name
	 * @return a ltk {@link CompositeChange} from a lsp {@link WorkspaceEdit}.
	 */
	public static CompositeChange toCompositeChange(WorkspaceEdit wsEdit, String name) {
		final var change = new CompositeChange(name);
		List<Either<TextDocumentEdit, ResourceOperation>> documentChanges = wsEdit.getDocumentChanges();
		if (documentChanges != null) {
			// documentChanges are present, the latter are preferred over changes
			// see specification at
			// https://github.com/Microsoft/language-server-protocol/blob/master/protocol.md#workspaceedit
			documentChanges.stream().forEach(action -> {
				if (action.isLeft()) {
					TextDocumentEdit edit = action.getLeft();
					VersionedTextDocumentIdentifier id = edit.getTextDocument();
					URI uri = URI.create(id.getUri());
					List<TextEdit> textEdits = edit.getEdits();
					change.addAll(toChanges(uri, textEdits));
				} else if (action.isRight()) {
					ResourceOperation resourceOperation = action.getRight();
					if (resourceOperation instanceof CreateFile) {
						CreateFile createOperation = (CreateFile) resourceOperation;
						URI targetURI = URI.create(createOperation.getUri());
						File targetFile = fromUri(targetURI);
						if (targetFile.exists() && createOperation.getOptions() != null) {
							if (!createOperation.getOptions().getIgnoreIfExists()) {
								if (createOperation.getOptions().getOverwrite()) {
									final var edit = new TextEdit(null, ""); //$NON-NLS-1$
									change.add(new LSPTextChange("Overwrite", //$NON-NLS-1$
											targetURI, edit));
								} else {
									// TODO? Log, warn user...?
								}
							}
						} else {
							final var operation = new CreateFileChange(targetURI, "", null); //$NON-NLS-1$
							change.add(operation);
						}
					} else if (resourceOperation instanceof DeleteFile delete) {
						IResource resource = findResourceFor(delete.getUri());
						if (resource != null) {
							final var deleteChange = new DeleteResourceChange(resource.getFullPath(), true);
							change.add(deleteChange);
						} else {
							LanguageServerPlugin.logWarning(
									"Changes outside of visible projects are not supported at the moment.", null); //$NON-NLS-1$
						}
					} else if (resourceOperation instanceof RenameFile rename) {
						URI oldURI = URI.create(rename.getOldUri());
						URI newURI = URI.create(rename.getNewUri());
						IFile oldFile = getFileHandle(oldURI);
						IFile newFile = getFileHandle(newURI);
						DeleteResourceChange removeNewFile = null;
						if (newFile != null && newFile.exists()) {
							if (((RenameFile) resourceOperation).getOptions().getOverwrite()) {
								removeNewFile = new DeleteResourceChange(newFile.getFullPath(), true);
							} else if (((RenameFile) resourceOperation).getOptions().getIgnoreIfExists()) {
								return;
							}
						}
						String content = ""; //$NON-NLS-1$
						String encoding = null;
						if (oldFile != null && oldFile.exists()) {
							try (var stream = new ByteArrayOutputStream((int) oldFile.getLocation().toFile().length());
									InputStream inputStream = oldFile.getContents();) {
								FileUtil.transferStreams(inputStream, stream, newURI.toString(), null);
								content = new String(stream.toByteArray());
								encoding = oldFile.getCharset();
							} catch (IOException | CoreException e) {
								LanguageServerPlugin.logError(e);
							}
						}
						final var createFileChange = new CreateFileChange(newURI, content, encoding);
						change.add(createFileChange);
						if (removeNewFile != null) {
							change.add(removeNewFile);
						}
						if (oldFile != null) {
							final var removeOldFile = new DeleteResourceChange(oldFile.getFullPath(), true);
							change.add(removeOldFile);
						} else {
							change.add(new DeleteExternalFile(new File(oldURI)));
						}
					}
				}
			});
		} else {
			Map<String, List<TextEdit>> changes = wsEdit.getChanges();
			if (changes != null) {
				for (java.util.Map.Entry<String, List<TextEdit>> edit : changes.entrySet()) {
					URI uri = URI.create(edit.getKey());
					List<TextEdit> textEdits = edit.getValue();
					change.addAll(toChanges(uri, textEdits));
				}
			}
		}
		return change;
	}

	/**
	 * Transform LSP {@link TextEdit} list into ltk {@link DocumentChange} and add
	 * it in the given ltk {@link CompositeChange}.
	 *
	 * @param uri
	 *            document URI to update
	 * @param textEdits
	 *            LSP text edits
	 * @param change
	 *            ltk change to update
	 */
	private static LSPTextChange[] toChanges(URI uri, List<TextEdit> textEdits) {
		Collections.sort(textEdits, Comparator.comparing(edit -> edit.getRange().getStart(),
				Comparator.comparingInt(Position::getLine).thenComparingInt(Position::getCharacter).reversed()));
		return textEdits.stream().map(te -> new LSPTextChange("LSP Text Edit", uri, te)) //$NON-NLS-1$
				.toArray(LSPTextChange[]::new);
	}

	public static URI toUri(IPath absolutePath) {
		return toUri(absolutePath.toFile());
	}

	public static URI toUri(@NonNull IResource resource) {
		URI adaptedURI = Adapters.adapt(resource, URI.class, true);
		if (adaptedURI != null) {
			return adaptedURI;
		}
		IPath location = resource.getLocation();
		if (location != null) {
			return toUri(location);
		}
		return resource.getLocationURI();
	}

	@Nullable public static URI toUri(@NonNull IFileBuffer buffer) {
		IFile res = ResourcesPlugin.getWorkspace().getRoot().getFile(buffer.getLocation());
		if (res != null) {
			URI uri = toUri(res);
			if (uri != null) {
				return uri;
			}
		}
		return buffer.getFileStore().toURI();
	}

	public static URI toUri(File file) {
		// URI scheme specified by language server protocol and LSP
		try {
			return new URI("file", "", file.getAbsoluteFile().toURI().getPath(), null); //$NON-NLS-1$ //$NON-NLS-2$
		} catch (URISyntaxException e) {
			LanguageServerPlugin.logError(e);
			return file.getAbsoluteFile().toURI();
		}
	}

	@Nullable public static IFile getFile(IDocument document) {
		IPath path = toPath(document);
		return getFile(path);
	}

	@Nullable public static IFile getFile(IPath path) {
		if(path == null) {
			return null;
		}
		IFile res = ResourcesPlugin.getWorkspace().getRoot().getFile(path);
		if (res != null && res.exists()) {
			return res;
		} else {
			return null;
		}
	}

	/**
	 * @return a list of folder objects for all open projects of the current workspace
	 */
	@NonNull
	public static List<@NonNull WorkspaceFolder> getWorkspaceFolders() {
		return Arrays.stream(ResourcesPlugin.getWorkspace().getRoot().getProjects())
		.filter(IProject::isAccessible) //
		.map(LSPEclipseUtils::toWorkspaceFolder) //
		.toList();
	}

	@NonNull
	public static WorkspaceFolder toWorkspaceFolder(@NonNull IProject project) {
		final var folder = new WorkspaceFolder();
		URI folderUri = toUri(project);
		folder.setUri(folderUri != null ? folderUri.toString() : ""); //$NON-NLS-1$
		folder.setName(project.getName());
		return folder;
	}

	@NonNull
	public static List<IContentType> getFileContentTypes(@NonNull IFile file) {
		IContentTypeManager contentTypeManager = Platform.getContentTypeManager();
		final var contentTypes = new ArrayList<IContentType>();
		if (file.exists()) {
			try (InputStream contents = file.getContents()) {
				// TODO consider using document as inputstream
				contentTypes.addAll(
						Arrays.asList(contentTypeManager.findContentTypesFor(contents, file.getName())));
			} catch (CoreException | IOException e) {
				LanguageServerPlugin.logError(e);
			}
		} else {
			contentTypes.addAll(Arrays.asList(contentTypeManager.findContentTypesFor(file.getName())));
		}
		return contentTypes;
	}

	@Nullable
	private static String getFileName(@NonNull IDocument document) {
		IFile file = getFile(document);
		if (file != null) {
			return file.getName();
		}
		IPath path = toPath(document);
		if(path != null) {
			return path.lastSegment();
		}
        return null;
	}

	@NonNull
	public static List<IContentType> getDocumentContentTypes(@NonNull IDocument document) {
		final var contentTypes = new ArrayList<IContentType>();

		ITextFileBufferManager bufferManager = FileBuffers.getTextFileBufferManager();
		if (bufferManager != null) {
			ITextFileBuffer buffer = bufferManager.getTextFileBuffer(document);
			if (buffer != null) {
				try {
					// may be a more specific content-type, relying on some content-type factory and actual content (not just name)
					IContentType contentType = buffer.getContentType();
					if (contentType != null) {
						contentTypes.add(contentType);
					}
				} catch (CoreException e) {
					if (!(e.getCause() instanceof java.io.FileNotFoundException)) {
						//the content type may be based on path or file name pattern or another subsystem via the ContentTypeManager
						// so that is not an error condition
						//otherwise, account for some other unknown CoreException
						LanguageServerPlugin.logError("Exception occurred while fetching the content type from the buffer", e); //$NON-NLS-1$;
					}
				}
			}
		}

		String fileName = getFileName(document);
		if (fileName != null) {
			try (var contents = new DocumentInputStream(document)) {
				contentTypes
						.addAll(Arrays.asList(Platform.getContentTypeManager().findContentTypesFor(contents, fileName)));
			} catch (IOException e) {
				LanguageServerPlugin.logError(e);
			}
		}
		return contentTypes;
	}

	/**
	 * Deprecated because any code that calls this probably needs to be changed
	 * somehow to be properly aware of markdown content. This method simply returns
	 * the doc string as a string, regardless of whether it is markdown or
	 * plaintext.
	 *
	 * @deprecated
	 */
	@Deprecated
	public static String getDocString(Either<String, MarkupContent> documentation) {
		if (documentation != null) {
			if (documentation.isLeft()) {
				return documentation.getLeft();
			} else {
				return documentation.getRight().getValue();
			}
		}
		return null;
	}

	public static String getHtmlDocString(Either<String, MarkupContent> documentation) {
		if (documentation.isLeft()) {
			return htmlParagraph(documentation.getLeft());
		} else if (documentation.isRight()) {
			MarkupContent markupContent = documentation.getRight();
			if (markupContent.getValue() != null) {
				if (MARKDOWN.equalsIgnoreCase(markupContent.getKind())
						|| MD.equalsIgnoreCase(markupContent.getKind())) {
					try {
						return MARKDOWN_PARSER.parseToHtml(markupContent.getValue());
					} catch (Exception e) {
						LanguageServerPlugin.logError(e);
						return htmlParagraph(markupContent.getValue());
					}
				} else if (HTML.equalsIgnoreCase(markupContent.getKind())) {
					return markupContent.getValue();
				} else {
					return htmlParagraph(markupContent.getValue());
				}
			}
		}
		return null;
	}

	public static ITextViewer getTextViewer(@Nullable final IEditorPart editorPart) {
		final @Nullable ITextViewer textViewer = Adapters.adapt(editorPart, ITextViewer.class);
		if (textViewer != null) {
			return textViewer;
		}

		if (Adapters.adapt(editorPart, ITextOperationTarget.class) instanceof ITextViewer viewer) {
			return viewer;
		}
		return null;
	}

	private static String htmlParagraph(String text) {
		final var sb = new StringBuilder();
		sb.append("<p>"); //$NON-NLS-1$
		sb.append(text);
		sb.append("</p>"); //$NON-NLS-1$
		return sb.toString();
	}

	/**
	 * Convert the given Eclipse <code>rgb</code> instance to a LSP {@link Color}
	 * instance.
	 *
	 * @param rgb
	 *            the rgb instance to convert
	 * @return the given Eclipse <code>rgb</code> instance to a LSP {@link Color}
	 *         instance.
	 */
	public static Color toColor(RGB rgb) {
		return new Color(rgb.red / 255d, rgb.green / 255d, rgb.blue / 255d, 1);
	}

	/**
	 * Convert the given LSP <code>color</code> instance to a Eclipse {@link RGBA}
	 * instance.
	 *
	 * @param color
	 *            the color instance to convert
	 * @return the given LSP <code>color</code> instance to a Eclipse {@link RGBA}
	 *         instance.
	 */
	public static RGBA toRGBA(Color color) {
		return new RGBA((int) (color.getRed() * 255), (int) (color.getGreen() * 255), (int) (color.getBlue() * 255),
				(int) color.getAlpha());
	}

	public static Set<IEditorReference> findOpenEditorsFor(URI uri) {
		if (uri == null) {
			return Collections.emptySet();
		}
		return Arrays.stream(PlatformUI.getWorkbench().getWorkbenchWindows())
			.map(IWorkbenchWindow::getPages)
			.flatMap(Arrays::stream)
			.map(IWorkbenchPage::getEditorReferences)
			.flatMap(Arrays::stream)
			.filter(ref -> {
				try {
					return uri.equals(toUri(ref.getEditorInput()));
				} catch (PartInitException e) {
					LanguageServerPlugin.logError(e);
					return false;
				}
			})
			.collect(Collectors.toSet());
	}

	private static URI toUri(IEditorInput editorInput) {
		if (editorInput instanceof FileEditorInput fileEditorInput) {
			return toUri(fileEditorInput.getFile());
		}
		if (editorInput instanceof IURIEditorInput uriEditorInput) {
			return toUri(Path.fromPortableString((uriEditorInput.getURI()).getPath()));
		}
		return null;
	}

	public static URI toUri(String uri) {
		return toUri(Path.fromPortableString(URI.create(uri).getPath()));
	}

	/**
	 * Use nio Paths to convert a file URI to a File in order to avoid problems with UNC paths on Windows.
	 * Java has historically generated 'unhealthy' UNC URIs so \\myserver\a\b becomes file:////myserver/a/b
	 * The favoured representation is to encode the server as the URI authority as file://myserver/a/b
	 * Java (and LSP4e) has kept the older representation using File.toURI() and new File(URI uri) for
	 * backward compatibility, but supported the newer representation in nio.Path
	 * Trying to construct a File directly with a new-style UNC URI causes it to throw with an 'authority is not null'
	 * complaint. Going via nio.Path allows us to accept either syntax. LSP4e does not use URIs directly e.g. as
	 * keys in lookup dictionaries so we don't have to worry about canonicalisation problems
	 *
	 * See https://bugs.openjdk.org/browse/JDK-4723726
	 * https://docs.microsoft.com/en-us/archive/blogs/ie/file-uris-in-windows
	 *
	 * @param uri A file URI, possibly for a UNC path in the newer syntax with the server encoded in the authority
	 * @return A file
	 */
	public static File fromUri(URI uri) {
		return Paths.get(uri).toFile();
	}

	public static boolean hasCapability(Either<Boolean, ? extends Object> eitherCapability) {
		if(eitherCapability != null) {
			if (eitherCapability.isLeft()) {
				return eitherCapability.getLeft();
			} else {
				return eitherCapability.getRight() != null;
			}
		} else {
			return false;
		}
	}

	/**
	 * Execute Eclipse Search UI to search LSP references from the given document and
	 * offset.
	 *
	 * @param document
	 *            the document.
	 * @param offset
	 *            the offset.
	 * @param display
	 *            the display to use to execute the search.
	 */
	public static void searchLSPReferences(@NonNull IDocument document, int offset, Display display) {
		LanguageServiceAccessor
				.getLanguageServers(document,
						capabilities -> hasCapability(capabilities.getReferencesProvider())) //
				.thenAcceptAsync(languageServers -> {
					if (languageServers.isEmpty()) {
						return;
					}
					LanguageServer ls = languageServers.get(0);
					try {
						LSSearchQuery query = new LSSearchQuery(document, offset, ls);
						display.asyncExec(() -> NewSearchUI.runQueryInBackground(query));
					} catch (BadLocationException e) {
						LanguageServerPlugin.logError(e);
					}
				});
	}
}
