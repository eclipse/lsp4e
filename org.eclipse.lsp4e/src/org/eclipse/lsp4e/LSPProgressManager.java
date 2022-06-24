/*******************************************************************************
 * Copyright (c) 2022 Avaloq Evolution AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Rubén Porras Campo (Avaloq Evolution AG) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.runtime.ICoreRunnable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.lsp4j.ProgressParams;
import org.eclipse.lsp4j.WorkDoneProgressBegin;
import org.eclipse.lsp4j.WorkDoneProgressCancelParams;
import org.eclipse.lsp4j.WorkDoneProgressCreateParams;
import org.eclipse.lsp4j.WorkDoneProgressEnd;
import org.eclipse.lsp4j.WorkDoneProgressKind;
import org.eclipse.lsp4j.WorkDoneProgressNotification;
import org.eclipse.lsp4j.WorkDoneProgressReport;
import org.eclipse.lsp4j.services.LanguageServer;

public class LSPProgressManager {
	private final Map<String, BlockingQueue<ProgressParams>> progressMap;
	private final Map<IProgressMonitor, Integer> currentPercentageMap;
	private LanguageServer languageServer;

	public LSPProgressManager() {
		this.progressMap = new ConcurrentHashMap<>();
		this.currentPercentageMap = new ConcurrentHashMap<>();
	}

	public void connect(final LanguageServer languageServer) {
		this.languageServer = languageServer;
	}
	/**
	 * Creates the progress.
	 *
	 * @param params
	 *            the {@link WorkDoneProgressCreateParams} to be used to create the progress
	 * @return the completable future
	 */
	public @NonNull CompletableFuture<Void> createProgress(final @NonNull WorkDoneProgressCreateParams params) {
		LinkedBlockingDeque<ProgressParams> queue = new LinkedBlockingDeque<>();

		String jobIdentifier = params.getToken().getLeft();
		BlockingQueue<ProgressParams> oldQueue = progressMap.put(jobIdentifier, queue);
		if (oldQueue != null) {
			LanguageServerPlugin.logInfo(
					"Old progress with identifier " + jobIdentifier + " discarded due to new create progress request"); //$NON-NLS-1$//$NON-NLS-2$
		}

		Job job = Job.create("Language Server Background Job", (ICoreRunnable) monitor -> { //$NON-NLS-1$
			try {
				while (true) {
					if (monitor.isCanceled()) {
						progressMap.remove(jobIdentifier);
						currentPercentageMap.remove(monitor);
						if (languageServer != null) {
							WorkDoneProgressCancelParams workDoneProgressCancelParams = new WorkDoneProgressCancelParams();
							workDoneProgressCancelParams.setToken(jobIdentifier);
							languageServer.cancelProgress(workDoneProgressCancelParams);
						}
						throw new OperationCanceledException();
					}
					ProgressParams nextProgressNotification = queue.pollFirst(1, TimeUnit.SECONDS);
					if (nextProgressNotification != null ) {
						WorkDoneProgressNotification progressNotification = nextProgressNotification.getValue().getLeft();
						if (progressNotification != null) {
							WorkDoneProgressKind kind = progressNotification.getKind();
							if (kind == WorkDoneProgressKind.begin) {
								begin((WorkDoneProgressBegin) progressNotification, monitor);
							} else if (kind == WorkDoneProgressKind.report) {
								report((WorkDoneProgressReport) progressNotification, monitor);
							} else if (kind == WorkDoneProgressKind.end) {
								end((WorkDoneProgressEnd) progressNotification, monitor);
								progressMap.remove(jobIdentifier);
								currentPercentageMap.remove(monitor);
								return;
							}
						}
					}
				}
			} catch (InterruptedException e) {
				LanguageServerPlugin.logError(e);
				Thread.currentThread().interrupt();
			}
		});
		job.schedule();
		return CompletableFuture.completedFuture(null);
	}

	private void begin(final WorkDoneProgressBegin begin, final IProgressMonitor monitor) {
		Integer percentage = begin.getPercentage();
		if (percentage != null) {
			monitor.beginTask(begin.getTitle(), percentage);
			currentPercentageMap.put(monitor, percentage);
		} else {
			monitor.beginTask(begin.getTitle(), IProgressMonitor.UNKNOWN);
		}

		String message = begin.getMessage();
		if (message != null && !message.isBlank()) {
			monitor.subTask(message);
		}
	}

	private void end(final WorkDoneProgressEnd end, final IProgressMonitor monitor) {
		monitor.subTask(end.getMessage());
		monitor.done();
	}

	private void report(final WorkDoneProgressReport report, final IProgressMonitor monitor) {
		if (report.getPercentage() == null) {
			return;
		}

		if (report.getMessage() != null && !report.getMessage().isBlank()) {
			monitor.subTask(report.getMessage());
		}

		if (currentPercentageMap.containsKey(monitor)) {
			Integer percentage = currentPercentageMap.get(monitor);
			int worked = percentage != null ? Math.min(percentage, report.getPercentage()) : 0;
			monitor.worked(report.getPercentage().intValue() - worked);
		}

		currentPercentageMap.put(monitor, report.getPercentage());
	}

	/**
	 * Notify progress.
	 *
	 * @param params
	 *            the {@link ProgressParams} used for the progress notification
	 */
	public void notifyProgress(final @NonNull ProgressParams params) {
		String jobName = params.getToken().getLeft();
		BlockingQueue<ProgressParams> progress = progressMap.get(jobName);
		if (progress != null) { // may happen if the server does not wait on the return value of the future of createProgress
			progress.add(params);
		}
	}

}
