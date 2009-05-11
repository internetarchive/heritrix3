
/* $Id:  $
 *
 * Copyright (C) 2007 Olaf Freyer
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
package org.archive.modules.deciderules;

import org.archive.modules.ProcessorURI;

/**
 * DecideRule whose decision is applied if the URI's content-type 
 * is present and does not match the supplied regular expression. 
 * 
 * @author Olaf Freyer
 */
public class ContentTypeNotMatchesRegExpDecideRule extends
        ContentTypeMatchesRegExpDecideRule {
    private static final long serialVersionUID = 4729800377757426137L;

    public ContentTypeNotMatchesRegExpDecideRule() {
    }
    
    /**
     * Evaluate whether given object's string version does not match 
     * configured regexp (by reversing the superclass's answer).
     * 
     * @param object Object to make decision about.
     * @return true if the regexp is not matched
     */
    @Override
    protected boolean evaluate(ProcessorURI o) {
        return !super.evaluate(o);
    }
    
}
