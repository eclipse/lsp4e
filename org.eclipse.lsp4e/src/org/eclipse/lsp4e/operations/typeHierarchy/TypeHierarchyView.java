/*******************************************************************************
 * Copyright (c) 2023 Bachmann electronic GmbH and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * Gesa Hentschke (Bachmann electronic GmbH) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.typeHierarchy;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.IFileBuffer;
import org.eclipse.core.filebuffers.IFileBufferListener;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IOpenListener;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.OpenEvent;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.internal.FileBufferListenerAdapter;
import org.eclipse.lsp4e.outline.SymbolsLabelProvider;
import org.eclipse.lsp4e.outline.SymbolsModel;
import org.eclipse.lsp4e.outline.SymbolsModel.DocumentSymbolWithURI;
import org.eclipse.lsp4e.ui.LSPImages;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4e.ui.views.HierarchyViewInput;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.ViewForm;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.FilteredTree;
import org.eclipse.ui.dialogs.PatternFilter;
import org.eclipse.ui.part.PageBook;
import org.eclipse.ui.part.ViewPart;

public class TypeHierarchyView extends ViewPart {

	class SymbolsContainer {
		public final IFile file;
		public final SymbolsModel symbolsModel;
		public volatile boolean isDirty = true;
		private boolean temporaryLoadedDocument = false;

		SymbolsContainer(IFile file) {
			this.file = file;
			this.symbolsModel = new SymbolsModel();
			this.symbolsModel.setUri(file.getLocationURI());
		}

		public IDocument getDocument() {
			var document = LSPEclipseUtils.getExistingDocument(file);
			if (document == null) {
				document = LSPEclipseUtils.getDocument(file);
				temporaryLoadedDocument = document != null;
			}
			return document;
		}

		public void dispose() {
			if (temporaryLoadedDocument) {
				try {
					FileBuffers.getTextFileBufferManager().disconnect(file.getFullPath(), LocationKind.IFILE,
							new NullProgressMonitor());
				} catch (CoreException e) {
					LanguageServerPlugin.logError(e);
				}
			}
		}
	}

	public static final String ID = "org.eclipse.lsp4e.operations.typeHierarchy.TypeHierarchyView"; //$NON-NLS-1$
	TypeHierarchyViewContentProvider contentProvider = new TypeHierarchyViewContentProvider();
	SymbolsLabelProvider symbolsLabelProvider = new SymbolsLabelProvider();
	// widgets
	private PageBook pagebook;
	private SashForm splitter;
	private ViewForm memberViewForm;
	private CLabel memberLabel;
	private Label infoText;

	// viewers
	private TableViewer memberViewer;
	protected TreeViewer treeViewer;

	private volatile CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> symbols;
	private HashMap<URI, SymbolsContainer> cachedSymbols = new HashMap<>();
	private IDocument document;
	private volatile String typeName;
	private String uri;

	private final IFileBufferListener fileBufferListener = new FileBufferListenerAdapter() {
		@Override
		public void dirtyStateChanged(IFileBuffer buffer, boolean isDirty) {
			if (isDirty && buffer != null) {
				var uri = LSPEclipseUtils.toUri(buffer);
				if (uri != null) {
					var cachedSymbol = cachedSymbols.get(uri);
					if (cachedSymbol != null) {
						cachedSymbol.isDirty = true;
					}
				}
			}
		}

	};

	private IDocumentListener documentListener = new IDocumentListener() {

		@Override
		public void documentChanged(DocumentEvent event) {
			var document = event.getDocument();
			if (document != null) {
				refreshMemberViewer(LSPEclipseUtils.getFile(document), true);
			}
		}

		@Override
		public void documentAboutToBeChanged(DocumentEvent event) {
			// Do nothing
		}
	};

	@Override
	public void setFocus() {
		pagebook.setFocus();
	}

	@Override
	public void createPartControl(Composite parent) {
		pagebook = new PageBook(parent, SWT.NULL);
		splitter = new SashForm(pagebook, SWT.VERTICAL);
		splitter.setLayoutData(new GridData(GridData.FILL_BOTH));
		splitter.addControlListener(new ControlListener() {
			@Override
			public void controlMoved(ControlEvent e) {
			}

			@Override
			public void controlResized(ControlEvent e) {
				splitter.setOrientation(getBestOrientation());
				splitter.layout();
			}
		});

		treeViewer = getFilteredTree(splitter).getViewer();
		treeViewer.setContentProvider(contentProvider);

		treeViewer.setLabelProvider(new TypeHierarchyItemLabelProvider());

		treeViewer.setUseHashlookup(true);
		treeViewer.getControl().setEnabled(false);
		treeViewer.addDoubleClickListener(new IDoubleClickListener() {
			@Override
			public void doubleClick(final DoubleClickEvent event) {
				var selection = ((IStructuredSelection) event.getSelection()).getFirstElement();
				if (selection instanceof TypeHierarchyItem item) {
					LSPEclipseUtils.open(item.getUri(), item.getSelectionRange());
				}
			}
		});
		treeViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(final SelectionChangedEvent event) {
				onHierarchySelectionChanged(event);
			}
		});

		memberViewForm = new ViewForm(splitter, SWT.NONE);
		Control memberControl = createMemberControl(memberViewForm);
		memberControl.setEnabled(false);
		memberViewForm.setContent(memberControl);

		memberLabel = new CLabel(memberViewForm, SWT.NONE);
		memberViewForm.setTopLeft(memberLabel);

		infoText = new Label(pagebook, SWT.TOP | SWT.LEFT | SWT.WRAP);
		infoText.setText(Messages.TH_diplay_hint);
		pagebook.showPage(infoText);

		FileBuffers.getTextFileBufferManager().addFileBufferListener(fileBufferListener);
	}

	@Override
	public void dispose() {
		FileBuffers.getTextFileBufferManager().removeFileBufferListener(fileBufferListener);
		cachedSymbols.forEach((uri, container) -> {container.dispose();});
		super.dispose();
	}

	public void initialize(final IDocument document, final int offset) {
		treeViewer.setInput(new HierarchyViewInput(document, offset));
		pagebook.showPage(splitter);
	}

	private void onHierarchySelectionChanged(SelectionChangedEvent event) {
		var selection = event.getSelection();
		if (selection instanceof TreeSelection && !selection.isEmpty()) {
			var element = ((TreeSelection) selection).getFirstElement();
			if (element instanceof TypeHierarchyItem item) {
				this.uri = item.getUri();
				this.typeName = item.getName();
				refreshMemberViewer(LSPEclipseUtils.getFileHandle(uri), false);
			}
		}
	}

	private synchronized void refreshMemberViewer(IFile file, boolean documentModified) {
		PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
			if (file != null) {
				refreshSymbols(getSymbolsContainer(file), documentModified);
				var symbol = getDocumentSymbol(typeName, file);
				memberViewer.setInput(symbol);
				memberLabel.setText(typeName);
				if (symbol != null ) {
					memberViewer.getControl().setEnabled(true);
					memberViewer.setSelection(new StructuredSelection(symbol));
					memberLabel.setImage(symbolsLabelProvider.getImage(symbol));
				}
			}
		});
	}

	private Control createMemberControl(ViewForm parent) {
		memberViewer = new TableViewer(parent, SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
		memberViewer.setContentProvider(new TypeMemberContentProvider());
		memberViewer.setLabelProvider(symbolsLabelProvider);
		memberViewer.addOpenListener(new IOpenListener() {
			@Override
			public void open(OpenEvent event) {
				DocumentSymbol symbol = (DocumentSymbol)((IStructuredSelection) event.getSelection()).getFirstElement();
				LSPEclipseUtils.open(uri, symbol.getRange());
			}
		});
		return memberViewer.getControl();
	}

	private int getBestOrientation() {
		Point size = splitter.getSize();
		if (size.x != 0 && size.y != 0 && 3 * size.x < 2 * size.y) {
			return SWT.VERTICAL;
		}
		return SWT.HORIZONTAL;
	}

	private FilteredTree getFilteredTree(Composite parent) {
		return new FilteredTree(parent, SWT.BORDER, new PatternFilter(), true, false) {
			@Override
			protected Composite createFilterControls(Composite parent) {
				Composite composite = new Composite(parent, SWT.NONE);
				GridLayout layout = new GridLayout(2, false);
				layout.horizontalSpacing=0;
				layout.marginWidth=0;
				layout.marginHeight=0;
				composite.setLayout(layout);

				Composite filterControls = super.createFilterControls(composite);
				filterControls.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));

				createToolBar(composite);

				return composite;
			}

			private void createToolBar(Composite composite) {
				ToolBar toolbar = new ToolBar(composite, org.eclipse.jface.dialogs.PopupDialog.HOVER_SHELLSTYLE);
				ToolItem hierchyModeItem = new ToolItem(toolbar, SWT.PUSH);
				updateHierarchyModeItem(hierchyModeItem, contentProvider.showSuperTypes);

				hierchyModeItem.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						contentProvider.showSuperTypes = !contentProvider.showSuperTypes;
						updateHierarchyModeItem(hierchyModeItem, contentProvider.showSuperTypes);
						getViewer().refresh();
					}
				});
			}

			private void updateHierarchyModeItem(ToolItem hierchyModeItem, boolean showSuperTypes) {
				hierchyModeItem.setImage(LSPImages.getImage(showSuperTypes ? LSPImages.IMG_SUBTYPE : LSPImages.IMG_SUPERTYPE));
				hierchyModeItem.setToolTipText(showSuperTypes ? Messages.typeHierarchy_show_subtypes : Messages.typeHierarchy_show_supertypes);
			}
		};
	}

	private SymbolsContainer getSymbolsContainer(IFile file) {
		SymbolsContainer symbolsContainer = cachedSymbols.get(file.getLocationURI());
		if (symbolsContainer == null) {
			symbolsContainer = new SymbolsContainer(file);
			cachedSymbols.put(file.getLocationURI(), symbolsContainer);
		}
		return symbolsContainer;
	}

	private void refreshSymbols(SymbolsContainer symbolsContainer, boolean documentModified) {
		if (symbolsContainer == null || (!symbolsContainer.isDirty && !documentModified)) {
			return;
		}
		final IDocument document = symbolsContainer.getDocument();
		try {
			if (document != null) {
				final var params = new DocumentSymbolParams(
						LSPEclipseUtils.toTextDocumentIdentifier(document));
				CompletableFuture<Optional<LanguageServerWrapper>> languageServer = LanguageServers
						.forDocument(document)
						.withFilter(
								capabilities -> LSPEclipseUtils.hasCapability(capabilities.getDocumentSymbolProvider()))
						.computeFirst((w, ls) -> CompletableFuture.completedFuture(w));
				try {
					symbols = languageServer.get(500, TimeUnit.MILLISECONDS).filter(Objects::nonNull)
							.filter(LanguageServerWrapper::isActive)
							.map(s -> s.execute(ls -> ls.getTextDocumentService().documentSymbol(params)))
							.orElse(CompletableFuture.completedFuture(null));
				} catch (TimeoutException | ExecutionException | InterruptedException e) {
					LanguageServerPlugin.logError(e);
					symbols = CompletableFuture.completedFuture(null);
					if (e instanceof InterruptedException) {
						Thread.currentThread().interrupt();
					}
				}
				symbols.thenAcceptAsync(response -> {
					symbolsContainer.symbolsModel.update(response);
					symbolsContainer.isDirty = false;
					//add listener:
					if (!document.equals(this.document)) {
						if (this.document != null) {
							this.document.removeDocumentListener(documentListener);
						}
						this.document = document;
						this.document.addDocumentListener(documentListener);
					}
				}).join();
			} else {
				symbolsContainer.symbolsModel.update(null);
			}
		} catch (Exception e) {
			LanguageServerPlugin.logError(e);
		}
	}

	private DocumentSymbol getDocumentSymbol(String typeName, IFile file) {
		var symbolsContainer = cachedSymbols.get(file.getLocationURI());
		if (symbolsContainer != null) {
			var elements = symbolsContainer.symbolsModel.getElements();
			for (var element : elements) {
				if (element instanceof DocumentSymbolWithURI symbolContainer) {
					if (isClass(symbolContainer.symbol.getKind()) && symbolContainer.symbol.getName().equals(typeName)) {
						return symbolContainer.symbol;
					}
					var grandchild = searchInChildren(symbolContainer.symbol.getChildren(), typeName);
					if (grandchild != null) {
						return grandchild;
					}
				}
			}
		}
		return null;
	}

	private boolean isClass(SymbolKind kind) {
		return SymbolKind.Class.equals(kind) || SymbolKind.Struct.equals(kind)  ;
	}

	private DocumentSymbol searchInChildren(List<DocumentSymbol> children, String typeName) {
		if (children == null) {
			return null;
		}
		for (var child : children) {
			if (child.getName().equals(typeName) && isClass(child.getKind())) {
				return child;
			}
			var grandchild = searchInChildren(child.getChildren(), typeName);
			if (grandchild != null) {
				return grandchild;
			}
		}
		return null;
	}
}
