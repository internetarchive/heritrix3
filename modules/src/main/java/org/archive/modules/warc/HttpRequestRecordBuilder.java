package org.archive.modules.warc;

import static org.archive.format.warc.WARCConstants.HEADER_KEY_CONCURRENT_TO;
import static org.archive.format.warc.WARCConstants.HTTP_REQUEST_MIMETYPE;

import java.io.IOException;
import java.net.URI;

import org.archive.format.warc.WARCConstants.WARCRecordType;
import org.archive.io.ReplayInputStream;
import org.archive.io.warc.WARCRecordInfo;
import org.archive.modules.CrawlURI;
import org.archive.util.ArchiveUtils;

public class HttpRequestRecordBuilder extends BaseWARCRecordBuilder {

    @Override
    public boolean shouldBuildRecord(CrawlURI curi) {
        return curi.getUURI().getScheme().toLowerCase().startsWith("http");
    }

    @Override
    public WARCRecordInfo buildRecord(CrawlURI curi, URI concurrentTo)
            throws IOException {
        final String timestamp =
                ArchiveUtils.getLog14Date(curi.getFetchBeginTime());

        WARCRecordInfo recordInfo = new WARCRecordInfo();
        recordInfo.setRecordId(generateRecordID());
        if (concurrentTo != null) {
            recordInfo.addExtraHeader(HEADER_KEY_CONCURRENT_TO,
                    '<' + concurrentTo.toString() + '>');
        }
        recordInfo.setType(WARCRecordType.request);
        recordInfo.setUrl(curi.toString());
        recordInfo.setCreate14DigitDate(timestamp);
        recordInfo.setMimetype(HTTP_REQUEST_MIMETYPE);
        recordInfo.setContentLength(curi.getRecorder().getRecordedOutput().getSize());
        recordInfo.setEnforceLength(true);
        
        ReplayInputStream 
            ris = curi.getRecorder().getRecordedOutput().getReplayInputStream();
        recordInfo.setContentStream(ris);

        return recordInfo;
    }

}
