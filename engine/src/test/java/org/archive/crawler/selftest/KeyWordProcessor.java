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

package org.archive.crawler.selftest;

import java.util.regex.Pattern;

import org.archive.io.ReplayCharSequence;
import org.archive.modules.CrawlURI;
import org.archive.modules.Processor;


/**
 * An example analysis module that prioritizes outlinks of URIs that contain
 * a certain keyword over the outlinks of URIs that do not.
 * 
 * <p>This is just a proof-of-concept; it isn't appropriate for actual 
 * production crawls, and so it lives with the test code.  This module has 
 * the following limitations:
 * 
 * <ol>
 * <li>It doesn't parse HTML content; so trying to match a keyword of "body"
 * would match.</li>
 * <li>It doesn't do any language analysis (eg, "political" if "politics" is 
 * the specified keyword).</li>
 * <li>It can't match more than one keyword.</li>
 * <li>It doesn't consider the number of times the keyword appears.</li>
 * </ol>
 * 
 * And so on.  However, this module does provide a simple example of how to
 * modify precedence values of a URI's links based on that URI's content.
 * 
 * NOTE: This processor may open a ReplayCharSequence from the 
 * CrawlURI's Recorder, without closing that ReplayCharSequence, to allow
 * reuse by later processors in sequence. In the usual (Heritrix) case, a 
 * call after all processing to the Recorder's endReplays() method ensures
 * timely close of any reused ReplayCharSequences. Reuse of this processor
 * elsewhere should ensure a similar cleanup call to Recorder.endReplays()
 * occurs. 
 * 
 * @author pjack
 */
public class KeyWordProcessor extends Processor {
    @SuppressWarnings("unused")
    private static final long serialVersionUID = 1L;
    /**
     * Regular expression used to detect the presence of a keyword.
     */
    Pattern pattern = Pattern.compile("\\bkeyword\\b");
    public Pattern getPattern() {
        return this.pattern;
    }
    public void setPattern(Pattern pattern) {
        this.pattern = pattern; 
    }

    /**
     * Precedence value to assign to discovered links of URIs that match
     * the pattern.
     */
    int foundPrecedence = 1; 
    public int getFoundPrecedence() {
        return this.foundPrecedence;
    }
    public void setFoundPrecedence(int prec) {
        this.foundPrecedence = prec; 
    }

    /**
     * Precedence value to assign to discovered links of URIs that do not
     * match the pattern.
     */
    int notFoundPrecedence = 10; 
    public int getNotFoundPrecedence() {
        return this.notFoundPrecedence;
    }
    public void setNotFoundPrecedence(int prec) {
        this.notFoundPrecedence = prec; 
    }

    @Override
    protected void innerProcess(CrawlURI curi) throws InterruptedException {
        try {
            CrawlURI viaUri = curi.getFullVia(); 
            if(!viaUri.getData().containsKey("keywordHit")) {
                ReplayCharSequence seq = viaUri.getRecorder().getContentReplayCharSequence();
                viaUri.getData().put("keywordHit", getPattern().matcher(seq).find());
            }
            boolean keywordHit = (Boolean) viaUri.getData().get("keywordHit");
            int precedence = keywordHit ? getFoundPrecedence() : getNotFoundPrecedence(); 
            curi.setPrecedence(precedence);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected boolean shouldProcess(CrawlURI uri) {
        if (!uri.getFullVia().getContentType().equals("text/html")) {
            return false;
        }
        return uri instanceof CrawlURI;
    }
}
