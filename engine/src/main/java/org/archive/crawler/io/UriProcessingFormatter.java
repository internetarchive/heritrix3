/* UriProcessingFormatter.java
 *
 * $Id$
 * 
 * Created on Jun 10, 2003
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
package org.archive.crawler.io;

import it.unimi.dsi.mg4j.util.MutableString;

import java.util.Collection;
import java.util.Iterator;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

import org.archive.crawler.datamodel.CoreAttributeConstants;
import org.archive.crawler.datamodel.CrawlURI;
import org.archive.util.ArchiveUtils;
import org.archive.util.MimetypeUtils;

/**
 * Formatter for 'crawl.log'. Expects completed CrawlURI as parameter.
 *
 * @author gojomo
 */
public class UriProcessingFormatter
extends Formatter implements CoreAttributeConstants {
    private final static String NA = "-";
    /**
     * Guess at line length (URIs are assumed avg. of 128 bytes).
     * Used to preallocated the buffer we accumulate the log line
     * in.  Hopefully we get it right most of the time and no need
     * to enlarge except in the rare case.
     */
    private final static int GUESS_AT_LOG_LENGTH =
        17 + 1 + 3 + 1 + 10 + 128 + + 1 + 10 + 1 + 128 + 1 + 10 + 1 + 3 +
        14 + 1 + 32 + 4 + 128 + 1;
    
    /**
     * Reuseable assembly buffer.
     */
    private final MutableString buffer =
        new MutableString(GUESS_AT_LOG_LENGTH);
    
    public String format(LogRecord lr) {
        CrawlURI curi = (CrawlURI)lr.getParameters()[0];
        String length = NA;
        String mime = null;
        if (curi.isHttpTransaction()) {
            if(curi.getContentLength() >= 0) {
                length = Long.toString(curi.getContentLength());
            } else if (curi.getContentSize() > 0) {
                length = Long.toString(curi.getContentSize());
            }
            mime = curi.getContentType();
        } else {
            if (curi.getContentSize() > 0) {
                length = Long.toString(curi.getContentSize());
            } 
            mime = curi.getContentType();
        }
        mime = MimetypeUtils.truncate(mime);

        long time = System.currentTimeMillis();
        String arcTimeAndDuration;
        if(curi.containsDataKey(A_FETCH_COMPLETED_TIME)) {
            long completedTime = curi.getFetchCompletedTime();
            long beganTime = curi.getFetchBeginTime();
            arcTimeAndDuration = ArchiveUtils.get17DigitDate(beganTime) + "+"
                    + Long.toString(completedTime - beganTime);
        } else {
            arcTimeAndDuration = NA;
        }

        String via = curi.flattenVia();
        
        String digest = curi.getContentDigestSchemeString();

        String sourceTag = curi.containsDataKey(A_SOURCE_TAG) 
                ? curi.getSourceTag()
                : null;
                
        this.buffer.length(0);
        this.buffer.append(ArchiveUtils.getLog17Date(time))
            .append(" ")
            .append(ArchiveUtils.padTo(curi.getFetchStatus(), 5))
            .append(" ")
            .append(ArchiveUtils.padTo(length, 10))
            .append(" ")
            .append(curi.getUURI().toString())
            .append(" ")
            .append(checkForNull(curi.getPathFromSeed()))
            .append(" ")
            .append(checkForNull(via))
            .append(" ")
            .append(mime)
            .append(" ")
            .append("#")
            // Pad threads to be 3 digits.  For Igor.
            .append(ArchiveUtils.padTo(
                Integer.toString(curi.getThreadNumber()), 3, '0'))
            .append(" ")
            .append(arcTimeAndDuration)
            .append(" ")
            .append(checkForNull(digest))
            .append(" ")
            .append(checkForNull(sourceTag))
            .append(" ");
        Collection<String> anno = curi.getAnnotations();
        if ((anno != null) && (anno.size() > 0)) {
        	Iterator<String> iter = anno.iterator();
            buffer.append(iter.next());
            while (iter.hasNext()) {
            	buffer.append(',');
            	buffer.append(iter.next());
            }
        }
            
        return buffer.append("\n").toString();
    }
    
    /**
     * @param str String to check.
     * @return Return passed string or <code>NA</code> if null.
     */
    protected String checkForNull(String str) {
        return (str == null || str.length() <= 0)? NA: str;
    }
}


