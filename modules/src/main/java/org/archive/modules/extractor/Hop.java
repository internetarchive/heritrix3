/* Copyright (C) 2006 Internet Archive.
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
 *
 * Hop.java
 * Created on October 5, 2006
 *
 * $Header$
 */
package org.archive.modules.extractor;


/**
 * The kind of "hop" from one URI to another.  Each hop type can be 
 * represented by a single character; strings of these characters can 
 * appear in logs.  Eg, "LLLX" means that a URI was three normal links from
 * a seed, and then one speculative link.
 * 
 * @author pjack
 */
public enum Hop {

    /** Navigation links, like A/@HREF. */
    NAVLINK('L'),
    
    /** Implied prerequisite links, like dns or robots. */
    PREREQ('P'),
    
    /** Embedded links necessary to render the page, like IMG/@SRC. */
    EMBED('E'),
    
    /** 
     * Speculative/aggressively extracted links, perhaps embed or nav, 
     * as in javascript.  
     */
    SPECULATIVE('X'),
    
    /** 
     * Referral/redirect links, like header 'Location:' on a 301/302 response. 
     */
    REFER('R');

    
    /** The hop character for logs. */
    private char hopChar;
    
    
    /**
     * Constructor.
     * 
     * @param hopChar  the hop character for logs
     */
    private Hop(char hopChar) {
        this.hopChar = hopChar;
    }

    
    /**
     * Returns a hop character suitable for display in logs.
     * 
     * @return   the hop character
     */
    public char getHopChar() {
        return hopChar;
    }
}
