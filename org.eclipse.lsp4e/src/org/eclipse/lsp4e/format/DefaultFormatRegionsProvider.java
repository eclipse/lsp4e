package org.eclipse.lsp4e.format;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.osgi.service.component.annotations.Component;

/**
 * Default OSGi service implementation if no bundle provides a OSGi service for {@link IFormatRegionsProvider}.
 *
 */
@Component
public class DefaultFormatRegionsProvider implements IFormatRegionsProvider {
	private final IFormatRegions noFormat = new NoFormat();

	@Override
	public IRegion[] getFormattingRegions(IDocument document) {
		//TODO: return region depending on a LSP4E preference: NO_FORMAT, EDITED_LINES, ALL_LINES
		// return NO_FORMAT until user can disable the format-on-save feature.
		return noFormat.getFormattingRegions(document);
	}

}
