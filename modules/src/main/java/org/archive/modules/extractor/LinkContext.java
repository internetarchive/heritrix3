/* Copyright (C) 2006 Internet Archive.
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
 *
 * LinkContext.java
 * Created on October 5, 2006
 *
 * $Header$
 */
package org.archive.modules.extractor;

import java.io.Serializable;


/**
 * The context of link discovery.  Different subclasses represent different
 * kinds of contexts.
 * 
 * @author pjack
 */
public abstract class LinkContext implements Serializable {

    
    /** Class for representing handy default LinkContext values. */
    private static class SimpleLinkContext extends LinkContext {

        private static final long serialVersionUID = 1L;

        private String desc;
        
        public SimpleLinkContext(String desc) {
            this.desc = desc;
        }
        
        public String toString() {
            return desc;
        }
    }

    /** Stand-in value for embeds without other context. */
    final public static LinkContext EMBED_MISC
     = new SimpleLinkContext("=EMBED_MISC");

    /** Stand-in value for JavaScript-discovered urls without other context. */
    final public static LinkContext JS_MISC
     = new SimpleLinkContext("=JS_MISC");
    
    /** Stand-in value for navlink urls without other context. */
    final public static LinkContext NAVLINK_MISC
     = new SimpleLinkContext("=NAVLINK_MISC");
    
    /** 
     * Stand-in value for speculative/aggressively extracted urls without 
     * other context. 
     */
    final public static LinkContext SPECULATIVE_MISC
     = new SimpleLinkContext("=SPECULATIVE_MISC");

    /** Stand-in value for prerequisite urls without other context. */
    final public static LinkContext PREREQ_MISC
     = new SimpleLinkContext("=PREREQ_MISC");

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof LinkContext)) {
            return false;
        }
        return o.toString().equals(toString());
    }
    
    
    public int hashCode() {
        return toString().hashCode();
    }
}
