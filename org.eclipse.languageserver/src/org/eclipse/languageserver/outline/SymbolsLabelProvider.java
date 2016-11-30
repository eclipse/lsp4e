/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.languageserver.outline;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.DecorationOverlayIcon;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider.IStyledLabelProvider;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.languageserver.LSPEclipseUtils;
import org.eclipse.languageserver.LSPImages;
import org.eclipse.languageserver.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.languageserver.ui.Messages;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.progress.ProgressManager;
import org.eclipse.ui.navigator.ICommonContentExtensionSite;
import org.eclipse.ui.navigator.ICommonLabelProvider;

public class SymbolsLabelProvider extends LabelProvider implements ICommonLabelProvider, IStyledLabelProvider {

	private Map<Image, Image[]> overlays = new HashMap<>();
	
	private boolean showLocation;

	public SymbolsLabelProvider() {
		this(false);
	}

	public SymbolsLabelProvider(boolean showLocation) {
		this.showLocation = showLocation;
	}
	
	@Override
	public Image getImage(Object element) {
		if (element == null){
			return null;
		}
		if (element == LSSymbolsContentProvider.COMPUTING) {
			return JFaceResources.getImage(ProgressManager.WAITING_JOB_KEY);
		}
		if (element instanceof Throwable) {
			return PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJS_ERROR_TSK);
		}
		SymbolInformation symbolInformation = (SymbolInformation) element;
		Image res = LSPImages.imageFromSymbolKind(symbolInformation.getKind());
		IResource resource = LSPEclipseUtils.findResourceFor(symbolInformation.getLocation().getUri());
		if (resource != null) {
			try {
				IDocument doc = LSPEclipseUtils.getDocument(resource);
				int maxSeverity = -1;
				for (IMarker marker : resource.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_ZERO)) {
					int offset = marker.getAttribute(IMarker.CHAR_START, -1);
					if (offset != -1
					    && offset >= LSPEclipseUtils.toOffset(symbolInformation.getLocation().getRange().getStart(), doc)
					    && offset <= LSPEclipseUtils.toOffset(symbolInformation.getLocation().getRange().getEnd(), doc)) {
						maxSeverity = Math.max(maxSeverity, marker.getAttribute(IMarker.SEVERITY, -1));
					}
				}
				if (maxSeverity > IMarker.SEVERITY_INFO) {
					return getOverlay(res, maxSeverity);
				}
			} catch (CoreException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (BadLocationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return res;
	}

	private Image getOverlay(Image res, int maxSeverity) {
		if (maxSeverity != 1 && maxSeverity != 2) {
			throw new IllegalArgumentException("Severity " + maxSeverity + " not supported."); //$NON-NLS-1$ //$NON-NLS-2$
		}
		if (!this.overlays.containsKey(res)) {
			this.overlays.put(res, new Image[2]);
		}
		Image[] currentOverlays = this.overlays.get(res);
		if (currentOverlays[maxSeverity - 1] == null) {
			String overlayId = null;
			if (maxSeverity == IMarker.SEVERITY_ERROR) {
				overlayId = ISharedImages.IMG_DEC_FIELD_ERROR;
			} else if (maxSeverity == IMarker.SEVERITY_WARNING) {
				overlayId = ISharedImages.IMG_DEC_FIELD_WARNING;
			}
			currentOverlays[maxSeverity - 1] = new DecorationOverlayIcon(res,
				PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(overlayId),
				IDecoration.BOTTOM_LEFT).createImage();
		}
		return currentOverlays[maxSeverity - 1];
	}

	@Override
	public String getText(Object element) {
		return getStyledText(element).getString();
	}

	@Override
	public StyledString getStyledText(Object element) {

		if (element == LSSymbolsContentProvider.COMPUTING) {
			return new StyledString(Messages.outline_computingSymbols);
		}
		if (element instanceof Throwable) {
			return new StyledString(((Throwable) element).getMessage());
		}
		if (element instanceof LSPDocumentInfo) {
			return new StyledString(((LSPDocumentInfo)element).getFileUri().getPath());
		}
		StyledString res = new StyledString();
		if (element == null){
			return res;
		}
		SymbolInformation symbol = (SymbolInformation) element;
		res.append(symbol.getName(), null);
		res.append(" :", null); //$NON-NLS-1$
		res.append(symbol.getKind().toString(), StyledString.DECORATIONS_STYLER);

		if (showLocation) {
			URI uri = URI.create(symbol.getLocation().getUri());
			res.append(' ');
			res.append(uri.getPath(), StyledString.QUALIFIER_STYLER);
		}
		return res;
	}

	@Override
	public void restoreState(IMemento aMemento) {
	}

	@Override
	public void saveState(IMemento aMemento) {
	}

	@Override
	public String getDescription(Object anElement) {
		return null;
	}

	@Override
	public void init(ICommonContentExtensionSite aConfig) {
	}

	@Override
	public void dispose() {
		
		super.dispose();
	}
}
