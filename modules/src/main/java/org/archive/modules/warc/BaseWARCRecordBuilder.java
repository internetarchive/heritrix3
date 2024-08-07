package org.archive.modules.warc;

import org.archive.io.warc.WARCRecordInfo;
import org.archive.modules.CrawlURI;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;

public abstract class BaseWARCRecordBuilder implements WARCRecordBuilder {
    public static URI generateRecordID() {
        try {
            return new URI("urn:uuid:" + UUID.randomUUID());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e); // impossible 
        }
    }
    public void postWrite(WARCRecordInfo recordInfo, CrawlURI curi) {
        return;
    }
}
