/*******************************************************************************
 * Copyright (c) 2017 TypeFox and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Jan Koehnlein (TypeFox) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.outline;

import org.eclipse.core.expressions.PropertyTester;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.core.runtime.content.IContentTypeManager;
import org.eclipse.lsp4j.SymbolInformation;

/**
 * A property tester that checks whether a given SmybolInformation belongs to a
 * specific file extension or content type.
 *
 * It can be used to customize the outline view for an LSP based language using
 * the trigger expression in an extension to the
 * <code>org.eclipse.ui.navigator.navigatorContent</code> extension point:
 *
 * <pre>
 *  <extension point="org.eclipse.ui.navigator.navigatorContent">
 *     <navigatorContent
 *           activeByDefault="true"
 *           contentProvider="my.lang.CustomContentProvider"
 *           labelProvider="my.lang.CustomLabelProvider"
 *           id="my.lang.navigatorContent"
 *           priority="high"
 *           name="MyLang Symbols">
 *       <triggerPoints>
 *           <test property="org.eclipse.lsp4e.symbolInformation.contentTypeId" value="my.lang"/>
 *       </triggerPoints>
 *     </navigatorContent>
 *  </extension>
 *  <extension point="org.eclipse.ui.navigator.viewer">
 *     <viewer viewerId="org.eclipse.lsp4e.outline"></viewer>
 *     <viewerContentBinding viewerId="org.eclipse.lsp4e.outline">
 *        <includes>
 *           <contentExtension
 *                 pattern="my.lang.navigatorContent">
 *           </contentExtension>
 *        </includes>
 *     </viewerContentBinding>
 *  </extension>
 * </pre>
 *
 * @author koehnlein
 */
public class SymbolInformationPropertyTester extends PropertyTester {

	public static final String FILE_EXTENSION = "extension"; //$NON-NLS-1$
	public static final String CONTENT_TYPE_ID = "contentTypeId"; //$NON-NLS-1$

	@Override
	public boolean test(Object receiver, String property, Object[] args, Object expectedValue) {
		if (receiver instanceof SymbolInformation) {
			SymbolInformation info = (SymbolInformation) receiver;
			if (info.getLocation() == null || info.getLocation().getUri() == null) {
				return false;
			}
			String uri = info.getLocation().getUri();
			switch (property) {
			case FILE_EXTENSION:
				return uri.endsWith("." + expectedValue.toString()); //$NON-NLS-1$
			case CONTENT_TYPE_ID:
				IContentTypeManager contentTypeManager = Platform.getContentTypeManager();
				IContentType contentType = contentTypeManager.findContentTypeFor(uri);
				return contentType != null && contentType.getId().equals(expectedValue.toString());
			default:
				return false;
			}
		}
		return false;
	}
}
