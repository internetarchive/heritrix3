/* $Id$
 * 
 * Created on September 1st, 2006
 *
 * Copyright (C) 2006 Internet Archive.
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
 * Strip cold fusion session ids.
 * @author stack
 * @version $Date$, $Revision$
 */
public class StripSessionCFIDs
extends BaseRule {

    private static final long serialVersionUID = 3L;

    private static final String REGEX = "^(.+)" +
        "(?:cfid=[^&]+&cftoken=[^&]+(?:jsession=[^&]+)?)(?:&(.*))?$";
    
//    private static final String DESCRIPTION = "Strip ColdFusion session IDs. " +
//        "Use this rule to remove sessionids that look like the following: " +
//        "CFID=12412453&CFTOKEN=15501799 or " +
//        "CFID=3304324&CFTOKEN=57491900&jsessionid=a63098d96360$B0$D9$A " +
//        "using the following case-insensitive regex: " + REGEX;
        
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
    private static final Pattern COLDFUSION_PATTERN =
        Pattern.compile(REGEX, Pattern.CASE_INSENSITIVE);
    

    public StripSessionCFIDs() {
    }

    public String canonicalize(String url) {
        return doStripRegexMatch(url, COLDFUSION_PATTERN.matcher(url));
    }

}