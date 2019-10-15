package org.archive.modules.warc;

import static org.archive.format.warc.WARCConstants.HEADER_KEY_CONCURRENT_TO;
import static org.archive.format.warc.WARCConstants.HEADER_KEY_IP;
import static org.archive.modules.CoreAttributeConstants.A_DNS_SERVER_IP_LABEL;

import java.io.IOException;
import java.net.URI;

import org.archive.format.warc.WARCConstants.WARCRecordType;
import org.archive.io.ReplayInputStream;
import org.archive.io.warc.WARCRecordInfo;
import org.archive.modules.CrawlURI;
import org.archive.util.ArchiveUtils;

public class DnsResponseRecordBuilder extends BaseWARCRecordBuilder {

    @Override
    public boolean shouldBuildRecord(CrawlURI curi) {
        return "dns".equals(curi.getUURI().getScheme().toLowerCase());
    }

    @Override
    public WARCRecordInfo buildRecord(CrawlURI curi, URI concurrentTo) throws IOException {
        final String timestamp =
                ArchiveUtils.getLog14Date(curi.getFetchBeginTime());

        WARCRecordInfo recordInfo = new WARCRecordInfo();
        recordInfo.setRecordId(generateRecordID());
        if (concurrentTo != null) {
            recordInfo.addExtraHeader(HEADER_KEY_CONCURRENT_TO,
                    '<' + concurrentTo.toString() + '>');
        }
        recordInfo.setType(WARCRecordType.response);
        recordInfo.setUrl(curi.toString());
        recordInfo.setCreate14DigitDate(timestamp);
        recordInfo.setMimetype(curi.getContentType());
        
        recordInfo.setContentLength(curi.getRecorder().getRecordedInput().getSize());
        recordInfo.setEnforceLength(true);
        
        String ip = (String)curi.getData().get(A_DNS_SERVER_IP_LABEL);
        if (ip != null && ip.length() > 0) {
            recordInfo.addExtraHeader(HEADER_KEY_IP, ip);
        }
        
        ReplayInputStream ris =
            curi.getRecorder().getRecordedInput().getReplayInputStream();
        recordInfo.setContentStream(ris);
        
        return recordInfo;
    }

}
