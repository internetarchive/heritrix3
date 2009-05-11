/*
 * ExtractorCSS
 *
 * $Id$
 *
 * Created on Mar 29, 2005
 *
 * Copyright (C) 2005 Internet Archive.
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

public class RegexpCSSLinkExtractor extends CharSequenceLinkExtractor {

    // private static Logger logger =
    //    Logger.getLogger(RegexpCSSLinkExtractor.class.getName());

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
            DevUtils.warnHandle(e, "RegexpCSSLinkExtractor StackOverflowError");
        }
        return false;
    }

    public void reset() {
        super.reset();
        TextUtils.recycleMatcher(uris);
        uris = null;
    }
    
    protected static CharSequenceLinkExtractor newDefaultInstance() {
        return new RegexpCSSLinkExtractor();
    }
}
