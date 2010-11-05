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
 * Strip any 'userinfo' found on http/https URLs.
 * @author stack
 * @version $Date$, $Revision$
 */
public class StripUserinfoRule extends BaseRule {

    private static final long serialVersionUID = 3L;

//    private static final String DESCRIPTION = "Strip any 'userinfo' found. " +
//        "Use this rule to equate 'http://stack:psswrd@archive.org/index.htm'" + 
//        " and 'http://archive.org/index.htm'. The resulting canonicalization" +
//        " returns 'http://archive.org/index.htm'. Removes any userinfo" +
//        " found.  Operates on http/https/ftp/ftps schemes only.";
    
    /**
     * Strip userinfo.
     */
    private static final Pattern REGEX =
        Pattern.compile("^((?:(?:https?)|(?:ftps?))://)(?:[^/]+@)(.*)$",
            Pattern.CASE_INSENSITIVE);
    
    public StripUserinfoRule() {
    }

    public String canonicalize(String url) {
        return doStripRegexMatch(url, REGEX.pattern());
    }
}
