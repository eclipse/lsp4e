/**
 *  Copyright (c) 2017-2019 Angelo ZERR.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *  Angelo Zerr <angelo.zerr@gmail.com> - initial API and implementation
 *  Pierre-Yves B. <pyvesdev@gmail.com> - Bug 525411 - [rename] input field should be filled with symbol to rename
 */
package org.eclipse.lsp4e.operations.rename;

import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

/**
 * The refactoring wizard for renaming LSP Symbols.
 *
 */
public class LSPRenameRefactoringWizard extends RefactoringWizard {

	public LSPRenameRefactoringWizard(Refactoring refactoring) {
		super(refactoring, DIALOG_BASED_USER_INTERFACE);
		super.setWindowTitle(Messages.rename_title);
	}

	@Override
	protected void addUserInputPages() {
		@SuppressWarnings("null")
		LSPRenameProcessor processor = this.getRefactoring().getAdapter(LSPRenameProcessor.class);
		this.addPage(new RenameInputWizardPage(processor));
	}

	/**
	 * Rename input wizard page.
	 *
	 */
	class RenameInputWizardPage extends UserInputWizardPage {

		private Text nameText;

		private final LSPRenameProcessor processor;

		RenameInputWizardPage(LSPRenameProcessor processor) {
			super(RenameInputWizardPage.class.getSimpleName());
			this.processor = processor;
		}

		@Override
		public void createControl(Composite parent) {
			Composite composite = new Composite(parent, SWT.NONE);
			composite.setLayout(new GridLayout(2, false));
			composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			composite.setFont(parent.getFont());

			Label label = new Label(composite, SWT.NONE);
			label.setLayoutData(new GridData());
			label.setText(Messages.rename_label);

			this.nameText = new Text(composite, SWT.BORDER);
			this.nameText.setText(processor.getPlaceholder());
			this.nameText.setFont(composite.getFont());
			this.nameText.setLayoutData(new GridData(GridData.FILL, GridData.BEGINNING, true, false));
			this.nameText.addModifyListener(e -> validatePage());
			this.nameText.selectAll();
			this.setControl(composite);
			validatePage();
		}

		@Override
		public IWizardPage getNextPage() {
			this.setNewName();
			return super.getNextPage();
		}

		@Override
		public void setVisible(boolean visible) {
			if (visible) {
				this.nameText.setFocus();
			}
			super.setVisible(visible);
		}

		@Override
		protected boolean performFinish() {
			this.setNewName();
			return super.performFinish();
		}

		private void setNewName() {
			this.processor.setNewName(this.nameText.getText());
		}

		/**
		 * Validate page fields.
		 */
		private final void validatePage() {
			RefactoringStatus status = validateName();
			setPageComplete(status);
		}

		/**
		 * Returns the status of the validated name.
		 *
		 * @return the status of the validated name.
		 */
		private RefactoringStatus validateName() {
			// Name is required
			if (this.nameText.getText().trim().length() == 0) {
				return RefactoringStatus.createFatalErrorStatus(Messages.rename_processor_required);
			}
			return new RefactoringStatus();
		}
	}
}
