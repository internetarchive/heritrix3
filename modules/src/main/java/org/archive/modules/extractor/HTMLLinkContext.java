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
    final public static HTMLLinkContext A_HREF = new HTMLLinkContext("a", "href");
    final public static HTMLLinkContext IMG_SRC = new HTMLLinkContext("img", "src");
    final public static HTMLLinkContext SCRIPT_SRC = new HTMLLinkContext("script", "src");
    final public static HTMLLinkContext META_HREF = new HTMLLinkContext("meta", "href");
    
    
    /**
     * The HTML path to the URL.
     */
    private final String path;
    
    /**
     * return an instance of HTMLLinkContext for attribute {@code attr} in
     * element {@code el}. returns pre-allocated shared instance for common case,
     * or new instance for others.
     * @param el element name
     * @param attr attribute name
     * @return instance of HTMLLinkContext
     */
    public static HTMLLinkContext get(CharSequence el, CharSequence attr) {
        if (attr.equals("href") || attr.equals("HREF")) {
            if (el.equals("a") || el.equals("A")) return A_HREF;
            if (el.equals("meta") || el.equals("META")) return META_HREF;
        } else if (attr.equals("src") || attr.equals("SRC")) {
            if (el.equals("img") || attr.equals("IMG")) return IMG_SRC;
            if (el.equals("script") || attr.equals("SCRIPT")) return SCRIPT_SRC;
        }
        return new HTMLLinkContext(el, attr);
    }
    /**
     * return an instance of HTMLLinkContext for path {@code path}.
     * returns pre-allocated shared instance for common case, or new instance for others.
     * <p>TODO: most code calling this method builds path by concatenating element name
     * and attribute name. consider changing such code to call {@link #get(CharSequence, CharSequence)}
     * instead.</p> 
     * @param path element and attribute in XLink-like path notation
     * @return instance of HTMLLinkContext
     */
    public static HTMLLinkContext get(String path) {
        if (path.equalsIgnoreCase("a/@href")) return A_HREF;
        if (path.equalsIgnoreCase("meta/@href")) return META_HREF;
        if (path.equalsIgnoreCase("img/@src")) return IMG_SRC;
        if (path.equalsIgnoreCase("script/@src")) return SCRIPT_SRC;
        return new HTMLLinkContext(path);
    }
    /**
     * Constructor.
     * 
     * @param path   an XPath-like context, eg "A\@HREF"
     */
    protected HTMLLinkContext(String path) {
        // FIXME: Verify that path really is XPath-like
        this.path = path;
    }

    
    protected HTMLLinkContext(CharSequence element, CharSequence attribute) {
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
