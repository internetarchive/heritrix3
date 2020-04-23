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

package org.archive.modules.writer;

import static org.archive.format.warc.WARCConstants.FTP_CONTROL_CONVERSATION_MIMETYPE;
import static org.archive.format.warc.WARCConstants.HEADER_KEY_CONCURRENT_TO;
import static org.archive.format.warc.WARCConstants.HEADER_KEY_IP;
import static org.archive.format.warc.WARCConstants.HEADER_KEY_PAYLOAD_DIGEST;
import static org.archive.format.warc.WARCConstants.HEADER_KEY_PROFILE;
import static org.archive.format.warc.WARCConstants.HEADER_KEY_TRUNCATED;
import static org.archive.format.warc.WARCConstants.HTTP_REQUEST_MIMETYPE;
import static org.archive.format.warc.WARCConstants.HTTP_RESPONSE_MIMETYPE;
import static org.archive.format.warc.WARCConstants.NAMED_FIELD_TRUNCATED_VALUE_HEAD;
import static org.archive.format.warc.WARCConstants.NAMED_FIELD_TRUNCATED_VALUE_LENGTH;
import static org.archive.format.warc.WARCConstants.NAMED_FIELD_TRUNCATED_VALUE_TIME;
import static org.archive.format.warc.WARCConstants.PROFILE_REVISIT_IDENTICAL_DIGEST;
import static org.archive.format.warc.WARCConstants.TYPE;
import static org.archive.modules.CoreAttributeConstants.A_DNS_SERVER_IP_LABEL;
import static org.archive.modules.CoreAttributeConstants.A_FTP_CONTROL_CONVERSATION;
import static org.archive.modules.CoreAttributeConstants.A_FTP_FETCH_STATUS;
import static org.archive.modules.CoreAttributeConstants.A_SOURCE_TAG;
import static org.archive.modules.CoreAttributeConstants.A_WARC_RESPONSE_HEADERS;
import static org.archive.modules.CoreAttributeConstants.HEADER_TRUNC;
import static org.archive.modules.CoreAttributeConstants.LENGTH_TRUNC;
import static org.archive.modules.CoreAttributeConstants.TIMER_TRUNC;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.archive.format.warc.WARCConstants.WARCRecordType;
import org.archive.io.ReplayInputStream;
import org.archive.io.warc.WARCRecordInfo;
import org.archive.io.warc.WARCWriter;
import org.archive.io.warc.WARCWriterPoolSettings;
import org.archive.modules.CoreAttributeConstants;
import org.archive.modules.CrawlURI;
import org.archive.modules.ProcessResult;
import org.archive.modules.revisit.RevisitProfile;
import org.archive.util.ArchiveUtils;
import org.archive.util.anvl.ANVLRecord;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * WARCWriterProcessor.
 * Intends to follow the WARC/1.0 specification.
 * 
 * <p>TODO: Remove ANVLRecord. Rename NameValue or use RFC822
 * (commons-httpclient?) or find something else.
 * 
 * @author stack
 * 
 * @deprecated in favor of {@link WARCWriterChainProcessor}
 */
@Deprecated
public class WARCWriterProcessor extends BaseWARCWriterProcessor implements WARCWriterPoolSettings {
    @SuppressWarnings("unused")
    private static final long serialVersionUID = 6182850087635847443L;
    private static final Logger logger = 
        Logger.getLogger(WARCWriterProcessor.class.getName());

    /**
     * Whether to write 'request' type records. Default is true.
     */
    {
        setWriteRequests(true);
    }
    public boolean getWriteRequests() {
        return (Boolean) kp.get("writeRequests");
    }
    public void setWriteRequests(boolean writeRequests) {
        kp.put("writeRequests",writeRequests);
    }
    
