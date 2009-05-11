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
 * Strip any 'www' found on http/https URLs, IF they have some
 * path/query component (content after third slash). (Top 'slash page' 
 * URIs are left unstripped, so that we prefer crawling redundant
 * top pages to missing an entire site only available from either
 * the www-full or www-less hostname, but not both). 
 * @author stack
 * @version $Date$, $Revision$
 */
public class StripWWWRule extends BaseRule {

    private static final long serialVersionUID = 3L;

//    private static final String DESCRIPTION = "Strip any 'www' found. " +
//        "Use this rule to equate 'http://www.archive.org/index.html' and" +
//        " 'http://archive.org/index.html'. The resulting canonicalization" +
//        " returns 'http://archive.org/index.html'.  It removes any www's " +
//        "found, except on URIs that have no path/query component " +
//        "('slash' pages).  Operates on http and https schemes only. " +
//        "Use the more general StripWWWNRule if you want to strip both 'www' " +
//        "and 'www01', 'www02', etc.";
    
    private static final Pattern REGEX =
        Pattern.compile("(?i)^(https?://)(?:www\\.)([^/]*/.+)$");
    
    public StripWWWRule() {
    }

    public String canonicalize(String url) {
        return doStripRegexMatch(url, REGEX.matcher(url));
    }
}
