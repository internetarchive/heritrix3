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
package org.archive.crawler.io;

import java.util.Collection;
import java.util.Iterator;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

import org.archive.io.Preformatter;
import org.archive.modules.CoreAttributeConstants;
import org.archive.modules.CrawlURI;
import org.archive.util.ArchiveUtils;
import org.archive.util.MimetypeUtils;

/**
 * Formatter for 'crawl.log'. Expects completed CrawlURI as parameter.
 *
 * @author gojomo
 */
public class UriProcessingFormatter
extends Formatter implements Preformatter, CoreAttributeConstants {
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
     * Reusable assembly buffer.
     */
    protected final ThreadLocal<StringBuilder> bufLocal =
        new ThreadLocal<StringBuilder>() {
            @Override
            protected StringBuilder initialValue() {
                return new StringBuilder(GUESS_AT_LOG_LENGTH);
            }
    };
    
    protected final ThreadLocal<String> cachedFormat = new ThreadLocal<String>();
    protected boolean logExtraInfo; 
    
    public UriProcessingFormatter(boolean logExtraInfo) {
        this.logExtraInfo = logExtraInfo;
    }

    public String format(LogRecord lr) {
        if(cachedFormat.get()!=null) {
            return cachedFormat.get();
        }
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

        String via = curi.flattenVia();
        
        String digest = curi.getContentDigestSchemeString();

        String sourceTag = curi.containsDataKey(A_SOURCE_TAG) 
                ? curi.getSourceTag()
                : null;
             
        StringBuilder buffer = bufLocal.get();
        buffer.setLength(0);
        buffer.append(ArchiveUtils.getLog17Date(time))
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
            .append(" ");
        
        // arcTimeAndDuration
        if(curi.containsDataKey(A_FETCH_COMPLETED_TIME)) {
            long completedTime = curi.getFetchCompletedTime();
            long beganTime = curi.getFetchBeginTime();
            buffer.append(ArchiveUtils.get17DigitDate(beganTime))
                    .append("+")
                    .append(Long.toString(completedTime - beganTime));
        } else {
            buffer.append(NA);
        }
        
        buffer.append(" ")
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
        } else {
            buffer.append(NA);
        }
        
        if (logExtraInfo) {
            // XXX would we rather have "-" if info's empty?
            buffer.append(" ").append(curi.getExtraInfo());
        }
        
        buffer.append("\n");
        return buffer.toString(); 
    }

    /**
     * @param str String to check.
     * @return Return passed string or <code>NA</code> if null.
     */
    protected String checkForNull(String str) {
        return (str == null || str.length() <= 0)? NA: str;
    }

    @Override
    public void clear() {
        cachedFormat.set(null); 
    }

    @Override
    public void preformat(LogRecord record) {
        cachedFormat.set(format(record));
    }
}


