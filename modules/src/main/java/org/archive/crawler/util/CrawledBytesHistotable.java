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

import java.util.Map;

import org.archive.io.warc.WARCWriter;
import org.archive.modules.CoreAttributeConstants;
import org.archive.modules.CrawlURI;
import org.archive.modules.revisit.IdenticalPayloadDigestRevisit;
import org.archive.modules.revisit.ServerNotModifiedRevisit;
import org.archive.util.ArchiveUtils;
import org.archive.util.Histotable;

public class CrawledBytesHistotable extends Histotable<String> 
implements CoreAttributeConstants {
    private static final long serialVersionUID = 7923431123239026213L;
    
    public static final String NOTMODIFIED = "notModified";
    public static final String DUPLICATE = "dupByHash";
    public static final String OTHERDUPLICATE = "otherDup";
    public static final String NOVEL = "novel";
    public static final String NOTMODIFIEDCOUNT = "notModifiedCount";
    public static final String DUPLICATECOUNT = "dupByHashCount";
    public static final String OTHERDUPLICATECOUNT = "otherDupCount";
    public static final String NOVELCOUNT = "novelCount";

    // total size of warc response and resource record payloads (includes http
    // headers, does not include warc record headers)
    public static final String WARC_NOVEL_CONTENT_BYTES = "warcNovelContentBytes";
    public static final String WARC_NOVEL_URLS = "warcNovelUrls";

    public CrawledBytesHistotable() {
        super();
    }

    @SuppressWarnings("unchecked")
    public void accumulate(CrawlURI curi) {
        if (curi.getRevisitProfile() instanceof ServerNotModifiedRevisit) {
            tally(NOTMODIFIED, curi.getContentSize());
            tally(NOTMODIFIEDCOUNT,1);
        } else if (curi.getRevisitProfile() instanceof IdenticalPayloadDigestRevisit) {
            tally(DUPLICATE,curi.getContentSize());
            tally(DUPLICATECOUNT,1);
        } else if (curi.getRevisitProfile() != null) {
            tally(OTHERDUPLICATE, curi.getContentSize());
            tally(OTHERDUPLICATECOUNT, 1);
        } else {
            tally(NOVEL,curi.getContentSize());
            tally(NOVELCOUNT,1);
        }
        Map<String,Map<String,Long>> warcStats = (Map<String,Map<String,Long>>) curi.getData().get(A_WARC_STATS);
        if (warcStats != null) {
            tally(WARC_NOVEL_CONTENT_BYTES,
                    WARCWriter.getStat(warcStats, "response", "contentBytes")
                    + WARCWriter.getStat(warcStats, "resource", "contentBytes"));
            tally(WARC_NOVEL_URLS,
                    WARCWriter.getStat(warcStats, "response", "numRecords")
                    + WARCWriter.getStat(warcStats, "resource", "numRecords"));
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
        if(get(OTHERDUPLICATE)!=null) {
            sb.append(", ");
            sb.append(ArchiveUtils.formatBytesForDisplay(get(OTHERDUPLICATE)));
            sb.append(" ");
            sb.append(OTHERDUPLICATECOUNT);
        }
        sb.append(")");
        return sb.toString();
    }
    
    public long getTotalBytes() {
        return get(NOVEL) + get(DUPLICATE) + get(NOTMODIFIED) + get(OTHERDUPLICATE);
    }
    
    public long getTotalUrls() {
        return get(NOVELCOUNT) + get(DUPLICATECOUNT) + get(NOTMODIFIEDCOUNT) + get(OTHERDUPLICATECOUNT);
    }
}
