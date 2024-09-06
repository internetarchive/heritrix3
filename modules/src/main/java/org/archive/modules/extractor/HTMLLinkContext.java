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
    final public static HTMLLinkContext IMG_SRCSET = new HTMLLinkContext("img", "srcset");
    final public static HTMLLinkContext SOURCE_SRCSET = new HTMLLinkContext("source", "srcset");
    final public static HTMLLinkContext IMG_DATA_SRC = new HTMLLinkContext("img", "data-src");
    final public static HTMLLinkContext IMG_DATA_SRC_SMALL = new HTMLLinkContext("img", "data-src-small");
    final public static HTMLLinkContext IMG_DATA_SRC_MEDIUM = new HTMLLinkContext("img", "data-src-medium");
    final public static HTMLLinkContext IMG_DATA_SRCSET = new HTMLLinkContext("img", "data-srcset");
    final public static HTMLLinkContext SOURCE_DATA_SRCSET = new HTMLLinkContext("source", "data-srcset");
    final public static HTMLLinkContext IMG_DATA_ORIGINAL = new HTMLLinkContext("img", "data-original");
    final public static HTMLLinkContext IMG_DATA_ORIGINAL_SET = new HTMLLinkContext("img", "data-original-set");
    final public static HTMLLinkContext SOURCE_DATA_ORIGINAL_SET = new HTMLLinkContext("source", "data-original-set");
    final public static HTMLLinkContext IMG_DATA_LAZY = new HTMLLinkContext("img", "data-lazy");
    final public static HTMLLinkContext IMG_DATA_LAZY_SRCSET = new HTMLLinkContext("img", "data-lazy-srcset");
    final public static HTMLLinkContext SOURCE_DATA_LAZY_SRCSET = new HTMLLinkContext("source", "data-lazy-srcset");
    final public static HTMLLinkContext IMG_DATA_FULL_SRC = new HTMLLinkContext("img", "data-full-src");
    final public static HTMLLinkContext SCRIPT_SRC = new HTMLLinkContext("script", "src");
    final public static HTMLLinkContext META_HREF = new HTMLLinkContext("meta", "href");
    final public static HTMLLinkContext LINK_IMAGESRCSET = new HTMLLinkContext("link", "imagesrcset");
    
    
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
        switch (attr.toString().toLowerCase()) {
        case "href":
            if (el.toString().equalsIgnoreCase("a")) return A_HREF;
            if (el.toString().equalsIgnoreCase("meta")) return META_HREF;
            break;
        case "src":
            if (el.toString().equalsIgnoreCase("img")) return IMG_SRC;
            if (el.toString().equalsIgnoreCase("script")) return SCRIPT_SRC;
            break;
        case "srcset":
            if (el.toString().equalsIgnoreCase("img")) return IMG_SRCSET;
            if (el.toString().equalsIgnoreCase("source")) return SOURCE_SRCSET;
            break;
        case "data-src":
            if (el.toString().equalsIgnoreCase("img")) return IMG_DATA_SRC;
            break;
        case "data-srcset":
            if (el.toString().equalsIgnoreCase("img")) return IMG_DATA_SRCSET;
            if (el.toString().equalsIgnoreCase("source")) return SOURCE_DATA_SRCSET;
            break;
        case "data-original":
            if (el.toString().equalsIgnoreCase("img")) return IMG_DATA_ORIGINAL;
            break;
        case "data-original-set":
            if (el.toString().equalsIgnoreCase("img")) return IMG_DATA_ORIGINAL_SET;
            if (el.toString().equalsIgnoreCase("source")) return SOURCE_DATA_ORIGINAL_SET;
            break;
        case "data-full-src":
            if (el.toString().equalsIgnoreCase("img")) return IMG_DATA_FULL_SRC;
            break;
        case "data-lazy-srcset":
            if (el.toString().equalsIgnoreCase("img")) return IMG_DATA_LAZY_SRCSET;
            if (el.toString().equalsIgnoreCase("source")) return SOURCE_DATA_LAZY_SRCSET;
            break;
        case "data-lazy":
            if (el.toString().equalsIgnoreCase("img")) return IMG_DATA_LAZY;
            break;
        case "data-src-small":
            if (el.toString().equalsIgnoreCase("img")) return IMG_DATA_SRC_SMALL;
            break;
        case "data-src-medium":
            if (el.toString().equalsIgnoreCase("img")) return IMG_DATA_SRC_MEDIUM;
            break;
        case "imagesrcset":
            if (el.toString().equalsIgnoreCase("link")) return LINK_IMAGESRCSET;
            break;
        default:
            return new HTMLLinkContext(el, attr);
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
        if (path.equalsIgnoreCase("img/@srcset")) return IMG_SRCSET;
        if (path.equalsIgnoreCase("source/@srcset")) return SOURCE_SRCSET;
        if (path.equalsIgnoreCase("script/@src")) return SCRIPT_SRC;
        if (path.equalsIgnoreCase("img/@data-src")) return IMG_DATA_SRC;
        if (path.equalsIgnoreCase("img/@data-src-small")) return IMG_DATA_SRC_SMALL;
        if (path.equalsIgnoreCase("img/@data-src-medium")) return IMG_DATA_SRC_MEDIUM;
        if (path.equalsIgnoreCase("img/@data-srcset")) return IMG_DATA_SRCSET;
        if (path.equalsIgnoreCase("img/@data-original")) return IMG_DATA_ORIGINAL;
        if (path.equalsIgnoreCase("img/@data-full-src")) return IMG_DATA_FULL_SRC; 
        if (path.equalsIgnoreCase("img/@data-original-set")) return IMG_DATA_ORIGINAL_SET;
        if (path.equalsIgnoreCase("source/@data-original-set")) return SOURCE_DATA_ORIGINAL_SET;
        if (path.equalsIgnoreCase("img/@data-lazy-srcset")) return IMG_DATA_LAZY_SRCSET;
        if (path.equalsIgnoreCase("source/@data-lazy-srcset")) return SOURCE_DATA_LAZY_SRCSET;
        if (path.equalsIgnoreCase("img/@data-lazy")) return IMG_DATA_LAZY;
        if (path.equalsIgnoreCase("source/@data-srcset")) return SOURCE_DATA_SRCSET;
        if (path.equalsIgnoreCase("link/@imagesrcset")) return LINK_IMAGESRCSET;
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
