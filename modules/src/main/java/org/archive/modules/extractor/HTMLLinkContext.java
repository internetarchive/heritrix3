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


/**
 * XPath-like context for HTML discovered URIs.
 * 
 * @author pjack
 */
public class HTMLLinkContext extends LinkContext {
    
    private static final long serialVersionUID = 1L;

    
    final public static HTMLLinkContext META = new HTMLLinkContext("meta");
    
    
    /**
     * The HTML path to the URL.
     */
    private String path;
    
    
    /**
     * Constructor.
     * 
     * @param path   an XPath-like context, eg "A\@HREF"
     */
    public HTMLLinkContext(String path) {
        // FIXME: Verify that path really is XPath-like
        this.path = path;
    }

    
    public HTMLLinkContext(CharSequence element, CharSequence attribute) {
        if (attribute == null) {
            this.path = "";
        } else {
            this.path = element + "/@" + attribute;
        }
    }
    
    @Override
    public String toString() {
        return path;
    }
}
