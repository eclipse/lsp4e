/*******************************************************************************
 * Copyright (c) 2022 Avaloq Evolution AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.operations.semanticTokens;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextPresentationListener;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.TextPresentation;
import org.eclipse.jface.text.TextViewer;
import org.eclipse.jface.text.reconciler.DirtyRegion;
import org.eclipse.jface.text.reconciler.IReconcilingStrategy;
import org.eclipse.jface.text.reconciler.IReconcilingStrategyExtension;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServersRegistry.LanguageServerDefinition;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.tm4e.ui.TMUIPlugin;
import org.eclipse.tm4e.ui.themes.ITheme;

/**
 * A reconciler strategy using semantic highlighting as defined by LSP.
 */
public class SemanticHighlightReconcilerStrategy
		implements IReconcilingStrategy, IReconcilingStrategyExtension, ITextPresentationListener {

	private @Nullable ITextViewer viewer;

	private @Nullable ITheme theme;

	private IDocument document;

	private Map<String, SemanticTokensLegend> semanticTokensLegendMap;

	private List<StyleRange> previousRanges;

	/**
	 * Installs the reconciler on the given text viewer. After this method has been
	 * finished, the reconciler is operational, i.e., it works without requesting
	 * further client actions until <code>uninstall</code> is called.
	 *
	 * @param textViewer
	 *            the viewer on which the reconciler is installed
	 */
	public void install(final ITextViewer textViewer) {
		viewer = textViewer;
		theme = TMUIPlugin.getThemeManager().getDefaultTheme();
		if (textViewer instanceof TextViewer viewer) {
			viewer.addTextPresentationListener(this);
		}
		previousRanges = new ArrayList<>();
	}

	/**
	 * Removes the reconciler from the text viewer it has previously been installed
	 * on.
	 */
	public void uninstall() {
		theme = null;
		ITextViewer textViewer = viewer;
		if (textViewer instanceof TextViewer viewer) {
			viewer.removeTextPresentationListener(this);
		}
		viewer = null;
		previousRanges = null;
		semanticTokensLegendMap = null;

	}

	private void initSemanticTokensLegendMap() {
		IFile file = LSPEclipseUtils.getFile(document);
		if (file != null) {
			try {
				semanticTokensLegendMap = new HashMap<>();
				for (LanguageServerWrapper wrapper: LanguageServiceAccessor.getLSWrappers(file, x -> true)) {
					ServerCapabilities serverCapabilities = wrapper.getServerCapabilities();
					if (serverCapabilities != null) {
						SemanticTokensWithRegistrationOptions semanticTokensProvider = serverCapabilities.getSemanticTokensProvider();
						if (semanticTokensProvider != null) {
							semanticTokensLegendMap.put(wrapper.serverDefinition.id, semanticTokensProvider.getLegend());
						}
					}
				}
			} catch (IOException e) {
				LanguageServerPlugin.logError(e);
			}
		}
	}

	private SemanticTokensParams getSemanticTokensParams() {
		URI uri = LSPEclipseUtils.toUri(document);
		if (uri != null) {
			SemanticTokensParams semanticTokensParams = new SemanticTokensParams();
			semanticTokensParams.setTextDocument(new TextDocumentIdentifier(uri.toString()));
			return semanticTokensParams;
		}
		return null;
	}

	private void saveStyle(final SemanticTokens semanticTokens, final SemanticTokensLegend semanticTokensLegend) {
		if (semanticTokens == null || semanticTokensLegend == null) {
			return;
		}
		List<Integer> dataStream = semanticTokens.getData();
		if (!dataStream.isEmpty()) {
			try {
				List<StyleRange> styleRanges = getStyleRanges(dataStream, semanticTokensLegend);
				saveStyles(styleRanges);
			} catch (BadLocationException e) {
				LanguageServerPlugin.logError(e);
			}
		}
	}

	private StyleRange clone(final StyleRange styleRange) {
		StyleRange clonedStyleRange = new StyleRange(styleRange.start, styleRange.length, styleRange.foreground,
				styleRange.background, styleRange.fontStyle);
		clonedStyleRange.strikeout = styleRange.strikeout;
		return clonedStyleRange;
	}

	private void mergeStyles(final TextPresentation textPresentation, final List<StyleRange> styleRanges) {
		StyleRange[] array = new StyleRange[styleRanges.size()];
		array = styleRanges.toArray(array);
		textPresentation.replaceStyleRanges(array);
	}

	private boolean overlaps(final StyleRange range, final IRegion region) {
		return isContained(range.start, region) || isContained(range.start + range.length, region)
				|| isContained(region.getOffset(), range);
	}

	private boolean isContained(final int offset, final StyleRange range) {
		return offset >= range.start && offset < (range.start + range.length);
	}

	private boolean isContained(final int offset, final IRegion region) {
		return offset >= region.getOffset() && offset < (region.getOffset() + region.getLength());
	}

	private void saveStyles(final List<StyleRange> styleRanges) {
		synchronized (previousRanges) {
			previousRanges.clear();
			previousRanges.addAll(styleRanges);
			previousRanges.sort(Comparator.comparing(s -> s.start));
		}
	}

	private List<StyleRange> getStyleRanges(final List<Integer> dataStream,
			final SemanticTokensLegend semanticTokensLegend) throws BadLocationException {
		List<StyleRange> styleRanges = new ArrayList<>(dataStream.size() / 5);

		int idx = 0;
		int prevLine = 0;
		int line = 0;
		int offset = 0;
		int length = 0;
		String tokenType = null;
		for (Integer data : dataStream) {
			switch (idx % 5) {
			case 0: // line
				line += data;
				break;
			case 1: // offset
				if (line == prevLine) {
					offset += data;
				} else {
					offset = LSPEclipseUtils.toOffset(new Position(line, data), document);
				}
				break;
			case 2: // length
				length = data;
				break;
			case 3: // token type
				tokenType = tokenType(data, semanticTokensLegend.getTokenTypes());
				break;
			case 4: // token modifier
				prevLine = line;
				List<String> tokenModifiers = tokenModifiers(data, semanticTokensLegend.getTokenModifiers());
				StyleRange styleRange = getStyleRange(offset, length, textAttribute(tokenType));
				if (tokenModifiers.stream().anyMatch(x -> x.equals(SemanticTokenModifiers.Deprecated))) {
					styleRange.strikeout = true;
				}
				styleRanges.add(styleRange);
				break;
			}
			idx++;
		}
		return styleRanges;
	}

	private String tokenType(final Integer data, final List<String> legend) {
		try {
			return legend.get(data - 1);
		} catch (IndexOutOfBoundsException e) {
			return null; // no match
		}
	}

	private List<String> tokenModifiers(final Integer data, final List<String> legend) {
		if (data.intValue() == 0) {
			return Collections.emptyList();
		}
		BitSet bitSet = BitSet.valueOf(new long[] { data });
		List<String> tokenModifiers = new ArrayList<>();
		for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
			try {
				tokenModifiers.add(legend.get(i));
			} catch (IndexOutOfBoundsException e) {
				// no match
			}
		}

		return tokenModifiers;
	}

	private TextAttribute textAttribute(final String tokenType) {
		ITheme localTheme = theme;
		if (localTheme != null && tokenType != null) {
			IToken token = localTheme.getToken(tokenType);
			if (token != null) {
				Object data = token.getData();
				if (data instanceof TextAttribute textAttribute) {
					return textAttribute;
				}
			}
		}
		return null;
	}

	/**
	 * Gets a style range for the given inputs.
	 *
	 * @param offset
	 *            the offset of the range to be styled
	 * @param length
	 *            the length of the range to be styled
	 * @param attr
	 *            the attribute describing the style of the range to be styled
	 */
	private StyleRange getStyleRange(final int offset, final int length, final TextAttribute attr) {
		final StyleRange styleRange;
		if (attr != null) {
			final int style = attr.getStyle();
			final int fontStyle = style & (SWT.ITALIC | SWT.BOLD | SWT.NORMAL);
			styleRange = new StyleRange(offset, length, attr.getForeground(), attr.getBackground(), fontStyle);
			styleRange.strikeout = (style & TextAttribute.STRIKETHROUGH) != 0;
			styleRange.underline = (style & TextAttribute.UNDERLINE) != 0;
			styleRange.font = attr.getFont();
			return styleRange;
		} else {
			styleRange = new StyleRange();
			styleRange.start = offset;
			styleRange.length = length;
		}
		return styleRange;
	}

	@Override
	public void setProgressMonitor(final IProgressMonitor monitor) {
	}

	@Override
	public void setDocument(final IDocument document) {
		this.document = document;
		initSemanticTokensLegendMap();
	}

	private SemanticTokensLegend getSemanticTokensLegend(final LanguageServer languageSever) {
		Optional<LanguageServerDefinition> serverDefinition = LanguageServiceAccessor
				.resolveServerDefinition(languageSever);
		if (serverDefinition.isPresent()) {
			return semanticTokensLegendMap.get(serverDefinition.get().id);
		}
		return null;
	}

	private boolean hasSemanticTokensFull(final ServerCapabilities serverCapabilities) {
		return serverCapabilities.getSemanticTokensProvider() != null
				&& serverCapabilities.getSemanticTokensProvider().getFull().getLeft();
	}

	private CompletableFuture<Void> semanticTokensFull(final List<LanguageServer> languageServers) {
		return CompletableFuture
				.allOf(languageServers.stream().map(this::semanticTokensFull).toArray(CompletableFuture[]::new));
	}

	private CompletableFuture<Void> semanticTokensFull(final LanguageServer languageServer) {
		SemanticTokensParams semanticTokensParams = getSemanticTokensParams();
		return languageServer.getTextDocumentService().semanticTokensFull(semanticTokensParams)
				.thenAccept(semanticTokens -> {
					saveStyle(semanticTokens, getSemanticTokensLegend(languageServer));
				}).exceptionally(e -> {
					LanguageServerPlugin.logError(e);
					return null;
				});
	}

	private void fullReconcile() {
		try {
			LanguageServiceAccessor.getLanguageServers(document, this::hasSemanticTokensFull)//
					.thenAccept(this::semanticTokensFull).get();
		} catch (InterruptedException | ExecutionException e) {
			LanguageServerPlugin.logError(e);
		}
	}

	@Override
	public void initialReconcile() {
		fullReconcile();
	}

	@Override
	public void reconcile(final DirtyRegion dirtyRegion, final IRegion subRegion) {
		fullReconcile();
	}

	@Override
	public void reconcile(final IRegion partition) {
		fullReconcile();
	}

	private List<StyleRange> appliedRanges(final TextPresentation textPresentation) {
		synchronized (previousRanges) {
			// we need to create new styles because the text presentation might change a
			// style when applied to the presentation
			// and we want the ones saved from the reconciling as immutable
			return previousRanges.stream()//
					.filter(r -> overlaps(r, textPresentation.getExtent()))//
					.map(this::clone).collect(Collectors.toList());
		}
	}

	@Override
	public void applyTextPresentation(final TextPresentation textPresentation) {
		mergeStyles(textPresentation, appliedRanges(textPresentation));
	}
}