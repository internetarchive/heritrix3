package org.archive.modules.warc;

import static org.archive.format.warc.WARCConstants.*;
import static org.archive.modules.CoreAttributeConstants.A_WARC_RESPONSE_HEADERS;
import static org.archive.modules.CoreAttributeConstants.HEADER_TRUNC;
import static org.archive.modules.CoreAttributeConstants.LENGTH_TRUNC;
import static org.archive.modules.CoreAttributeConstants.TIMER_TRUNC;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;

import org.apache.commons.lang3.StringUtils;
import org.archive.format.warc.WARCConstants.WARCRecordType;
import org.archive.io.ReplayInputStream;
import org.archive.io.warc.WARCRecordInfo;
import org.archive.modules.CrawlURI;
import org.archive.util.ArchiveUtils;

public class HttpResponseRecordBuilder extends BaseWARCRecordBuilder {

    @Override
    public boolean shouldBuildRecord(CrawlURI curi) {
        return !curi.isRevisit()
                && curi.getUURI().getScheme().toLowerCase().startsWith("http");
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
        recordInfo.setMimetype(HTTP_RESPONSE_MIMETYPE);
        recordInfo.setContentLength(
                curi.getRecorder().getRecordedInput().getSize());
        recordInfo.setEnforceLength(true);

        if (curi.getContentDigest() != null) {
            recordInfo.addExtraHeader(HEADER_KEY_PAYLOAD_DIGEST,
                    curi.getContentDigestSchemeString());
        }

        if (curi.getServerIP() != null) {
            recordInfo.addExtraHeader(HEADER_KEY_IP, curi.getServerIP());
        }

        // Check for truncated annotation
        String value = null;
        Collection<String> anno = curi.getAnnotations();
        if (anno.contains(TIMER_TRUNC)) {
            value = NAMED_FIELD_TRUNCATED_VALUE_TIME;
        } else if (anno.contains(LENGTH_TRUNC)) {
            value = NAMED_FIELD_TRUNCATED_VALUE_LENGTH;
        } else if (anno.contains(HEADER_TRUNC)) {
            value = NAMED_FIELD_TRUNCATED_VALUE_HEAD;
        }
        // TODO: Add annotation for TRUNCATED_VALUE_UNSPECIFIED
        if (value != null) {
            recordInfo.addExtraHeader(HEADER_KEY_TRUNCATED, value);
        }

        addWarcProtocolHeader(curi, recordInfo);

        if (curi.getData().containsKey(A_WARC_RESPONSE_HEADERS)) {
            for (Object headerObj: curi.getDataList(A_WARC_RESPONSE_HEADERS)) {
                String[] kv = StringUtils.split(((String) headerObj), ":", 2);
                recordInfo.addExtraHeader(kv[0].trim(), kv[1].trim());
            }
        }

        ReplayInputStream ris =
            curi.getRecorder().getRecordedInput().getReplayInputStream();
        recordInfo.setContentStream(ris);

        return recordInfo;
    }

    static void addWarcProtocolHeader(CrawlURI curi, WARCRecordInfo recordInfo) {
        var anno = curi.getAnnotations();
        if (anno.contains("h2")) {
            recordInfo.addExtraHeader("WARC-Protocol", "h2");
        } else if (anno.contains("h3")) {
            recordInfo.addExtraHeader("WARC-Protocol", "h3");
        }
    }
}
