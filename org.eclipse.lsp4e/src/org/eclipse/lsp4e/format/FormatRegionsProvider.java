/*******************************************************************************
 * Copyright (c) 2023 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   See git history
 *******************************************************************************/
package org.eclipse.lsp4e.format;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.cdt.internal.ui.text.LineComparator;
import org.eclipse.compare.rangedifferencer.IRangeComparator;
import org.eclipse.compare.rangedifferencer.RangeDifference;
import org.eclipse.compare.rangedifferencer.RangeDifferencer;
import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component
public class FormatRegionsProvider implements IFormatRegionsProvider {

	@Reference
	private IFormatOnSave formatOnSave;

	/**
	 * Get the formatting regions depending on the {@link FormatStrategy}
	 * @param document
	 * @param strategy
	 * @return region to be formatted or null
	 * @throws CoreException
	 */
	@Override
	public IRegion[] getFormattingRegions(IDocument document) throws CoreException {
		switch (formatOnSave.getFormatStrategy(document)) {
			case NO_FORMAT:
				return null;
			case ALL_LINES:
				return new IRegion[] { new Region(0, document.getLength()) };
			case EDITED_LINES:
				return calculateChangedLineRegions(LSPEclipseUtils.toBuffer(document), new NullProgressMonitor());
			default:
				break;
		}
		return null;
	}

	/**
	 * Return the regions of all lines which have changed in the given buffer since the
	 * last save occurred. Each region in the result spans over the size of at least one line.
	 * If successive lines have changed a region spans over the size of all successive lines.
	 * The regions include line delimiters.
	 *
	 * @param buffer the buffer to compare contents from
	 * @param monitor to report progress to
	 * @return the regions of the changed lines
	 * @throws CoreException
	 *
	 * Copied from org.eclipse.cdt.internal.ui.util.EditorUtility.calculateChangedLineRegions(ITextFileBuffer, IProgressMonitor)
	 */
	private IRegion[] calculateChangedLineRegions(final ITextFileBuffer buffer, final IProgressMonitor monitor)
			throws CoreException {
		final IRegion[][] result = new IRegion[1][];
		final IStatus[] errorStatus = new IStatus[] { Status.OK_STATUS };

		try {
			SafeRunner.run(new ISafeRunnable() {
				@Override
				public void handleException(Throwable exception) {
					LanguageServerPlugin.logError(exception.getLocalizedMessage(), exception);
					String msg = "An error occurred while calculating the changed regions. See error log for details"; //$NON-NLS-1$
					errorStatus[0] = new Status(IStatus.ERROR, LanguageServerPlugin.PLUGIN_ID, msg, exception);
					result[0] = null;
				}

				@Override
				public void run() throws Exception {
					SubMonitor progress = SubMonitor.convert(monitor, "Calculating changed regions", 4); //$NON-NLS-1$
					IFileStore fileStore = buffer.getFileStore();

					ITextFileBufferManager fileBufferManager = FileBuffers.createTextFileBufferManager();
					fileBufferManager.connectFileStore(fileStore, progress.split(3));
					try {
						IDocument currentDocument = buffer.getDocument();
						IDocument oldDocument = ((ITextFileBuffer) fileBufferManager.getFileStoreFileBuffer(fileStore))
								.getDocument();

						result[0] = getChangedLineRegions(oldDocument, currentDocument);
					} finally {
						fileBufferManager.disconnectFileStore(fileStore, progress.split(1));
					}
				}

				/**
				 * Return regions of all lines which differ comparing {@code oldDocument}s content
				 * with {@code currentDocument}s content. Successive lines are merged into one region.
				 *
				 * @param oldDocument a document containing the old content
				 * @param currentDocument a document containing the current content
				 * @return the changed regions
				 * @throws BadLocationException
				 */
				private IRegion[] getChangedLineRegions(IDocument oldDocument, IDocument currentDocument) {
					/*
					 * Do not change the type of those local variables. We use Object
					 * here in order to prevent loading of the Compare plug-in at load
					 * time of this class.
					 */
					Object leftSide = new LineComparator(oldDocument);
					Object rightSide = new LineComparator(currentDocument);

					RangeDifference[] differences = RangeDifferencer.findDifferences((IRangeComparator) leftSide,
							(IRangeComparator) rightSide);

					// It holds that:
					// 1. Ranges are sorted:
					//     forAll r1,r2 element differences: indexOf(r1) < indexOf(r2) -> r1.rightStart() < r2.rightStart();
					// 2. Successive changed lines are merged into on RangeDifference
					//     forAll r1,r2 element differences: r1.rightStart() < r2.rightStart() -> r1.rightEnd() < r2.rightStart

					List<IRegion> regions = new ArrayList<>();
					final int numberOfLines = currentDocument.getNumberOfLines();
					for (RangeDifference curr : differences) {
						if (curr.kind() == RangeDifference.CHANGE) {
							int startLine = Math.min(curr.rightStart(), numberOfLines - 1);
							int endLine = curr.rightEnd() - 1;

							IRegion startLineRegion;
							try {
								startLineRegion = currentDocument.getLineInformation(startLine);
								if (startLine >= endLine) {
									// startLine > endLine indicates a deletion of one or more lines.
									// Deletions are ignored except at the end of the document.
									if (startLine == endLine || startLineRegion.getOffset()
											+ startLineRegion.getLength() == currentDocument.getLength()) {
										regions.add(startLineRegion);
									}
								} else {
									IRegion endLineRegion = currentDocument.getLineInformation(endLine);
									int startOffset = startLineRegion.getOffset();
									int endOffset = endLineRegion.getOffset() + endLineRegion.getLength();
									regions.add(new Region(startOffset, endOffset - startOffset));
								}
							} catch (BadLocationException e) {
								LanguageServerPlugin.logError(e);
							}
						}
					}

					return regions.toArray(new IRegion[regions.size()]);
				}
			});
		} finally {
			if (!errorStatus[0].isOK())
				throw new CoreException(errorStatus[0]);
		}

		return result[0];
	}

}
