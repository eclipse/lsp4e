/*******************************************************************************
 * Copyright (c) 2016 Rogue Wave Software Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.symbols;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.text.contentassist.BoldStylerProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServiceAccessor.LSPServerInfo;
import org.eclipse.lsp4e.outline.SymbolsLabelProvider;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.FilteredItemsSelectionDialog;

public class LSPSymbolInWorkspaceDialog extends FilteredItemsSelectionDialog {

	private static final String DIALOG_SETTINGS = LSPSymbolInWorkspaceDialog.class.getName();

	private static class InternalSymbolsLabelProvider extends SymbolsLabelProvider {

		private String pattern;
		private BoldStylerProvider stylerProvider;

		public InternalSymbolsLabelProvider(BoldStylerProvider stylerProvider) {
			super(true);
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

		public void setPattern(String pattern) {
			this.pattern = pattern;
		}
	}

	private class InternalItemsFilter extends ItemsFilter {

		@Override
		public boolean matchItem(Object item) {
			if (!(item instanceof SymbolInformation)) {
				return false;
			}
			SymbolInformation info = (SymbolInformation) item;
			return info.getName().toLowerCase().indexOf(getPattern().toLowerCase()) != -1;
		}

		@Override
		public boolean isConsistentItem(Object item) {
			return true;
		}
	}

	private List<LSPServerInfo> infos;
	private InternalSymbolsLabelProvider labelProvider;

	public LSPSymbolInWorkspaceDialog(Shell shell, List<LSPServerInfo> infos) {
		super(shell);
		this.infos = infos;
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

		for (LSPServerInfo info : infos) {
			if (monitor.isCanceled()) {
				return;
			}

			WorkspaceSymbolParams params = new WorkspaceSymbolParams(itemsFilter.getPattern());
			CompletableFuture<List<? extends SymbolInformation>> symbols = info.getLanguageServer()
			        .getWorkspaceService().symbol(params);

			try {
				List<?> items = symbols.get(1, TimeUnit.SECONDS);
				for (Object item : items) {
					if (item != null) {
						contentProvider.add(item, itemsFilter);
					}
				}
			} catch (InterruptedException | ExecutionException | TimeoutException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	@Override
	public String getElementName(Object item) {
		SymbolInformation info = (SymbolInformation) item;
		return info.getName();
	}

	@Override
	protected Comparator<SymbolInformation> getItemsComparator() {
		return new Comparator<SymbolInformation>() {

			@Override
			public int compare(SymbolInformation o1, SymbolInformation o2) {
				return o1.getName().compareToIgnoreCase(o2.getName());
			}
		};
	}

	@Override
	protected IStatus validateItem(Object item) {
		return Status.OK_STATUS;
	}

	@Override
	protected IDialogSettings getDialogSettings() {
		IDialogSettings settings = LanguageServerPlugin.getDefault().getDialogSettings()
		        .getSection(DIALOG_SETTINGS);
		if (settings == null) {
			settings = LanguageServerPlugin.getDefault().getDialogSettings().addNewSection(DIALOG_SETTINGS);
		}
		return settings;
	}

	@Override
	protected Control createExtendedContentArea(Composite parent) {
		return null;
	}

}
