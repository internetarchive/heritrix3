/* LineReadingIterator
*
* $Id$
*
* Created on Jul 27, 2004
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
package org.archive.util.iterator;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Utility class providing an Iterator interface over line-oriented
 * text input, as a thin wrapper over a BufferedReader.
 * 
 * @author gojomo
 */
public class LineReadingIterator extends LookaheadIterator<String> {
    private static final Logger logger =
        Logger.getLogger(LineReadingIterator.class.getName());

    protected BufferedReader reader = null;

    public LineReadingIterator(BufferedReader r) {
        reader = r;
    }

    /**
     * Loads next line into lookahead spot
     * 
     * @return whether any item was loaded into next field
     */
    protected boolean lookahead() {
        try {
            next = this.reader.readLine();
            if(next == null) {
                // TODO: make this close-on-exhaust optional?
                reader.close();
            }
            return (next!=null);
        } catch (IOException e) {
            logger.warning(e.toString());
            return false;
        }
    }
}
