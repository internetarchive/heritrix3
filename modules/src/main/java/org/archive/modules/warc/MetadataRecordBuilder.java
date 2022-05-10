package org.archive.modules.warc;

import static org.archive.format.warc.WARCConstants.HEADER_KEY_CONCURRENT_TO;
import static org.archive.modules.CoreAttributeConstants.A_FTP_FETCH_STATUS;
import static org.archive.modules.CoreAttributeConstants.A_SOURCE_TAG;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;

import org.apache.commons.lang.StringUtils;
import org.archive.format.warc.WARCConstants.WARCRecordType;
import org.archive.io.warc.WARCRecordInfo;
import org.archive.modules.CrawlURI;
import org.archive.util.ArchiveUtils;
import org.archive.util.anvl.ANVLRecord;

public class MetadataRecordBuilder extends BaseWARCRecordBuilder {

    /**
     * If you don't want metadata records, take this class out of the chain.
     */
    @Override
    public boolean shouldBuildRecord(CrawlURI curi) {
        String scheme = curi.getUURI().getScheme().toLowerCase();
        return scheme.startsWith("http") || "ftp".equals(scheme) || "sftp".equals(scheme); 
    }

    @Override
    public WARCRecordInfo buildRecord(CrawlURI curi, URI concurrentTo) throws IOException {
        final String timestamp =
                ArchiveUtils.getLog14Date(curi.getFetchBeginTime());
        WARCRecordInfo recordInfo = new WARCRecordInfo();
        recordInfo.setType(WARCRecordType.metadata);
        recordInfo.setRecordId(generateRecordID());
        if (concurrentTo != null) {
            recordInfo.addExtraHeader(HEADER_KEY_CONCURRENT_TO,
                    "<" + concurrentTo + ">");
        }
        recordInfo.setUrl(curi.toString());
        recordInfo.setCreate14DigitDate(timestamp);
        recordInfo.setMimetype(ANVLRecord.MIMETYPE);
        recordInfo.setEnforceLength(true);

        // Get some metadata from the curi.
        // TODO: Get all curi metadata.
        // TODO: Use other than ANVL (or rename ANVL as NameValue or use
        // RFC822 (commons-httpclient?).
        ANVLRecord r = new ANVLRecord();
        if (curi.isSeed()) {
            r.addLabel("seed");
        } else {
            if (curi.forceFetch()) {
                r.addLabel("force-fetch");
            }
            if(StringUtils.isNotBlank(curi.getVia().toString())) {
                r.addLabelValue("via", curi.getVia().toString());
            }
            if(StringUtils.isNotBlank(curi.getPathFromSeed())) {
                r.addLabelValue("hopsFromSeed", curi.getPathFromSeed());
            }
            if (curi.containsDataKey(A_SOURCE_TAG)) {
                r.addLabelValue("sourceTag", 
                        (String)curi.getData().get(A_SOURCE_TAG));
            }
        }
        long duration = curi.getFetchCompletedTime() - curi.getFetchBeginTime();
        if (duration > -1) {
            r.addLabelValue("fetchTimeMs", Long.toString(duration));
        }
        
        if (curi.getData().containsKey(A_FTP_FETCH_STATUS)) {
            r.addLabelValue("ftpFetchStatus", curi.getData().get(A_FTP_FETCH_STATUS).toString());
        }

        if (curi.getRecorder() != null && curi.getRecorder().getCharset() != null) {
            r.addLabelValue("charsetForLinkExtraction", curi.getRecorder().getCharset().name());
        }
        
        for (String annotation: curi.getAnnotations()) {
            if (annotation.startsWith("usingCharsetIn") || annotation.startsWith("inconsistentCharsetIn")) {
                String[] kv = annotation.split(":", 2);
                r.addLabelValue(kv[0], kv[1]);
            }
        }

        // Add outlinks though they are effectively useless without anchor text.
        Collection<CrawlURI> links = curi.getOutLinks();
        if (links != null && links.size() > 0) {
            for (CrawlURI link: links) {
                r.addLabelValue("outlink", link.getURI()+" "+link.getLastHop()+" "+link.getViaContext());
            }
        }
        
        // TODO: Other curi fields to write to metadata.
        // 
        // Credentials
        // 
        // fetch-began-time: 1154569278774
        // fetch-completed-time: 1154569281816
        //
        // Annotations.
        
        byte [] b = r.getUTF8Bytes();
        recordInfo.setContentStream(new ByteArrayInputStream(b));
        recordInfo.setContentLength((long) b.length);
        
        return recordInfo;
    }
}
