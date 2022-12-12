/*******************************************************************************
 * Copyright (c) 2016, 2019 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.symbols;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.BoldStylerProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.lsp4e.LSPExecutor.LSPProjectExecutor;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.outline.CNFOutlinePage;
import org.eclipse.lsp4e.outline.SymbolsLabelProvider;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolTag;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.FilteredItemsSelectionDialog;

public class LSPSymbolInWorkspaceDialog extends FilteredItemsSelectionDialog {

	private static final String DIALOG_SETTINGS = LSPSymbolInWorkspaceDialog.class.getName();

	private static class InternalSymbolsLabelProvider extends SymbolsLabelProvider {

		private String pattern;
		private final BoldStylerProvider stylerProvider;

		public InternalSymbolsLabelProvider(BoldStylerProvider stylerProvider) {
			super(true, InstanceScope.INSTANCE.getNode(LanguageServerPlugin.PLUGIN_ID)
					.getBoolean(CNFOutlinePage.SHOW_KIND_PREFERENCE, false));
			this.stylerProvider = stylerProvider;
		}

		@Override
		public StyledString getStyledText(Object element) {
			StyledString styledString = super.getStyledText(element);
			int index = styledString.getString().toLowerCase().indexOf(pattern);
			if (index != -1) {
				styledString.setStyle(index, pattern.length(), stylerProvider.getBoldStyler());
			}
			return styledString;
		}

		@Override
		protected int getMaxSeverity(@NonNull IResource resource, @NonNull IDocument doc, @NonNull Range range) throws CoreException, BadLocationException {
			int maxSeverity = -1;
			for (IMarker marker : resource.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_ZERO)) {
				int offset = marker.getAttribute(IMarker.CHAR_START, -1);
				if (offset != -1) {
					maxSeverity = Math.max(maxSeverity, marker.getAttribute(IMarker.SEVERITY, -1));
				}
			}
			return maxSeverity;
		}

		public void setPattern(String pattern) {
			this.pattern = pattern;
		}
	}

	private class InternalItemsFilter extends ItemsFilter {

		@Override
		public boolean matchItem(Object item) {
			return item instanceof WorkspaceSymbol info && //
					info.getName().toLowerCase().indexOf(getPattern().toLowerCase()) != -1;
		}

		@Override
		public boolean isConsistentItem(Object item) {
			return true;
		}
	}

	private final LSPProjectExecutor executor;
	private final InternalSymbolsLabelProvider labelProvider;

	public LSPSymbolInWorkspaceDialog(Shell shell, LSPProjectExecutor executor) {
		super(shell);
		this.executor = executor;
		this.labelProvider = new InternalSymbolsLabelProvider(new BoldStylerProvider(shell.getFont()));
		setMessage(Messages.LSPSymbolInWorkspaceDialog_DialogLabel);
		setTitle(Messages.LSPSymbolInWorkspaceDialog_DialogTitle);
		setListLabelProvider(labelProvider);
	}

	@Override
	protected ItemsFilter createFilter() {
		InternalItemsFilter itemsFilter = new InternalItemsFilter();
		labelProvider.setPattern(itemsFilter.getPattern());
		return itemsFilter;
	}

	@Override
	protected void fillContentProvider(AbstractContentProvider contentProvider, ItemsFilter itemsFilter,
			IProgressMonitor monitor) throws CoreException {
		if (itemsFilter.getPattern().isEmpty()) {
			return;
		}
		WorkspaceSymbolParams params = new WorkspaceSymbolParams(itemsFilter.getPattern());

		this.executor.computeAll((w, ls) -> ls.getWorkspaceService()
				.symbol(params)).stream().map(s -> s.thenApply(LSPSymbolInWorkspaceDialog::eitherToWorkspaceSymbols))
			.forEach(cf -> {
				if (monitor.isCanceled()) {
					return;
				}
				try {
					final List<? extends WorkspaceSymbol> items = cf.get(1, TimeUnit.SECONDS);
					if(items != null) {
						for (Object item : items) {
							if (item != null) {
								contentProvider.add(item, itemsFilter);
							}
						}
					}
				} catch (ExecutionException e) {
					LanguageServerPlugin.logError(e);
				} catch (InterruptedException e) {
					LanguageServerPlugin.logError(e);
					Thread.currentThread().interrupt();
				} catch (TimeoutException e) {
					LanguageServerPlugin.logWarning("Could not get workspace symbols due to timeout after 1 seconds in `workspace/symbol`", e); //$NON-NLS-1$
				}
			});


	}

	@Override
	public String getElementName(Object item) {
		return ((WorkspaceSymbol)item).getName();
	}

	@Override
	protected Comparator<WorkspaceSymbol> getItemsComparator() {
		return (o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName());
	}

	@Override
	protected IStatus validateItem(Object item) {
		return Status.OK_STATUS;
	}

	@Override
	protected IDialogSettings getDialogSettings() {
		IDialogSettings settings = LanguageServerPlugin.getDefault().getDialogSettings().getSection(DIALOG_SETTINGS);
		if (settings == null) {
			settings = LanguageServerPlugin.getDefault().getDialogSettings().addNewSection(DIALOG_SETTINGS);
		}
		return settings;
	}

	@Override
	protected Control createExtendedContentArea(Composite parent) {
		return null;
	}

	private static List<WorkspaceSymbol> toWorkspaceSymbols(List<? extends SymbolInformation> source) {
		return source == null ?
				List.of() :
				source.stream().map(LSPSymbolInWorkspaceDialog::toWorkspaceSymbol).collect(Collectors.toList());
	}

	private static WorkspaceSymbol toWorkspaceSymbol(SymbolInformation symbolinformation) {
		if (symbolinformation == null) {
			return null;
		}
		WorkspaceSymbol res = new WorkspaceSymbol();
		res.setName(symbolinformation.getName());
		res.setLocation(Either.forLeft(symbolinformation.getLocation()));
		res.setKind(symbolinformation.getKind());
		res.setContainerName(symbolinformation.getContainerName());
		res.setTags(symbolinformation.getDeprecated() ? List.of(SymbolTag.Deprecated) : List.of());
		return res;
	}

	static List<? extends WorkspaceSymbol> eitherToWorkspaceSymbols(Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>> source) {
		return source.map(LSPSymbolInWorkspaceDialog::toWorkspaceSymbols, Function.identity());
	}
}