    /**
     * Whether to write 'metadata' type records. Default is true.
     */
    {
        setWriteMetadata(true);
    }
    public boolean getWriteMetadata() {
        return (Boolean) kp.get("writeMetadata");
    }
    public void setWriteMetadata(boolean writeMetadata) {
        kp.put("writeMetadata",writeMetadata);
    }

    @Deprecated
    public void setWriteRevisitForIdenticalDigests(boolean writeRevisits) {
        logger.warning("setting writeRevisitForIdenticalDigests is deprecated, value ignored");
    }

    @Deprecated
    public void setWriteRevisitForNotModified(boolean writeRevisits) {
        logger.warning("setting writeRevisitForNotModified is deprecated, value ignored");
    }

    public WARCWriterProcessor() {
    }

    /**
     * Writes a CrawlURI and its associated data to store file.
     * 
     * Currently this method understands the following uri types: dns, http, and
     * https.
     * 
     * @param curi CrawlURI to process.
     * 
     */
    @Override
    protected ProcessResult innerProcessResult(CrawlURI curi) {
        String scheme = curi.getUURI().getScheme().toLowerCase();
        try {
            if (shouldWrite(curi)) {
                return write(scheme, curi);
            } else {
                copyForwardWriteTagIfDupe(curi);
            }
        } catch (IOException e) {
            curi.getNonFatalFailures().add(e);
            logger.log(Level.SEVERE, "Failed write of Records: " +
                curi.toString(), e);
        }
        return ProcessResult.PROCEED;
    }

    protected ProcessResult write(final String lowerCaseScheme, 
            final CrawlURI curi)
    throws IOException {
        WARCWriter writer = (WARCWriter) getPool().borrowFile();

        // Reset writer temp stats so they reflect only this set of records.
        writer.resetTmpStats();
        writer.resetTmpRecordLog();

        long position = writer.getPosition();
        try {
            // Roll over to new warc file if we've exceeded maxBytes.
            writer.checkSize();
            if (writer.getPosition() != position) {
                // We rolled over to a new warc and wrote a warcinfo record.
                // Tally stats and reset temp stats, to avoid including warcinfo
                // record in stats for current url.
                setTotalBytesWritten(getTotalBytesWritten() +
                    (writer.getPosition() - position));
                addStats(writer.getTmpStats());
                writer.resetTmpStats();
                writer.resetTmpRecordLog();

                position = writer.getPosition();
            }

            // Write a request, response, and metadata all in the one
            // 'transaction'.
            final URI baseid = getRecordID();
            final String timestamp =
                ArchiveUtils.getLog14Date(curi.getFetchBeginTime());
            if (lowerCaseScheme.startsWith("http")) {
                writeHttpRecords(curi, writer, baseid, timestamp); 
            } else if (lowerCaseScheme.equals("dns")) {
                writeDnsRecords(curi, writer, baseid, timestamp);
            } else if (lowerCaseScheme.equals("ftp") || lowerCaseScheme.equals("sftp")) {
                writeFtpRecords(writer, curi, baseid, timestamp);
            } else if (lowerCaseScheme.equals("whois")) {
                writeWhoisRecords(writer, curi, baseid, timestamp);
            } else {
                logger.warning("No handler for scheme " + lowerCaseScheme);
            }
        } catch (IOException e) {
            // Invalidate this file (It gets a '.invalid' suffix).
            getPool().invalidateFile(writer);
            // Set the writer to null otherwise the pool accounting
            // of how many active writers gets skewed if we subsequently
            // do a returnWriter call on this object in the finally block.
            writer = null;
            throw e;
        } finally {
            if (writer != null) {
                updateMetadataAfterWrite(curi, writer, position);
                getPool().returnFile(writer);
            }
        }
        // XXX this looks wrong, check should happen *before* writing the
        // record, the way checkBytesWritten() currently works
        return checkBytesWritten();
    }
    
