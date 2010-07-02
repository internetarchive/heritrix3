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

package org.archive.util.iterator;

import java.util.Iterator;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class providing an Iterator interface over line-oriented
 * text input. By providing regexs indicating lines to ignore
 * (such as pure whitespace or comments), lines to consider input, and
 * what to return from the input lines (such as a whitespace-trimmed
 * non-whitespace token with optional trailing comment), this can
 * be configured to handle a number of formats. 
 * 
 * The public static members provide pattern configurations that will
 * be helpful in a wide variety of contexts. 
 * 
 * @author gojomo
 */
public class RegexLineIterator 
extends TransformingIteratorWrapper<String,String> {
    private static final Logger logger =
        Logger.getLogger(RegexLineIterator.class.getName());

    public static final String COMMENT_LINE = "\\s*(#.*)?";
    public static final String NONWHITESPACE_ENTRY_TRAILING_COMMENT = 
        "^[\\s\ufeff]*(\\S+)\\s*(#.*)?$";
    public static final String TRIMMED_ENTRY_TRAILING_COMMENT = 
        "^\\s*([^#]+?)\\s*(#.*)?$";

    public static final String ENTRY = "$1";

    protected Matcher ignoreLine = null;
    protected Matcher extractLine = null;
    protected String outputTemplate = null;


    public RegexLineIterator(Iterator<String> inner, String ignore, 
            String extract, String replace) {
        this.inner = inner;
        ignoreLine = Pattern.compile(ignore).matcher("");
        extractLine = Pattern.compile(extract).matcher("");
        outputTemplate = replace;
    }

    /**
     * Loads next item into lookahead spot, if available. Skips
     * lines matching ignoreLine; extracts desired portion of
     * lines matching extractLine; informationally reports any
     * lines matching neither. 
     * 
     * @return whether any item was loaded into next field
     */
    protected String transform(String line) {
        ignoreLine.reset(line);
        if(ignoreLine.matches()) {
            return null; 
        }
        extractLine.reset(line);
        if(extractLine.matches()) {
            StringBuffer output = new StringBuffer();
            // TODO: consider if a loop that find()s all is more 
            // generally useful here
            extractLine.appendReplacement(output,outputTemplate);
            return output.toString();
        }
        // no match; possibly error
        logger.warning("line not extracted nor no-op: "+line);
        return null;
    }
}
