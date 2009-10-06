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

package org.archive.extractor;

import java.util.regex.Matcher;

import org.apache.commons.httpclient.URIException;
import org.archive.modules.extractor.Hop;
import org.archive.modules.extractor.Link;
import org.archive.modules.extractor.LinkContext;
import org.archive.net.UURIFactory;
import org.archive.util.DevUtils;
import org.archive.util.TextUtils;

/**
 * This extractor is parsing URIs from CSS type files.
 * The format of a CSS URL value is 'url(' followed by optional white space
 * followed by an optional single quote (') or double quote (") character
 * followed by the URL itself followed by an optional single quote (') or
 * double quote (") character followed by optional white space followed by ')'.
 * Parentheses, commas, white space characters, single quotes (') and double
 * quotes (") appearing in a URL must be escaped with a backslash:
 * '\(', '\)', '\,'. Partial URLs are interpreted relative to the source of
 * the style sheet, not relative to the document. <a href="http://www.w3.org/TR/REC-CSS1#url">
 * Source: www.w3.org</a>
 *
 * ROUGH DRAFT IN PROGRESS / incomplete... untested... major changes likely
 *
 * @author igor gojomo
 *
 **/

public class RegexCSSLinkExtractor extends CharSequenceLinkExtractor {

    private static String ESCAPED_AMP = "&amp";
    // CSS escapes: "Parentheses, commas, whitespace characters, single
    // quotes (') and double quotes (") appearing in a URL must be
    // escaped with a backslash"
    static final String CSS_BACKSLASH_ESCAPE = "\\\\([,'\"\\(\\)\\s])";

    protected Matcher uris;

    /**
     *  CSS URL extractor pattern.
     *
     *  This pattern extracts URIs for CSS files
     **/
    static final String CSS_URI_EXTRACTOR =
    "(?:@import (?:url[(]|)|url[(])\\s*([\\\"\']?)([^\\\"\'].*?)\\1\\s*[);]";

    protected boolean findNextLink() {
        if (uris == null) {
            uris = TextUtils.getMatcher(CSS_URI_EXTRACTOR, sourceContent);
            // NOTE: this matcher can't be recycled in this method because
            // it is reused on rentry
        }
        String cssUri;
        try {
            while (uris.find()) {
                cssUri = uris.group(2);
                // TODO: Escape more HTML Entities.
                cssUri = TextUtils.replaceAll(ESCAPED_AMP, cssUri, "&");
                // Remove backslashes when used as escape character in CSS URL
                cssUri = TextUtils.replaceAll(CSS_BACKSLASH_ESCAPE, cssUri, "$1");
                // TODO: handle relative URIs?
                try {
                    Link link = new Link(source, UURIFactory.getInstance(base,
                            cssUri), LinkContext.EMBED_MISC, Hop.EMBED);
                    next.addLast(link);
                } catch (URIException e) {
                    extractErrorListener.noteExtractError(e, source, cssUri);
                }
                return true;
            }
        } catch (StackOverflowError e) {
            DevUtils.warnHandle(e, "RegexCSSLinkExtractor StackOverflowError");
        }
        return false;
    }

    public void reset() {
        super.reset();
        TextUtils.recycleMatcher(uris);
        uris = null;
    }
    
    protected static CharSequenceLinkExtractor newDefaultInstance() {
        return new RegexCSSLinkExtractor();
    }
}
