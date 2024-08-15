/*******************************************************************************
 * Copyright (c) 2020 Pivotal Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Martin Lippert (Pivotal Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.jdt.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.osgi.util.NLS;

@NonNullByDefault({})
public class Messages extends NLS {

   public static String javaSpecificCompletionError;

   static {
      NLS.initializeMessages("org.eclipse.lsp4e.jdt.messages", Messages.class); //$NON-NLS-1$
   }
}