    protected void writeDnsRecords(final CrawlURI curi, WARCWriter w,
            final URI baseid, final String timestamp) throws IOException {
        WARCRecordInfo recordInfo = new WARCRecordInfo();
        recordInfo.setType(WARCRecordType.response);
        recordInfo.setUrl(curi.toString());
        recordInfo.setCreate14DigitDate(timestamp);
        recordInfo.setMimetype(curi.getContentType());
        recordInfo.setRecordId(baseid);
        
        recordInfo.setContentLength(curi.getRecorder().getRecordedInput().getSize());
        recordInfo.setEnforceLength(true);
        
        String ip = (String)curi.getData().get(A_DNS_SERVER_IP_LABEL);
        if (ip != null && ip.length() > 0) {
            recordInfo.addExtraHeader(HEADER_KEY_IP, ip);
        }
        
        ReplayInputStream ris =
            curi.getRecorder().getRecordedInput().getReplayInputStream();
        recordInfo.setContentStream(ris);
        
        try {
            w.writeRecord(recordInfo);
        } finally {
            IOUtils.closeQuietly(ris);
        }
        
        recordInfo.getRecordId();
    }

    protected void writeWhoisRecords(WARCWriter w, CrawlURI curi, URI baseid,
            String timestamp) throws IOException {
        WARCRecordInfo recordInfo = new WARCRecordInfo();
        recordInfo.setType(WARCRecordType.response);
        recordInfo.setUrl(curi.toString());
        recordInfo.setCreate14DigitDate(timestamp);
        recordInfo.setMimetype(curi.getContentType());
        recordInfo.setRecordId(baseid);
        recordInfo.setContentLength(curi.getRecorder().getRecordedInput().getSize());
        recordInfo.setEnforceLength(true);
        
        Object whoisServerIP = curi.getData().get(CoreAttributeConstants.A_WHOIS_SERVER_IP);
        if (whoisServerIP != null) {
            recordInfo.addExtraHeader(HEADER_KEY_IP, whoisServerIP.toString());
        }
        
        ReplayInputStream ris =
            curi.getRecorder().getRecordedInput().getReplayInputStream();
        recordInfo.setContentStream(ris);
        
        try {
            w.writeRecord(recordInfo);
        } finally {
            IOUtils.closeQuietly(ris);
        }
        recordInfo.getRecordId();
    }

    protected void writeHttpRecords(final CrawlURI curi, WARCWriter w,
            final URI baseid, final String timestamp) throws IOException {
        // Add named fields for ip, checksum, and relate the metadata
        // and request to the resource field.
        // TODO: Use other than ANVL (or rename ANVL as NameValue or
        // use RFC822 (commons-httpclient?).
        ANVLRecord headers = new ANVLRecord();
        headers.addLabelValue(HEADER_KEY_IP, getHostAddress(curi));

        URI rid;
        
        if (curi.isRevisit()) {
            rid = writeRevisit(w, timestamp, HTTP_RESPONSE_MIMETYPE, baseid, curi, headers);
        } else {
            if (curi.getContentDigest() != null) {
                headers.addLabelValue(HEADER_KEY_PAYLOAD_DIGEST,
                        curi.getContentDigestSchemeString());
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
                headers.addLabelValue(HEADER_KEY_TRUNCATED, value);
            }
            rid = writeResponse(w, timestamp, HTTP_RESPONSE_MIMETYPE,
            	baseid, curi, headers);
        }
        
        headers = new ANVLRecord();
        headers.addLabelValue(HEADER_KEY_CONCURRENT_TO,
            '<' + rid.toString() + '>');

        if (getWriteRequests()) {
            writeRequest(w, timestamp, HTTP_REQUEST_MIMETYPE,
                    baseid, curi, headers);
        }
        if (getWriteMetadata()) {
            writeMetadata(w, timestamp, baseid, curi, headers);
        }
    }

