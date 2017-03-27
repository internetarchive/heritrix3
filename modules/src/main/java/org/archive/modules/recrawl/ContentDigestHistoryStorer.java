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
package org.archive.modules.recrawl;

import org.archive.modules.CrawlURI;
import org.archive.modules.Processor;
import org.springframework.beans.factory.annotation.Autowired;

public class ContentDigestHistoryStorer extends Processor {
    
    protected AbstractContentDigestHistory contentDigestHistory;
    @Autowired
    public void setContentDigestHistory(
            AbstractContentDigestHistory contentDigestHistory) {
        this.contentDigestHistory = contentDigestHistory;
    }

    @Override
    protected boolean shouldProcess(CrawlURI uri) {
        return uri.getContentDigest() != null && uri.getContentLength() > 0
                && uri.hasContentDigestHistory()
                && !uri.getContentDigestHistory().isEmpty();
    }

    @Override
    protected void innerProcess(CrawlURI curi) throws InterruptedException {
        contentDigestHistory.store(curi);
    }
}