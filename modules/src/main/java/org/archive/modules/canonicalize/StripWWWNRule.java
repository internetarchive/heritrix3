/* StripWWWRule
 * 
 * Created on Oct 5, 2004
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

import java.util.regex.Pattern;

/**
 * Strip any 'www[0-9]*' found on http/https URLs IF they have some
 * path/query component (content after third slash). Top 'slash page' 
 * URIs are left unstripped: we prefer crawling redundant
 * top pages to missing an entire site only available from either
 * the www-full or www-less hostname, but not both. 
 * @author stack
 * @version $Date$, $Revision$
 */
public class StripWWWNRule extends BaseRule {
    private static final long serialVersionUID = 3L;

//    private static final String DESCRIPTION = "Strip any 'www[0-9]*' found. " +
//        "Use this rule to equate 'http://www.archive.org/index.html' and " +
//        "'http://www0001.archive.org/index.html' with " +
//        "'http://archive.org/index.html'.  The resulting canonicalization " +
//        "returns 'http://archive.org/index.html'.  It removes any www's " +
//        "or wwwNNN's found, where 'N' is one or more numerics, EXCEPT " +
//        "on URIs that have no path/query component " +
//        ". Top-level 'slash page' URIs are left unstripped: we prefer " +
//        "crawling redundant top pages to missing an entire site only " +
//        "available from either the www-full or www-less hostname, but not " +
//        "both.  Operates on http and https schemes only. " +
//        "Use StripWWWRule to strip a lone 'www' only (This rule is a " +
//        "more general version of StripWWWRule).";
    
    private static final Pattern REGEX =
        Pattern.compile("(?i)^(https?://)(?:www[0-9]*\\.)([^/]*/.+)$");
    
    public StripWWWNRule() {
    }

    public String canonicalize(String url) {
        return doStripRegexMatch(url, REGEX.matcher(url));
    }
}