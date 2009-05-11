/* StripSessionIDs
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

import java.util.regex.Pattern;

/**
 * Strip known session ids.
 * @author stack
 * @version $Date$, $Revision$
 */
public class StripSessionIDs
extends BaseRule {

    private static final long serialVersionUID = 3L;

//    private static final String DESCRIPTION = "Strip known session IDs. " +
//        "Use this rule to remove all of a set of known session IDs." +
//        " For example, this rule will strip JSESSIONID and its value from" +
//        " 'http://archive.org/index.html?" +
//        "JSESSIONID=DDDSSE233232333355FFSXXXXDSDSDS'.  The resulting" +
//        " canonicalization returns 'http://archive.org/index.html'." +
//        " This rule strips JSESSIONID, ASPSESSIONID, PHPSESSID, and 'sid'" +
//        " session ids.";
    
    /**
     * Example: jsessionid=999A9EF028317A82AC83F0FDFE59385A.
     * Example: PHPSESSID=9682993c8daa2c5497996114facdc805.
     */
    private static final Pattern BASE_PATTERN = Pattern.compile("^(.+)" +
            "(?:(?:(?:jsessionid)|(?:phpsessid))=" +
                 "[0-9a-zA-Z]{32})(?:&(.*))?$",  Pattern.CASE_INSENSITIVE);
    
    /**
     * Example: sid=9682993c8daa2c5497996114facdc805. 
     * 'sid=' can be tricky but all sid= followed by 32 byte string
     * so far seen have been session ids.  Sid is a 32 byte string
     * like the BASE_PATTERN only 'sid' is the tail of 'phpsessid'
     * so have to have it run after the phpsessid elimination.
     */
    private static final Pattern SID_PATTERN =
        Pattern.compile("^(.+)" +
            "(?:sid=[0-9a-zA-Z]{32})(?:&(.*))?$", Pattern.CASE_INSENSITIVE);
    
    /**
     * Example:ASPSESSIONIDAQBSDSRT=EOHBLBDDPFCLHKPGGKLILNAM.
     */
    private static final Pattern ASPSESSION_PATTERN =
        Pattern.compile("^(.+)" +
            "(?:ASPSESSIONID[a-zA-Z]{8}=[a-zA-Z]{24})(?:&(.*))?$",
                Pattern.CASE_INSENSITIVE);

    public StripSessionIDs() {
    }

    public String canonicalize(String url) {
        url = doStripRegexMatch(url, BASE_PATTERN.matcher(url));
        url = doStripRegexMatch(url, SID_PATTERN.matcher(url));
        url = doStripRegexMatch(url, ASPSESSION_PATTERN.matcher(url));
        return url;
    }
}