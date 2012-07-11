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
package org.archive.modules.extractor;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;
import java.util.regex.Matcher;

import org.apache.commons.lang.StringUtils;
import org.archive.io.ReplayCharSequence;
import org.archive.modules.CrawlURI;
import org.archive.modules.Processor;
import org.archive.util.TextUtils;

/**
 * A processor for calculating custom HTTP content digests in place of the 
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
 * NOTE: This processor may open a ReplayCharSequence from the 
 * CrawlURI's Recorder, without closing that ReplayCharSequence, to allow
 * reuse by later processors in sequence. In the usual (Heritrix) case, a 
 * call after all processing to the Recorder's endReplays() method ensures
 * timely close of any reused ReplayCharSequences. Reuse of this processor
 * elsewhere should ensure a similar cleanup call to Recorder.endReplays()
 * occurs. 
 * 
 * @author Kristinn Sigurdsson
 */
public class HTTPContentDigest extends Processor {

    @SuppressWarnings("unused")
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
        setStripRegex("");
    }
    public String getStripRegex() {
        return (String) kp.get("stripRegex");
    }
    public void setStripRegex(String regex) {
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

    
    protected boolean shouldProcess(CrawlURI uri) {
        if (!uri.getContentType().startsWith("text")) {
            return false;
        }
        
        long maxSize = getMaxSizeToDigest();
        if ((maxSize > - 1) && (maxSize < uri.getContentSize())) {
            return false;
        }
        
        return true;
    }

    protected void innerProcess(CrawlURI curi) throws InterruptedException {
        // Ok, if we got this far we need to calculate the content digest. 
        // Get the regex
        String regex = getStripRegex();
        
        // Get a replay of the document character seq.
        ReplayCharSequence cs = null;
        try {
           cs = curi.getRecorder().getContentReplayCharSequence();
           // Create a MessageDigest 
           MessageDigest digest = null;
           try {
               digest = MessageDigest.getInstance(SHA1);
           } catch (NoSuchAlgorithmException e1) {
               e1.printStackTrace();
               return;
           }

           digest.reset();

           String s = null;

           if (StringUtils.isEmpty(regex)) {
               s = cs.toString();
           } else {
               // Process the document
               Matcher m = TextUtils.getMatcher(regex, cs);
               s = m.replaceAll(" ");
               TextUtils.recycleMatcher(m);
           }
           digest.update(s.getBytes());
           // Get the new digest value
           byte[] newDigestValue = digest.digest();
           // Save new digest value
           curi.setContentDigest(SHA1, newDigestValue);
           
        } catch (Exception e) {
            curi.getNonFatalFailures().add(e);
            logger.warning("Failed get of replay char sequence " +
                curi.toString() + " " + e.getMessage() + " " +
                Thread.currentThread().getName());
            return; // Can't proceed if this happens.
        }
    }
}