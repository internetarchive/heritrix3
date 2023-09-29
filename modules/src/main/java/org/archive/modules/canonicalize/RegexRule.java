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

import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * General conversion rule.
 * @author stack
 * @version $Date$, $Revision$
 */
public class RegexRule
extends BaseRule {

    private static final long serialVersionUID = -3L;

    protected static Logger logger =
        Logger.getLogger(BaseRule.class.getName());

//    private static final String DESCRIPTION = "General regex rule. " +
//        "Specify a matching regex and a format string used outputting" +
//        " result if a match was found.  If problem compiling regex or" +
//        " interpreting format, problem is logged, and this rule does" +
//        " nothing.  See User Manual for example usage.";

    
    /**
     * The regular expression to use to match.
     */
    {
        setRegex(Pattern.compile("(.*)"));
    }
    public Pattern getRegex() {
        return (Pattern) kp.get("regex");
    }
    public void setRegex(Pattern regex) {
        kp.put("regex",regex);
    }
    
    /**
     * The format string to use when a match is found.
     */
    {
        setFormat("$1");
    }
    public String getFormat() {
        return (String) kp.get("format");
    }
    public void setFormat(String format) {
        kp.put("format",format);
    }

    public RegexRule() {
    }
    

    public String canonicalize(String url) {
        Pattern pattern = getRegex();
        Matcher matcher = pattern.matcher(url);
        if (!matcher.matches()) {
            return url;
        }
        StringBuffer buffer = new StringBuffer(url.length() * 2);
        matcher.appendReplacement(buffer,getFormat());
        return buffer.toString();
    }
}