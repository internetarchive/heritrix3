/* RegexRule
 * 
 * Created on Oct 6, 2004
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