/* Copyright (C) 2003 Internet Archive.
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
 * UriErrorFormatter.java
 * Created on Jul 7, 2003
 *
 * $Header$
 */
package org.archive.crawler.io;

import java.util.logging.Formatter;
import java.util.logging.LogRecord;

import org.archive.modules.CoreAttributeConstants;
import org.archive.net.UURI;
import org.archive.util.ArchiveUtils;

/**
 * Formatter for 'uri-errors.log', of URIs so malformed they could
 * not be instantiated.
 * 
 * @author gojomo
 *
 */
public class UriErrorFormatter extends Formatter implements CoreAttributeConstants {

    /* (non-Javadoc)
     * @see java.util.logging.Formatter#format(java.util.logging.LogRecord)
     */
    public String format(LogRecord lr) {
        UURI uuri = (UURI) lr.getParameters()[0];
        String problem = (String) lr.getParameters()[1];

        return ArchiveUtils.getLog17Date()
        + " "
        + ( (uuri ==null) ? "n/a" : uuri.toString() )
        + " \""
        + lr.getMessage()
        + "\" "
        + problem
        + "\n";
    }
}

