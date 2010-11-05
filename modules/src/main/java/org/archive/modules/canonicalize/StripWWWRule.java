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
        return doStripRegexMatch(url, REGEX.pattern());
    }
}
