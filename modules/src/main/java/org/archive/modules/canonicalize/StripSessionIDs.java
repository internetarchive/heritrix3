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
    private static final String BASE_PATTERN = 
        "(?i)^(.+)(?:(?:(?:jsessionid)|(?:phpsessid))=[0-9a-zA-Z]{32})(?:&(.*))?$";
    
    /**
     * Example: sid=9682993c8daa2c5497996114facdc805. 
     * 'sid=' can be tricky but all sid= followed by 32 byte string
     * so far seen have been session ids.  Sid is a 32 byte string
     * like the BASE_PATTERN only 'sid' is the tail of 'phpsessid'
     * so have to have it run after the phpsessid elimination.
     */
    private static final String SID_PATTERN =
        "(?i)^(.+)(?:sid=[0-9a-zA-Z]{32})(?:&(.*))?$";
    
    /**
     * Example:ASPSESSIONIDAQBSDSRT=EOHBLBDDPFCLHKPGGKLILNAM.
     */
    private static final String ASPSESSION_PATTERN =
        "(?i)^(.+)(?:ASPSESSIONID[a-zA-Z]{8}=[a-zA-Z]{24})(?:&(.*))?$";

    public StripSessionIDs() {
    }

    public String canonicalize(String url) {
        url = doStripRegexMatch(url, BASE_PATTERN);
        url = doStripRegexMatch(url, SID_PATTERN);
        url = doStripRegexMatch(url, ASPSESSION_PATTERN);
        return url;
    }
}