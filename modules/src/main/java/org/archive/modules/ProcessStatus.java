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
 * ProcessStatus.java
 * Created on December 13, 2006
 *
 * $Header$
 */
package org.archive.modules;


/**
 * Returned by a Processor's process method to indicate the status of the
 * process.  
 * 
 * @author pjack
 */
public enum ProcessStatus {

    /**
     * The URI was processed normally, and no special action needs to be taken
     * by the framework.
     */
    PROCEED,

    /**
     * The Processor believes that the ProcessorURI is invalid, or otherwise
     * incapable of further processing at this time. The framework should not
     * send the URI to any more processors, but should instead perform any
     * necessary cleanup or post-processing on the URI.
     */
    FINISH,

    /**
     * The Processor has specified the next processor for the URI.  The 
     * framework should send the URI to that processor instead of the reguarly
     * scheduled next processor.
     */
    JUMP,
    
    /**
     * The Processor believes that futher processing of <i>any</i> ProcessorURIs is 
     * impossible at this point.  For instance, if a Processor detects that 
     * a network interface is unavailable, or that a disk is full.
     */
    STUCK

}
