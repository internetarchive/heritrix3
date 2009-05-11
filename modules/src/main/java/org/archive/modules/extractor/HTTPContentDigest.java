/* HTTPContentDigest
 * 
 * $Id$
 * 
 * Created on 5.1.2005
 *
 * Copyright (C) 2005 Kristinn Sigur?sson
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
package org.archive.modules.extractor;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import org.archive.io.ReplayCharSequence;
import org.archive.modules.Processor;
import org.archive.modules.ProcessorURI;
import org.archive.util.TextUtils;

/**
 * A processor for calculating custum HTTP content digests in place of the 
 * default (if any) computed by the HTTP fetcher processors.
 * <p>
 * This processor allows the user to specify a regular expression called 
 * <i>strip-reg-expr<i>. Any segment of a document (text only, binary files will
 * be skipped) that matches this regular expression will by rewritten with 
 * the blank character (character 32 in the ANSI character set) <b> for the 
 * purpose of the digest</b> this has no effect on the document for subsequent 
 * processing or archiving.
 * <p>
 * NOTE: Content digest only accounts for the document body, not headers.
 * <p>
 * The operator will also be able to specify a maximum length for documents 
 * being evaluated by this processors. Documents exceeding that length will be 
 * ignored.
 * <p>
 * To further discriminate by file type or URL, an operator should use the 
 * override and refinement options. 
 * <p>
 * It is generally recommended that this recalculation only be performed when 
 * absolutely needed (because of stripping data that changes automatically each 
 * time the URL is fetched) as this is an expensive operation.
 *
 * @author Kristinn Sigurdsson
 */
public class HTTPContentDigest extends Processor {

    private static final long serialVersionUID = 3L;

    private static Logger logger =
        Logger.getLogger(HTTPContentDigest.class.getName());

    
    /**
     * A regular expression that matches those portions of downloaded documents
     * that need to be ignored when calculating the content digest. Segments
     * matching this expression will be rewritten with the blank character for
     * the content digest.
     */
    {
        setStripRegex(null);
    }
    public Pattern getStripRegex() {
        return (Pattern) kp.get("stripRegex");
    }
    public void setStripRegex(Pattern regex) {
        kp.put("stripRegex",regex);
    }

    /** Maximum file size for - longer files will be ignored. -1 = unlimited*/
    {
        setMaxSizeToDigest(1*1024*1024L); // 1MB
    }
    public long getMaxSizeToDigest() {
        return (Long) kp.get("maxSizeToDigest");
    }
    public void setMaxSizeToDigest(long threshold) {
        kp.put("maxSizeToDigest",threshold);
    }
    
    private static final String SHA1 = "SHA1";

    
    /**
     * Constructor.
     */
    public HTTPContentDigest() {
    }

    
    protected boolean shouldProcess(ProcessorURI uri) {
        if (!uri.getContentType().startsWith("text")) {
            return false;
        }
        
        long maxSize = getMaxSizeToDigest();
        if ((maxSize > - 1) && (maxSize < uri.getContentSize())) {
            return false;
        }
        
        return true;
    }

    protected void innerProcess(ProcessorURI curi) throws InterruptedException {
        // Ok, if we got this far we need to calculate the content digest. 
        // Get the regexpr
        Pattern regexpr = getStripRegex();
        
        // Get a replay of the document character seq.
        ReplayCharSequence cs = null;
        
        try {
           cs = curi.getRecorder().getReplayCharSequence();
        } catch (Exception e) {
            curi.getNonFatalFailures().add(e);
            logger.warning("Failed get of replay char sequence " +
                curi.toString() + " " + e.getMessage() + " " +
                Thread.currentThread().getName());
            return; // Can't proceed if this happens.
        }
        
        // Create a MessageDigest 
        MessageDigest digest = null;
        
        // We have a ReplayCharSequence open.  Wrap all in finally so we
        // for sure close it before we leave.
        try {
            try {
                digest = MessageDigest.getInstance(SHA1);
            } catch (NoSuchAlgorithmException e1) {
                e1.printStackTrace();
                return;
            }

            digest.reset();

            String s = null;

            if (regexpr != null) {
                s = cs.toString();
            } else {
                // Process the document
                Matcher m = regexpr.matcher(cs);
                s = m.replaceAll(" ");
            }
            digest.update(s.getBytes());

            // Get the new digest value
            byte[] newDigestValue = digest.digest();

            // Log if needed.
//            if (logger.isLoggable(Level.FINEST)) {
//                logger.finest("Recalculated content digest for "
//                        + curi.toString() + " old: "
//                        + Base32.encode((byte[]) curi.getContentDigest())
//                        + ", new: " + Base32.encode(newDigestValue));
//            }
            // Save new digest value
            curi.setContentDigest(SHA1, newDigestValue);
        } finally {
            if (cs != null) {
                try {
                    cs.close();
                } catch (IOException ioe) {
                    logger.warning(TextUtils.exceptionToString(
                            "Failed close of ReplayCharSequence.", ioe));
                }
            }
        }
    }
}