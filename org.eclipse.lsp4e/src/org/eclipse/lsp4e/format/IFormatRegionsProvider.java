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

/**
 * Can be implemented by clients as OSGi service
 * to provide editor specific formatting regions for the format-on-save feature.
 * The OSGi component service has to implement the {@code serverDefinitionId} property.
 * The value must be the {@code server id} of the corresponding {@code languageServer} extension point.
 * This service will then be used for documents who are connected to this language server.
 * <p>Example:
 * <pre>{@code
 * @Component(property = { "serverDefinitionId:String=org.eclipse.cdt.lsp.server" })
 * public interface IFormatRegionsProvider {
 *  	IRegion[] getFormattingRegions(IDocument document){
 *  		//formats whole document:
 * 			new IRegion[] { new Region(0, document.getLength()) };
 * }
 * }</pre>
 */
public interface IFormatRegionsProvider extends IFormatRegions {

}
