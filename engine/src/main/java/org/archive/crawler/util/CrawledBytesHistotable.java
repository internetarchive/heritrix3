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

package org.archive.crawler.util;

import org.apache.commons.httpclient.HttpStatus;
import org.archive.modules.CoreAttributeConstants;
import org.archive.modules.CrawlURI;
import org.archive.modules.deciderules.recrawl.IdenticalDigestDecideRule;
import org.archive.util.ArchiveUtils;
import org.archive.util.Histotable;

public class CrawledBytesHistotable extends Histotable<String> 
implements CoreAttributeConstants {
    private static final long serialVersionUID = 7923431123239026213L;
    
    public static final String NOTMODIFIED = "notModified";
    public static final String DUPLICATE = "dupByHash";
    public static final String NOVEL = "novel";
    public static final String NOTMODIFIEDCOUNT = "notModifiedCount";
    public static final String DUPLICATECOUNT = "dupByHashCount";
    public static final String NOVELCOUNT = "novelCount";
    
    public CrawledBytesHistotable() {
        super();
    }

    public void accumulate(CrawlURI curi) {
        if(curi.getFetchStatus()==HttpStatus.SC_NOT_MODIFIED) {
            tally(NOTMODIFIED, curi.getContentSize());
            tally(NOTMODIFIEDCOUNT,1);
        } else if (IdenticalDigestDecideRule.hasIdenticalDigest(curi)) {
            tally(DUPLICATE,curi.getContentSize());
            tally(DUPLICATECOUNT,1);
        } else {
            tally(NOVEL,curi.getContentSize());
            tally(NOVELCOUNT,1);
        }
    }
    
    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append(ArchiveUtils.formatBytesForDisplay(getTotalBytes()));
        sb.append(" crawled (");
        sb.append(ArchiveUtils.formatBytesForDisplay(get(NOVEL)));
        sb.append(" novel");
        if(get(DUPLICATE)!=null) {
            sb.append(", ");
            sb.append(ArchiveUtils.formatBytesForDisplay(get(DUPLICATE)));
            sb.append(" ");
            sb.append(DUPLICATE);
        }
        if(get(NOTMODIFIED)!=null) {
            sb.append(", ");
            sb.append(ArchiveUtils.formatBytesForDisplay(get(NOTMODIFIED)));
            sb.append(" ");
            sb.append(NOTMODIFIED);
        }
        sb.append(")");
        return sb.toString();
    }
    
    public long getTotalBytes() {
        return get(NOVEL) + get(DUPLICATE) + get(NOTMODIFIED);
    }
    
    public long getTotalUrls() {
        return get(NOVELCOUNT) + get(DUPLICATECOUNT) + get(NOTMODIFIEDCOUNT);
    }
}
