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

import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_ORIGINAL_DATE;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_ORIGINAL_URL;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_WARC_RECORD_ID;

import org.archive.modules.CrawlURI;
import org.archive.modules.Processor;
import org.archive.modules.revisit.IdenticalPayloadDigestRevisit;
import org.springframework.beans.factory.annotation.Autowired;

public class ContentDigestHistoryLoader extends Processor {
    
    protected AbstractContentDigestHistory contentDigestHistory;
    @Autowired
    public void setContentDigestHistory(
            AbstractContentDigestHistory contentDigestHistory) {
        this.contentDigestHistory = contentDigestHistory;
    }

    @Override
    protected boolean shouldProcess(CrawlURI uri) {
        return uri.getContentDigest() != null && uri.getContentLength() > 0;
    }

    @Override
    protected void innerProcess(CrawlURI curi) throws InterruptedException {
        contentDigestHistory.load(curi);

        if (!curi.getContentDigestHistory().isEmpty()) {
        	IdenticalPayloadDigestRevisit revisit = 
        			new IdenticalPayloadDigestRevisit(curi.getContentDigestSchemeString());
			revisit.setRefersToDate((String)curi.getContentDigestHistory().get(A_ORIGINAL_DATE));
			revisit.setRefersToTargetURI((String)curi.getContentDigestHistory().get(A_ORIGINAL_URL));
			String warcRecordId= (String)curi.getContentDigestHistory().get(A_WARC_RECORD_ID);
			if (warcRecordId!=null) {
				revisit.setRefersToRecordID(warcRecordId);
			}
			curi.setRevisitProfile(revisit);
            curi.getAnnotations().add("duplicate:digest");
        }
    }
}
