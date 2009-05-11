/* CanonicalizationRule
 * 
 * Created on Oct 7, 2004
 *
 * Copyright (C) 2004 Internet Archive.
 * 
 * This file is part of the Heritrix web crawler (crawler.archive.org).
 * 
 * Heritrix is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * any later version.
 * 
 * Heritrix is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser Public License
 * along with Heritrix; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.archive.modules.canonicalize;

/**
 * A rule to apply canonicalizing a url.
 * @author stack
 * @version $Date$, $Revision$
 */
public interface CanonicalizationRule {
    /**
     * Apply this canonicalization rule.
     * 
     * @param url Url string we apply this rule to.
     * @param context An object that will provide context for the settings
     * system.  The UURI of the URL we're canonicalizing is an example of
     * an object that provides context.
     * @return Result of applying this rule to passed <code>url</code>.
     */
    public String canonicalize(String url);

    /**
     * @return Name of this rule.
     */
//    public String getName();
    
    /**
     * @param context An object that will provide context for the settings
     * system.  The UURI of the URL we're canonicalizing is an example of
     * an object that provides context.
     * @return True if this rule is enabled and to be run.
     */
    public boolean getEnabled();
}
