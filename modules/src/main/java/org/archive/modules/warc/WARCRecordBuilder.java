package org.archive.modules.warc;

import java.io.IOException;
import java.net.URI;

import org.archive.io.warc.WARCRecordInfo;
import org.archive.modules.CrawlURI;

public interface WARCRecordBuilder {

    boolean shouldBuildRecord(CrawlURI curi);

    WARCRecordInfo buildRecord(CrawlURI curi, URI concurrentTo)
            throws IOException;

}