/* LongWrapper
 *
 * $Id$
 *
 * Copyright (C) 2003 Internet Archive.
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
package org.archive.util;

import java.io.Serializable;

/**
 * Wraps a long. Used in place of Long so that when we extract it from a
 * Collection we can modify the long value without creating a new object.
 * This way we don't have to rewrite the Collection to update one of the
 * stored longs.
 * @author Kristinn Sigurdsson
 */
public class LongWrapper implements Serializable {

    private static final long serialVersionUID = -6537350490019555280L;

    public long longValue;
    public LongWrapper(long initial){
        this.longValue = initial;
    }
    public long getLongValue() {
        return longValue;
    }
}
