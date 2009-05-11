/* 
 * Copyright (C) 2007 Internet Archive.
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
 * LoudObjectOutputStream.java
 *
 * Created on Mar 10, 2007
 *
 * $Id:$
 */

package org.archive.io;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * ObjectOutputStream that logs class name of each object that is written
 * to the stream.  Useful for tracking down sources of NotSerializableException. 
 * 
 * @author pjack
 *
 */
public class LoudObjectOutputStream extends ObjectOutputStream {

    
    final private static Logger LOGGER = Logger.getLogger(
            LoudObjectOutputStream.class.getName()); 
    
    // Only log each class name once
    private Set<String> alreadyLogged = new HashSet<String>();
    
    public LoudObjectOutputStream(OutputStream out) throws IOException {
        super(out);
        this.enableReplaceObject(true);
    }


    @Override
    protected Object replaceObject(Object obj) throws IOException {
        if (obj != null) {
            String name = obj.getClass().getName();
            if (alreadyLogged.add(name)) {
                LOGGER.info("WROTE: " + name);
            }
        }
        return obj;
    }    
    

}
