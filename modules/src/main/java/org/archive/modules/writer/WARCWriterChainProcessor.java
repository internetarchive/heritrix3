package org.archive.modules.writer;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.io.warc.WARCRecordInfo;
import org.archive.io.warc.WARCWriter;
import org.archive.modules.CrawlURI;
import org.archive.modules.ProcessResult;
import org.archive.modules.deciderules.recrawl.IdenticalDigestDecideRule;
import org.archive.modules.warc.DnsResponseRecordBuilder;
import org.archive.modules.warc.FtpControlConversationRecordBuilder;
import org.archive.modules.warc.FtpResponseRecordBuilder;
import org.archive.modules.warc.HttpRequestRecordBuilder;
import org.archive.modules.warc.HttpResponseRecordBuilder;
import org.archive.modules.warc.MetadataRecordBuilder;
import org.archive.modules.warc.RevisitRecordBuilder;
import org.archive.modules.warc.WARCRecordBuilder;
import org.archive.modules.warc.WhoisResponseRecordBuilder;
import org.archive.spring.HasKeyedProperties;

/**
 * WARC writer processor. The types of records that to be written can be
 * configured by including or excluding {@link WARCRecordBuilder}
 * implementations (see {@link #setChain(List)}).
 *
 * <p>This is the default chain:
 * <pre>
 *   &lt;property name="chain"&gt;
 *    &lt;list&gt;
 *     &lt;bean class="org.archive.modules.warc.DnsResponseRecordBuilder"/&gt;
 *     &lt;bean class="org.archive.modules.warc.HttpResponseRecordBuilder"/&gt;
 *     &lt;bean class="org.archive.modules.warc.WhoisResponseRecordBuilder"/&gt;
 *     &lt;bean class="org.archive.modules.warc.FtpControlConversationRecordBuilder"/&gt;
 *     &lt;bean class="org.archive.modules.warc.FtpResponseRecordBuilder"/&gt;
 *     &lt;bean class="org.archive.modules.warc.RevisitRecordBuilder"/&gt;
 *     &lt;bean class="org.archive.modules.warc.HttpRequestRecordBuilder"/&gt;
 *     &lt;bean class="org.archive.modules.warc.MetadataRecordBuilder"/&gt;
 *    &lt;/list&gt;
 *   &lt;/property&gt;
 * </pre>
 *
 * <p>
 * Replaces {@link WARCWriterProcessor}.
 *
 * @see WARCRecordBuilder
 * @author nlevitt
 */
public class WARCWriterChainProcessor extends BaseWARCWriterProcessor implements HasKeyedProperties {
    private static final Logger logger = 
            Logger.getLogger(WARCWriterChainProcessor.class.getName());
    
    {
        setChain(Arrays.asList(
                new DnsResponseRecordBuilder(),
                new HttpResponseRecordBuilder(),
                new WhoisResponseRecordBuilder(),
                new FtpControlConversationRecordBuilder(),
                new FtpResponseRecordBuilder(),
                new RevisitRecordBuilder(),
                new HttpRequestRecordBuilder(),
                new MetadataRecordBuilder()));
    }
    @SuppressWarnings("unchecked")
    public List<? extends WARCRecordBuilder> getChain() {
        return (List<WARCRecordBuilder>) kp.get("chain");
    }
    public void setChain(List<? extends WARCRecordBuilder> chain) {
        kp.put("chain", chain);
    }

    @Override
    protected boolean shouldWrite(CrawlURI curi) {
        if (getSkipIdenticalDigests()
                && IdenticalDigestDecideRule.hasIdenticalDigest(curi)) {
            curi.getAnnotations().add(ANNOTATION_UNWRITTEN 
                    + ":identicalDigest");
            return false;
        }

        // WARCWriterProcessor has seemingly unnecessarily complicated logic
        if (curi.getFetchStatus() <= 0) {
            curi.getAnnotations().add(ANNOTATION_UNWRITTEN + ":status");
            return false;
        }
        
        return true;
    }
    
    @Override
    protected ProcessResult innerProcessResult(CrawlURI curi) {
        try {
            if (shouldWrite(curi)) {
                return write(curi);
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
    
    protected ProcessResult write(final CrawlURI curi)
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

            writeRecords(curi, writer);
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

    protected void writeRecords(CrawlURI curi, WARCWriter writer) throws IOException {
        URI concurrentTo = null;
        for (WARCRecordBuilder recordBuilder: getChain()) {
            if (recordBuilder.shouldBuildRecord(curi)) {
                WARCRecordInfo record = recordBuilder.buildRecord(curi, concurrentTo);
                if (record != null) {
                    writer.writeRecord(record);
                    if (concurrentTo == null) {
                        concurrentTo = record.getRecordId();
                    }
                }
            }
        }
    }
}
