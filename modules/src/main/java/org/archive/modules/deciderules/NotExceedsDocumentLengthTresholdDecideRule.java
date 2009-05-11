/* $Id: NotExceedsDocumentLengthTresholdDecideRule.java 4649 2006-09-25 17:16:55Z paul_jack $
 * 
 * Created on 28.8.2006
 *
 * Copyright (C) 2006 Olaf Freyer
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
package org.archive.modules.deciderules;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.httpclient.HttpMethod;
import org.archive.modules.ProcessorURI;

/**
 * Applies configured decision for URIs with content length less than a given
 * treshold. Either examines HTTP header content length or actual downloaded
 * content length and returns false for documents exceeding a given length
 * treshold.
 */
public class NotExceedsDocumentLengthTresholdDecideRule
extends PredicatedDecideRule {
	

    private static final long serialVersionUID = -8774160016195991876L;

    private static final Logger logger = Logger.
    	getLogger(NotExceedsDocumentLengthTresholdDecideRule.class.getName());
    

    /**
     * Shall this rule be used as a midfetch rule? If true, this rule will
     * determine content length based on HTTP header information, otherwise
     * the size of the already downloaded content will be used.
     */
    {
        setUseHeaderLength(true);
    }
    public boolean getUseHeaderLength() {
        return (Boolean) kp.get("useHeaderLength");
    }
    public void setUseHeaderLength(boolean useHeaderLength) {
        kp.put("useHeaderLength",useHeaderLength);
    }
    
    /**
     * Max content-length this filter will allow to pass through. If -1, 
     * then no limit.
     */
    {
        setContentLengthThreshold(-1L);
    }
    public long getContentLengthThreshold() {
        return (Long) kp.get("contentLengthThreshold");
    }
    public void setContentLengthThreshold(long threshold) {
        kp.put("contentLengthThreshold",threshold);
    }

    // Header predictor state constants
    public static final int HEADER_PREDICTS_MISSING = -1;
	
    public NotExceedsDocumentLengthTresholdDecideRule() {
    }
    
    protected boolean evaluate(ProcessorURI curi) {
        int contentlength = HEADER_PREDICTS_MISSING;

        // filter used as midfetch filter
        if (getUseHeaderLength()) {

            if (curi.getHttpMethod() == null) {
                // Missing header info, let pass
                if (logger.isLoggable(Level.INFO)) {
                    logger.info("Error: Missing HttpMethod object in "
                            + "CrawlURI. " + curi.toString());
                }
                return false;
            }

            // Initially assume header info is missing
            HttpMethod method = curi.getHttpMethod();

            // get content-length
            String newContentlength = null;
            if (method.getResponseHeader("content-length") != null) {
                newContentlength = method.getResponseHeader("content-length")
                        .getValue();
            }

            if (newContentlength != null && newContentlength.length() > 0) {
                try {
                    contentlength = Integer.parseInt(newContentlength);
                } catch (NumberFormatException nfe) {
                    // Ignore.
                }
            }

            // If no document length was reported or format was wrong,
            // let pass
            if (contentlength == HEADER_PREDICTS_MISSING) {
                return false;
            }
        } else {
            contentlength = (int) curi.getContentSize();
        }
        
        return test(contentlength);
    }
    
    
    boolean test(int contentlength) {
        return contentlength < getContentLengthThreshold();        
    }
}