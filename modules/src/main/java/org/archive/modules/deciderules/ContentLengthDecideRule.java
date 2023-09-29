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
package org.archive.modules.deciderules;

import org.archive.modules.CrawlURI;

public class ContentLengthDecideRule extends DecideRule {

    private static final long serialVersionUID = 3L;


    /**
     * Content-length threshold.  The rule returns ACCEPT if the content-length
     * is less than this threshold, or REJECT otherwise.  The default is
     * 2^63, meaning any document will be accepted.
     */
    {
        setContentLengthThreshold(Long.MAX_VALUE);
    }
    public long getContentLengthThreshold() {
        return (Long) kp.get("contentLengthThreshold");
    }
    public void setContentLengthThreshold(long threshold) {
        kp.put("contentLengthThreshold",threshold);
    }

    /**
     * Usual constructor. 
     */
    public ContentLengthDecideRule() {
    }
    
    
    protected DecideResult innerDecide(CrawlURI uri) {
        if (uri.getContentLength() < getContentLengthThreshold()) {
            return DecideResult.ACCEPT;
        }
        return DecideResult.REJECT;
    }

}