    protected void writeFtpRecords(WARCWriter w, final CrawlURI curi, final URI baseid,
            final String timestamp) throws IOException {
        ANVLRecord headers = new ANVLRecord();
        headers.addLabelValue(HEADER_KEY_IP, getHostAddress(curi));
        String controlConversation = curi.getData().get(A_FTP_CONTROL_CONVERSATION).toString();
        URI rid = writeFtpControlConversation(w, timestamp, baseid, curi, headers, controlConversation);
        
        if (curi.getContentDigest() != null) {
            headers.addLabelValue(HEADER_KEY_PAYLOAD_DIGEST,
            curi.getContentDigestSchemeString());
        }
            
        if (curi.getRecorder() != null) {
            if (curi.isRevisit()) {
                rid = writeRevisit(w, timestamp, null,
                        baseid, curi, headers, 0);
            } else {
                headers = new ANVLRecord();
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
                    headers.addLabelValue(HEADER_KEY_TRUNCATED, value);
                }
                
                if (curi.getContentDigest() != null) {
                    headers.addLabelValue(HEADER_KEY_PAYLOAD_DIGEST,
                            curi.getContentDigestSchemeString());
                }
                headers.addLabelValue(HEADER_KEY_CONCURRENT_TO, '<' + rid.toString() + '>');
                rid = writeResource(w, timestamp, curi.getContentType(), baseid, curi, headers);
            }
        }
        if (getWriteMetadata()) {
            headers = new ANVLRecord();
            headers.addLabelValue(HEADER_KEY_CONCURRENT_TO, '<' + rid.toString() + '>');
            writeMetadata(w, timestamp, baseid, curi, headers);
        }
    }

    protected URI writeFtpControlConversation(WARCWriter w, String timestamp,
            URI baseid, CrawlURI curi, ANVLRecord headers,
            String controlConversation) throws IOException {
        
        WARCRecordInfo recordInfo = new WARCRecordInfo();
        recordInfo.setCreate14DigitDate(timestamp);
        recordInfo.setUrl(curi.toString());
        recordInfo.setMimetype(FTP_CONTROL_CONVERSATION_MIMETYPE);
        recordInfo.setExtraHeaders(headers);
        recordInfo.setEnforceLength(true);
        recordInfo.setType(WARCRecordType.metadata);

        recordInfo.setRecordId(qualifyRecordID(baseid, TYPE, WARCRecordType.metadata.toString()));
        
        byte[] b = controlConversation.getBytes("UTF-8");
        
        recordInfo.setContentStream(new ByteArrayInputStream(b));
        recordInfo.setContentLength((long) b.length);
        
        w.writeRecord(recordInfo);
        
        return recordInfo.getRecordId();
    }

    protected URI writeRequest(final WARCWriter w,
            final String timestamp, final String mimetype,
            final URI baseid, final CrawlURI curi,
            final ANVLRecord namedFields) 
    throws IOException {
        WARCRecordInfo recordInfo = new WARCRecordInfo();
        recordInfo.setType(WARCRecordType.request);
        recordInfo.setUrl(curi.toString());
        recordInfo.setCreate14DigitDate(timestamp);
        recordInfo.setMimetype(mimetype);
        recordInfo.setExtraHeaders(namedFields);
        recordInfo.setContentLength(curi.getRecorder().getRecordedOutput().getSize());
        recordInfo.setEnforceLength(true);
        
        final URI uid = qualifyRecordID(baseid, TYPE, WARCRecordType.request.toString());
        recordInfo.setRecordId(uid);
        
        ReplayInputStream 
            ris = curi.getRecorder().getRecordedOutput().getReplayInputStream();
        recordInfo.setContentStream(ris);
        
        try {
            w.writeRecord(recordInfo);
        } finally {
            IOUtils.closeQuietly(ris);
        }
        
        return recordInfo.getRecordId();
    }
    
    protected URI writeResponse(final WARCWriter w,
            final String timestamp, final String mimetype,
            final URI baseid, final CrawlURI curi,
            final ANVLRecord suppliedFields) 
    throws IOException {
        ANVLRecord namedFields = suppliedFields;
        if(curi.getData().containsKey(A_WARC_RESPONSE_HEADERS)) {
           namedFields = namedFields.clone(); 
           for (Object headerObj : curi.getDataList(A_WARC_RESPONSE_HEADERS)) {
               String[] kv = StringUtils.split(((String)headerObj),":",2);
               namedFields.addLabelValue(kv[0].trim(), kv[1].trim());
           }
        }
        
        WARCRecordInfo recordInfo = new WARCRecordInfo();
        recordInfo.setType(WARCRecordType.response);
        recordInfo.setUrl(curi.toString());
        recordInfo.setCreate14DigitDate(timestamp);
        recordInfo.setMimetype(mimetype);
        recordInfo.setRecordId(baseid);
        recordInfo.setExtraHeaders(namedFields);
        recordInfo.setContentLength(curi.getRecorder().getRecordedInput().getSize());
        recordInfo.setEnforceLength(true);
        
        ReplayInputStream ris =
            curi.getRecorder().getRecordedInput().getReplayInputStream();
        recordInfo.setContentStream(ris);
        
        try {
            w.writeRecord(recordInfo);
        } finally {
            IOUtils.closeQuietly(ris);
        }
        
        return recordInfo.getRecordId();
    }
    
    protected URI writeResource(final WARCWriter w,
            final String timestamp, final String mimetype,
            final URI baseid, final CrawlURI curi,
            final ANVLRecord namedFields) 
    throws IOException {
        WARCRecordInfo recordInfo = new WARCRecordInfo();
        recordInfo.setType(WARCRecordType.resource);
        recordInfo.setUrl(curi.toString());
        recordInfo.setCreate14DigitDate(timestamp);
        recordInfo.setMimetype(mimetype);
        recordInfo.setRecordId(baseid);
        recordInfo.setExtraHeaders(namedFields);
        recordInfo.setContentLength(curi.getRecorder().getRecordedInput().getSize());
        recordInfo.setEnforceLength(true);
        
        ReplayInputStream ris = curi.getRecorder().getRecordedInput().getReplayInputStream();
        recordInfo.setContentStream(ris);
        try {
            w.writeRecord(recordInfo);
        } finally {
            IOUtils.closeQuietly(ris);
        }
        
        return recordInfo.getRecordId();
    }

    protected URI writeRevisit(final WARCWriter w,
            final String timestamp, final String mimetype,
            final URI baseid, final CrawlURI curi,
            final ANVLRecord headers)  
            	    throws IOException {
        long revisedLength = 0; // By default, truncate all data 
        
        if (curi.getRevisitProfile().getProfileName().equals(PROFILE_REVISIT_IDENTICAL_DIGEST) ) {
        	// Save response from identical digest matches
        	revisedLength = curi.getRecorder().getRecordedInput().getContentBegin();
        	revisedLength = revisedLength > 0 
        			? revisedLength 
        			: curi.getRecorder().getRecordedInput().getSize();
        }
        return writeRevisit(w, timestamp, mimetype, baseid, curi,
                headers, revisedLength);
    }
    
    protected URI writeRevisit(final WARCWriter w,
            final String timestamp, final String mimetype,
            final URI baseid, final CrawlURI curi,
            final ANVLRecord headers,
            final long contentLength)  
            	    throws IOException {
        WARCRecordInfo recordInfo = new WARCRecordInfo();
        recordInfo.setType(WARCRecordType.revisit);
        recordInfo.setUrl(curi.toString());
        recordInfo.setCreate14DigitDate(timestamp);
        recordInfo.setMimetype(mimetype);
        recordInfo.setRecordId(baseid);
        recordInfo.setContentLength(contentLength);
        recordInfo.setEnforceLength(false);
    	
        RevisitProfile revisitProfile = curi.getRevisitProfile();
        
        headers.addLabelValue(HEADER_KEY_PROFILE, revisitProfile.getProfileName());
        headers.addLabelValue(HEADER_KEY_TRUNCATED, NAMED_FIELD_TRUNCATED_VALUE_LENGTH);

        Map<String, String> revisitHeaders = revisitProfile.getWarcHeaders();
        
        if (!revisitHeaders.isEmpty()) {
        	recordInfo.setExtraHeaders(headers);
	        for ( String key : revisitHeaders.keySet()) {
	            headers.addLabelValue(key, revisitHeaders.get(key));        	
	        }
        }
        
		ReplayInputStream ris = curi.getRecorder().getRecordedInput().getReplayInputStream();
		recordInfo.setContentStream(ris);

		try {
			w.writeRecord(recordInfo);
		} finally {
			IOUtils.closeQuietly(ris);
		}
		return recordInfo.getRecordId();
    }
    
    /**
     * Saves a header from the given HTTP operation into the 
     * provider headers under a new name
     */
    protected void saveHeader(CrawlURI curi, ANVLRecord warcHeaders,
            String origName, String newName) {
        String value = curi.getHttpResponseHeader(origName);
        if (value != null) {
            warcHeaders.addLabelValue(newName, value);
        }
    }

	protected URI writeMetadata(final WARCWriter w,
            final String timestamp,
            final URI baseid, final CrawlURI curi,
            final ANVLRecord namedFields) 
    throws IOException {
	    WARCRecordInfo recordInfo = new WARCRecordInfo();
        recordInfo.setType(WARCRecordType.metadata);
        recordInfo.setUrl(curi.toString());
        recordInfo.setCreate14DigitDate(timestamp);
        recordInfo.setMimetype(ANVLRecord.MIMETYPE);
        recordInfo.setExtraHeaders(namedFields);
        recordInfo.setEnforceLength(true);
	    
        recordInfo.setRecordId(qualifyRecordID(baseid, TYPE, WARCRecordType.metadata.toString()));

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
            if(StringUtils.isNotBlank(flattenVia(curi))) {
                r.addLabelValue("via", flattenVia(curi));
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
        
        w.writeRecord(recordInfo);
        
        return recordInfo.getRecordId();
    }
    
    protected URI qualifyRecordID(final URI base, final String key,
            final String value)
    throws IOException {
        Map<String, String> qualifiers = new HashMap<String, String>(1);
        qualifiers.put(key, value);
        return generator.qualifyRecordID(base, qualifiers);
    }  
    
    @Override
    protected JSONObject toCheckpointJson() throws JSONException {
        JSONObject json = super.toCheckpointJson();
        json.put("urlsWritten", urlsWritten);
        json.put("stats", stats);
        return json;
    }
    
    @Override
    protected void fromCheckpointJson(JSONObject json) throws JSONException {
        super.fromCheckpointJson(json);

        // conditionals below are for backward compatibility with old checkpoints
        
        if (json.has("urlsWritten")) {
            urlsWritten.set(json.getLong("urlsWritten"));
        }
        
        if (json.has("stats")) {
            HashMap<String, Map<String, Long>> cpStats = new HashMap<String, Map<String, Long>>();
            JSONObject jsonStats = json.getJSONObject("stats");
            if (JSONObject.getNames(jsonStats) != null) {
                for (String key1: JSONObject.getNames(jsonStats)) {
                    JSONObject jsonSubstats = jsonStats.getJSONObject(key1);
                    if (!cpStats.containsKey(key1)) {
                        cpStats.put(key1, new HashMap<String, Long>());
                    }
                    Map<String, Long> substats = cpStats.get(key1);

                    for (String key2: JSONObject.getNames(jsonSubstats)) {
                        long value = jsonSubstats.getLong(key2);
                        substats.put(key2, value);
                    }
                }
                addStats(cpStats);
            }
        }
    }
    
}
