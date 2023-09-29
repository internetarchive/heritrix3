/*
 *  This file is part of the Heritrix web crawler (crawler.archive.org).
 *
 *  Licensed to the Internet Archive (IA) by one or more individual 
 *  contributors. 
 *
 *  The IA licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
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
    private static final long serialVersionUID = 4117965561244539334L;


    /** Class for representing handy default LinkContext values. */
    public static class SimpleLinkContext extends LinkContext {

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

    /** 
     * Stand-in value for inferred urls without  other context. 
     */
    final public static LinkContext INFERRED_MISC
        = new SimpleLinkContext("=INFERRED_MISC");
    
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
