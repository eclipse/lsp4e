/*******************************************************************************
 * Copyright (c) 2022-24 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.operations.inlayhint;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.codemining.LineContentCodeMining;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServers.LanguageServerDocumentExecutor;
import org.eclipse.lsp4e.command.CommandExecutor;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintLabelPart;
import org.eclipse.lsp4j.InlayHintRegistrationOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;

public class LSPLineContentCodeMining extends LineContentCodeMining {

	private InlayHint inlayHint;
	private final LanguageServerWrapper wrapper;
	private IDocument document;

	private Point location;
	private FontData[] fontData;

	public LSPLineContentCodeMining(InlayHint inlayHint, IDocument document, LanguageServerWrapper languageServerWrapper,
			InlayHintProvider provider) throws BadLocationException {
		super(toPosition(inlayHint.getPosition(), document), provider);
		this.inlayHint = inlayHint;
		this.wrapper = languageServerWrapper;
		this.document = document;
		setLabel(getInlayHintString(inlayHint));
	}

	protected static @Nullable String getInlayHintString(@NonNull InlayHint inlayHint) {
		Either<String, List<InlayHintLabelPart>> label = inlayHint.getLabel();
		return label.map(Function.identity(), (parts) -> {
			if (parts == null) {
				return null;
			}
			return parts.stream().map(InlayHintLabelPart::getValue).collect(Collectors.joining());
		});
	}

	@Override
	protected CompletableFuture<Void> doResolve(ITextViewer viewer, IProgressMonitor monitor) {
		if (wrapper.isActive() && canResolveInlayHint(wrapper.getServerCapabilities())) {
			return wrapper.execute(ls -> ls.getTextDocumentService().resolveInlayHint(this.inlayHint)
					.thenAcceptAsync(resolvedInlayHint -> {
						inlayHint = resolvedInlayHint;
						if (resolvedInlayHint != null) {
							setLabel(getInlayHintString(resolvedInlayHint));
						}
					}));
		}
		return CompletableFuture.completedFuture(null);
	}

	private static boolean canResolveInlayHint(ServerCapabilities capabilities) {
		Either<Boolean, InlayHintRegistrationOptions> inlayProvider = capabilities.getInlayHintProvider();
		if (inlayProvider != null && inlayProvider.isRight()) {
			InlayHintRegistrationOptions options = inlayProvider.getRight();
			return options.getResolveProvider() != null && options.getResolveProvider().booleanValue();
		}
		return false;
	}

	/**
	 * Returns the Eclipse position from the given LSP position.
	 *
	 * @param position
	 *            the LSP position to convert
	 * @param document
	 * @return the Eclipse position from the given LSP position.
	 *
	 * @throws BadLocationException
	 */
	private static org.eclipse.jface.text.Position toPosition(Position position, IDocument document)
			throws BadLocationException {
		int start = LSPEclipseUtils.toOffset(position, document);
		return new org.eclipse.jface.text.Position(start, 1);
	}

	@Override
	public final Consumer<MouseEvent> getAction() {
		return me -> {
			String title= getLabel();
			if (title != null && !title.isEmpty()) {
				findLabelPart(me).ifPresent(labelPart -> {
					final Command command = labelPart.getCommand();
					if(command != null && command.getCommand() != null && !command.getCommand().isEmpty()) {
						ExecuteCommandOptions provider = wrapper.getServerCapabilities().getExecuteCommandProvider();
						if (provider != null && provider.getCommands().contains(command.getCommand())) {
							LanguageServerDocumentExecutor.forDocument(document).computeAll((w, ls) -> {
								if (w == this.wrapper) {
									return ls.getWorkspaceService()
											.executeCommand(new ExecuteCommandParams(command.getCommand(), command.getArguments()));
								}
								return CompletableFuture.completedFuture(null);
							});
						} else  {
							CommandExecutor.executeCommandClientSide(command, document);
						}
					}
				});
			}
		};
	}

	private Optional<InlayHintLabelPart> findLabelPart(MouseEvent me) {
		if (inlayHint.getLabel().isRight()) {
			if (inlayHint.getLabel().getRight().size() == 1) {
				return Optional.of(inlayHint.getLabel().getRight().get(0));
			}
			if (location != null && fontData != null) {
				Point relativeLocation = new Point(me.x - location.x, me.y - location.y);
				Display display = Display.getCurrent();
				Image image = null;
				GC gc = null;
				Font font = null;
				try {
					image = new Image(display, 1, 1);
					gc = new GC(image);
					font = new Font(display, fontData);
					gc.setFont(font);
					Point origin = new Point(0, 0);
					for (InlayHintLabelPart l : inlayHint.getLabel().getRight()) {
						Point size = gc.stringExtent(l.getValue());
						Rectangle bounds = new Rectangle(origin.x, origin.y, size.x, size.y);
						if (bounds.contains(relativeLocation)) {
							return Optional.of(l);
						} else {
							origin.x += size.x;
						}
					}
				} finally {
					if (font != null && !font.isDisposed()) {
						font.dispose();
					}
					if (gc != null && !gc.isDisposed()) {
						gc.dispose();
					}
					if (image != null && !image.isDisposed()) {
						image.dispose();
					}
				}
			}
		}
		return Optional.empty();
	}

	@Override
	public Point draw(GC gc, StyledText textWidget, Color color, int x, int y) {
		this.location = new Point(x, y);
		Point size = super.draw(gc, textWidget, color, x, y);
		this.fontData = gc.getFont().getFontData();
		return size;
	}


}
