/* ARCLocation
 * 
 * $Id$
 * 
 * Created on April 27, 2005.
 * 
 * Copyright (C) 2005 Internet Archive.
 * 
 * This file is part of the Heritrix web crawler (crawler.archive.org).
 *
 * Heritrix is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or any
 * later version.
 * 
 * The archive-access tools are distributed in the hope that they will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser
 * Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser Public License along with
 * the archive-access tools; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package org.archive.io.arc;

/**
 * Datastructure to hold ARC record location.
 * Used by wayback machine.
 * @author stack
 */
public interface ARCLocation {
    /**
     * @return Returns the ARC filename.  Can be full path to ARC, URL to an
     * ARC or just the portion of an ARC name that is unique to a collection.
     */
    public String getName();

    /**
     * @return Returns the offset into the ARC.
     */
    public long getOffset();
}
