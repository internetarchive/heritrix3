package org.archive.modules.warc;

import static org.archive.format.warc.WARCConstants.HEADER_KEY_CONCURRENT_TO;
import static org.archive.format.warc.WARCConstants.HEADER_KEY_PROFILE;
import static org.archive.format.warc.WARCConstants.HEADER_KEY_TRUNCATED;
import static org.archive.format.warc.WARCConstants.HTTP_RESPONSE_MIMETYPE;
import static org.archive.format.warc.WARCConstants.NAMED_FIELD_TRUNCATED_VALUE_LENGTH;
import static org.archive.format.warc.WARCConstants.PROFILE_REVISIT_IDENTICAL_DIGEST;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Map.Entry;

import org.archive.format.warc.WARCConstants.WARCRecordType;
import org.archive.io.ReplayInputStream;
import org.archive.io.warc.WARCRecordInfo;
import org.archive.modules.CrawlURI;
import org.archive.modules.revisit.RevisitProfile;
import org.archive.util.ArchiveUtils;

public class RevisitRecordBuilder extends BaseWARCRecordBuilder {

    @Override
    public boolean shouldBuildRecord(CrawlURI curi) {
        String scheme = curi.getUURI().getScheme().toLowerCase();
        return curi.isRevisit()
                && (scheme.startsWith("http") || scheme.equals("ftp") || scheme.equals("sftp"));
    }

    @Override
    public WARCRecordInfo buildRecord(CrawlURI curi, URI concurrentTo) throws IOException {
        final String timestamp =
                ArchiveUtils.getLog14Date(curi.getFetchBeginTime());
        
        long revisedLength = 0; // By default, truncate all data 
        if (curi.getRevisitProfile().getProfileName().equals(PROFILE_REVISIT_IDENTICAL_DIGEST)) {
            // Save response from identical digest matches
            revisedLength = curi.getRecorder().getRecordedInput().getContentBegin();
            revisedLength = revisedLength > 0 
                    ? revisedLength 
                    : curi.getRecorder().getRecordedInput().getSize();
        }

        WARCRecordInfo recordInfo = new WARCRecordInfo();
        recordInfo.setRecordId(generateRecordID());
        if (concurrentTo != null) {
            recordInfo.addExtraHeader(HEADER_KEY_CONCURRENT_TO,
                    '<' + concurrentTo.toString() + '>');
        }
        recordInfo.setType(WARCRecordType.revisit);
        recordInfo.setUrl(curi.toString());
        recordInfo.setCreate14DigitDate(timestamp);
        String scheme = curi.getUURI().getScheme().toLowerCase();
        if (scheme.startsWith("http")) {
            recordInfo.setMimetype(HTTP_RESPONSE_MIMETYPE);
        }
        recordInfo.setContentLength(revisedLength);
        recordInfo.setEnforceLength(false);

        RevisitProfile revisitProfile = curi.getRevisitProfile();
        recordInfo.addExtraHeader(HEADER_KEY_PROFILE,
                revisitProfile.getProfileName());
        recordInfo.addExtraHeader(HEADER_KEY_TRUNCATED,
                NAMED_FIELD_TRUNCATED_VALUE_LENGTH);

        Map<String, String> revisitHeaders = revisitProfile.getWarcHeaders();
        for (Entry<String, String> entry: revisitHeaders.entrySet()) {
            recordInfo.addExtraHeader(entry.getKey(), entry.getValue());            
        }

        ReplayInputStream ris = curi.getRecorder().getRecordedInput().getReplayInputStream();
        recordInfo.setContentStream(ris);

        return recordInfo;
    }

}
