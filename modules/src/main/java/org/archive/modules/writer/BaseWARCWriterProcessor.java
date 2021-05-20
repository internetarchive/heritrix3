package org.archive.modules.writer;

import static org.archive.modules.CoreAttributeConstants.A_WARC_STATS;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_CONTENT_DIGEST_COUNT;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_ORIGINAL_DATE;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_ORIGINAL_URL;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_WARC_FILENAME;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_WARC_FILE_OFFSET;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_WARC_RECORD_ID;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_WRITE_TAG;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.archive.format.warc.WARCConstants.WARCRecordType;
import org.archive.io.warc.WARCRecordInfo;
import org.archive.io.warc.WARCWriter;
import org.archive.io.warc.WARCWriterPool;
import org.archive.io.warc.WARCWriterPoolSettings;
import org.archive.modules.CrawlMetadata;
import org.archive.modules.CrawlURI;
import org.archive.modules.revisit.IdenticalPayloadDigestRevisit;
import org.archive.spring.ConfigPath;
import org.archive.uid.RecordIDGenerator;
import org.archive.uid.UUIDGenerator;
import org.archive.util.ArchiveUtils;
import org.archive.util.anvl.ANVLRecord;

abstract public class BaseWARCWriterProcessor extends WriterPoolProcessor
        implements WARCWriterPoolSettings {

    private static final Logger logger = 
            Logger.getLogger(BaseWARCWriterProcessor.class.getName());

    protected AtomicLong urlsWritten = new AtomicLong();
    protected ConcurrentMap<String, ConcurrentMap<String, AtomicLong>> stats = new ConcurrentHashMap<String, ConcurrentMap<String, AtomicLong>>();
    public ConcurrentMap<String, ConcurrentMap<String, AtomicLong>> getStats() {
        return stats;
    }


    /**
     * Generator for record IDs
     */
    protected RecordIDGenerator generator = new UUIDGenerator();
    public RecordIDGenerator getRecordIDGenerator() {
        return generator; 
    }
    public void setRecordIDGenerator(RecordIDGenerator generator) {
        this.generator = generator;
    }

    protected URI getRecordID() throws IOException {
        return generator.getRecordID();
    }

    public long getDefaultMaxFileSize() {
        return 1000000000L; // 1 SI giga-byte (10^9 bytes), per WARC appendix A
    }

    public List<ConfigPath> getDefaultStorePaths() {
        List<ConfigPath> paths = new ArrayList<ConfigPath>();
        paths.add(new ConfigPath("warcs default store path", "warcs"));
        return paths;
    }

    @Override
    protected void setupPool(final AtomicInteger serialNo) {
        setPool(new WARCWriterPool(serialNo, this, getPoolMaxActive(), getMaxWaitForIdleMs()));
    }

    private transient List<String> cachedMetadata;
    public List<String> getMetadata() {
        if (cachedMetadata != null) {
            return cachedMetadata;
        }
        ANVLRecord record = new ANVLRecord();
        record.addLabelValue("software", "Heritrix/" +
                ArchiveUtils.VERSION + " http://crawler.archive.org");
        try {
            InetAddress host = InetAddress.getLocalHost();
            record.addLabelValue("ip", host.getHostAddress());
            record.addLabelValue("hostname", host.getCanonicalHostName());
        } catch (UnknownHostException e) {
            logger.log(Level.WARNING,"unable top obtain local crawl engine host",e);
        }
        
        // conforms to ISO 28500:2009 as of May 2009
        // as described at http://bibnum.bnf.fr/WARC/ 
        // latest draft as of November 2008
        record.addLabelValue("format","WARC File Format 1.0"); 
        record.addLabelValue("conformsTo","http://bibnum.bnf.fr/WARC/WARC_ISO_28500_version1_latestdraft.pdf");
        
        // Get other values from metadata provider

        CrawlMetadata provider = getMetadataProvider();

        addIfNotBlank(record,"operator", provider.getOperator());
        addIfNotBlank(record,"publisher", provider.getOrganization());
        addIfNotBlank(record,"audience", provider.getAudience());
        addIfNotBlank(record,"isPartOf", provider.getJobName());
        // TODO: make date match 'job creation date' as in Heritrix 1.x
        // until then, leave out (plenty of dates already in WARC 
        // records
//            String rawDate = provider.getBeginDate();
//            if(StringUtils.isNotBlank(rawDate)) {
//                Date date;
//                try {
//                    date = ArchiveUtils.parse14DigitDate(rawDate);
//                    addIfNotBlank(record,"created",ArchiveUtils.getLog14Date(date));
//                } catch (ParseException e) {
//                    logger.log(Level.WARNING,"obtaining warc created date",e);
//                }
//            }
        addIfNotBlank(record,"description", provider.getDescription());
        addIfNotBlank(record,"robots", provider.getRobotsPolicyName().toLowerCase());

        addIfNotBlank(record,"http-header-user-agent",
                provider.getUserAgent());
        addIfNotBlank(record,"http-header-from",
                provider.getOperatorFrom());

        // really ugly to return as List<String>, but changing would require 
        // larger refactoring
        return Collections.singletonList(record.toString());
    }
    
    protected void addIfNotBlank(ANVLRecord record, String label, String value) {
        if(StringUtils.isNotBlank(value)) {
            record.addLabelValue(label, value);
        }
    }


    protected void addStats(Map<String, Map<String, Long>> substats) {
        for (String key: substats.keySet()) {
            // intentionally redundant here -- if statement avoids creating
            // unused empty map every time; putIfAbsent() ensures thread safety
            if (stats.get(key) == null) {
                stats.putIfAbsent(key, new ConcurrentHashMap<String, AtomicLong>());
            }
            
            for (String subkey: substats.get(key).keySet()) {
                AtomicLong oldValue = stats.get(key).get(subkey);
                if (oldValue == null) {
                    oldValue = stats.get(key).putIfAbsent(subkey, new AtomicLong(substats.get(key).get(subkey)));
                }
                if (oldValue != null) {
                    oldValue.addAndGet(substats.get(key).get(subkey));
                }
            }
        }
    }

    @Override
    public String report() {
        // XXX note in report that stats include recovered checkpoint?
        logger.info("final stats: " + stats);
        
        StringBuilder buf = new StringBuilder();
        buf.append("Processor: " + getClass().getName() + "\n");
        buf.append("  Function:          Writes WARCs\n");
        buf.append("  Total CrawlURIs:   " + urlsWritten + "\n");
        buf.append("  Revisit records:   " + WARCWriter.getStat(stats, WARCRecordType.revisit.toString(), WARCWriter.NUM_RECORDS) + "\n");
        
        long bytes = WARCWriter.getStat(stats, WARCRecordType.response.toString(), WARCWriter.CONTENT_BYTES)
                + WARCWriter.getStat(stats, WARCRecordType.resource.toString(), WARCWriter.CONTENT_BYTES);
        buf.append("  Crawled content bytes (including http headers): "
                + bytes + " (" + ArchiveUtils.formatBytesForDisplay(bytes) + ")\n");
        
        bytes = WARCWriter.getStat(stats, WARCWriter.TOTALS, WARCWriter.TOTAL_BYTES);
        buf.append("  Total uncompressed bytes (including all warc records): "
                + bytes + " (" + ArchiveUtils.formatBytesForDisplay(bytes) + ")\n");
        
        buf.append("  Total size on disk ("+ (getCompress() ? "compressed" : "uncompressed") + "): "
                + getTotalBytesWritten() + " (" + ArchiveUtils.formatBytesForDisplay(getTotalBytesWritten()) + ")\n");
        
        return buf.toString();
    }

    protected Map<String, Map<String, Long>> copyStats(Map<String, Map<String, Long>> orig) {
        Map<String, Map<String, Long>> copy = new HashMap<String, Map<String, Long>>(orig.size());
        for (String k: orig.keySet()) {
            copy.put(k, new HashMap<String, Long>(orig.get(k)));
        }
        return copy;
    }
   
    protected void updateMetadataAfterWrite(final CrawlURI curi,
            WARCWriter writer, long startPosition) {
        if (WARCWriter.getStat(writer.getTmpStats(), WARCWriter.TOTALS, WARCWriter.NUM_RECORDS) > 0l) {
             addStats(writer.getTmpStats());
             urlsWritten.incrementAndGet();
        }
        if (logger.isLoggable(Level.FINE)) { 
            logger.fine("wrote " 
                + WARCWriter.getStat(writer.getTmpStats(), WARCWriter.TOTALS, WARCWriter.SIZE_ON_DISK) 
                + " bytes to " + writer.getFile().getName() + " for " + curi);
        }
        setTotalBytesWritten(getTotalBytesWritten() + (writer.getPosition() - startPosition));

        curi.addExtraInfo("warcFilename", writer.getFilenameWithoutOccupiedSuffix());
        curi.addExtraInfo("warcFileOffset", startPosition);

        curi.getData().put(A_WARC_STATS, copyStats(writer.getTmpStats()));

        // history for uri-based dedupe
        Map<String,Object>[] history = curi.getFetchHistory();
        if (history != null && history[0] != null) {
            history[0].put(A_WRITE_TAG, writer.getFilenameWithoutOccupiedSuffix());
        }
        
        // history for uri-agnostic, content digest based dedupe
        if (curi.getContentDigest() != null && curi.hasContentDigestHistory()) {
            for (WARCRecordInfo warcRecord: writer.getTmpRecordLog()) {
                if ((warcRecord.getType() == WARCRecordType.response 
                        || warcRecord.getType() == WARCRecordType.resource)
                        && warcRecord.getContentStream() != null
                        && warcRecord.getContentLength() > 0) {
                    curi.getContentDigestHistory().put(A_ORIGINAL_URL, warcRecord.getUrl());
                    curi.getContentDigestHistory().put(A_WARC_RECORD_ID, warcRecord.getRecordId().toString());
                    curi.getContentDigestHistory().put(A_WARC_FILENAME, warcRecord.getWARCFilename());
                    curi.getContentDigestHistory().put(A_WARC_FILE_OFFSET, warcRecord.getWARCFileOffset());
                    curi.getContentDigestHistory().put(A_ORIGINAL_DATE, warcRecord.getCreate14DigitDate());
                    curi.getContentDigestHistory().put(A_CONTENT_DIGEST_COUNT, 1);
                } else if (warcRecord.getType() == WARCRecordType.revisit
                        && curi.getRevisitProfile() instanceof IdenticalPayloadDigestRevisit) {
                     Integer oldCount = (Integer) curi.getContentDigestHistory().get(A_CONTENT_DIGEST_COUNT);
                     if (oldCount == null) {
                         // shouldn't happen, log a warning?
                         oldCount = 1;
                     }
                     curi.getContentDigestHistory().put(A_CONTENT_DIGEST_COUNT, oldCount + 1);
                }
            }
        }
    }

}
