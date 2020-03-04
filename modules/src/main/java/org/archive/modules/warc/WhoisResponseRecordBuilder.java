package org.archive.modules.warc;

import static org.archive.format.warc.WARCConstants.HEADER_KEY_CONCURRENT_TO;
import static org.archive.format.warc.WARCConstants.HEADER_KEY_IP;

import java.io.IOException;
import java.net.URI;

import org.archive.format.warc.WARCConstants.WARCRecordType;
import org.archive.io.ReplayInputStream;
import org.archive.io.warc.WARCRecordInfo;
import org.archive.modules.CoreAttributeConstants;
import org.archive.modules.CrawlURI;
import org.archive.util.ArchiveUtils;

public class WhoisResponseRecordBuilder extends BaseWARCRecordBuilder {

    @Override
    public boolean shouldBuildRecord(CrawlURI curi) {
        return "whois".equals(curi.getUURI().getScheme().toLowerCase());
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
        
        Object whoisServerIP = curi.getData().get(CoreAttributeConstants.A_WHOIS_SERVER_IP);
        if (whoisServerIP != null) {
            recordInfo.addExtraHeader(HEADER_KEY_IP, whoisServerIP.toString());
        }
        
        ReplayInputStream ris =
            curi.getRecorder().getRecordedInput().getReplayInputStream();
        recordInfo.setContentStream(ris);
        
        return recordInfo;
    }

}
