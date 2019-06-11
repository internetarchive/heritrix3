package org.archive.modules.warc;

import static org.archive.format.warc.WARCConstants.FTP_CONTROL_CONVERSATION_MIMETYPE;
import static org.archive.format.warc.WARCConstants.HEADER_KEY_IP;
import static org.archive.modules.CoreAttributeConstants.A_FTP_CONTROL_CONVERSATION;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;

import org.archive.format.warc.WARCConstants.WARCRecordType;
import org.archive.io.warc.WARCRecordInfo;
import org.archive.modules.CrawlURI;
import org.archive.util.ArchiveUtils;
import org.archive.util.anvl.ANVLRecord;

public class FtpControlConversationRecordBuilder extends WARCRecordBuilder {

    @Override
    public boolean shouldProcess(CrawlURI curi) {
        return "ftp".equals(curi.getUURI().getScheme().toLowerCase());
    }

    @Override
    public WARCRecordInfo buildRecord(CrawlURI curi, URI concurrentTo) throws IOException {
        final String timestamp =
                ArchiveUtils.getLog14Date(curi.getFetchBeginTime());
        String controlConversation =
                curi.getData().get(A_FTP_CONTROL_CONVERSATION).toString();
        ANVLRecord headers = new ANVLRecord();
        headers.addLabelValue(HEADER_KEY_IP, getHostAddress(curi));

        WARCRecordInfo recordInfo = new WARCRecordInfo();
        recordInfo.setCreate14DigitDate(timestamp);
        recordInfo.setUrl(curi.toString());
        recordInfo.setMimetype(FTP_CONTROL_CONVERSATION_MIMETYPE);
        recordInfo.setExtraHeaders(headers);
        recordInfo.setEnforceLength(true);
        recordInfo.setType(WARCRecordType.metadata);

        recordInfo.setRecordId(generateRecordID());
        
        byte[] b = controlConversation.getBytes("UTF-8");
        
        recordInfo.setContentStream(new ByteArrayInputStream(b));
        recordInfo.setContentLength((long) b.length);

        return recordInfo;
    }

}
