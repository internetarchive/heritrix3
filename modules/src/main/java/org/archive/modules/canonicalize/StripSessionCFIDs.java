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
 * Strip cold fusion session ids.
 * @author stack
 * @version $Date$, $Revision$
 */
public class StripSessionCFIDs
extends BaseRule {

    private static final long serialVersionUID = 3L;

    /**
     * Examples:
     * <pre>
     * Examples:
     * boo?CFID=1169580&CFTOKEN=48630702&dtstamp=22%2F08%2F2006%7C06%3A58%3A11
     * boo?CFID=12412453&CFTOKEN=15501799&dt=19_08_2006_22_39_28
     * boo?CFID=14475712&CFTOKEN=2D89F5AF-3048-2957-DA4EE4B6B13661AB&r=468710288378&m=forgotten
     * boo?CFID=16603925&CFTOKEN=2AE13EEE-3048-85B0-56CEDAAB0ACA44B8&r=501652357733&l1=home
     * boo?CFID=3304324&CFTOKEN=57491900&jsessionid=a63098d96360$B0$D9$A 
     * </pre>
     */
    private static final String REGEX = "^(.+)" +
        "(?i)(?:cfid=[^&]+&cftoken=[^&]+(?:jsession=[^&]+)?)(?:&(.*))?$";
    
//    private static final String DESCRIPTION = "Strip ColdFusion session IDs. " +
//        "Use this rule to remove sessionids that look like the following: " +
//        "CFID=12412453&CFTOKEN=15501799 or " +
//        "CFID=3304324&CFTOKEN=57491900&jsessionid=a63098d96360$B0$D9$A " +
//        "using the following case-insensitive regex: " + REGEX;
        
    public StripSessionCFIDs() {
    }

    public String canonicalize(String url) {
        return doStripRegexMatch(url, REGEX);
    }

}