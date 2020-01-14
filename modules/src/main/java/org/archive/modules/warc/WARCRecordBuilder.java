package org.archive.modules.warc;

import java.io.IOException;
import java.net.URI;

import org.archive.io.warc.WARCRecordInfo;
import org.archive.modules.CrawlURI;

/**
 * Implementations of this interface are each responsible for building a
 * particular type of WARC record.
 *
 * @author nlevitt
 */
public interface WARCRecordBuilder {

    /**
     * Decides whether to build a record for the given capture.
     *
     * <p>
     * For example, {@link DnsResponseRecordBuilder#shouldBuildRecord(CrawlURI)}
     * will return true if and only if <code>curi</code> is a capture of a dns: url.
     *
     * @param curi a captured url
     * @return <code>true</code> if it is appropriate for this
     *         {@link WARCRecordBuilder} to build a record for this capture,
     *         <code>false</code> otherwise
     */
    boolean shouldBuildRecord(CrawlURI curi);

    /**
     * Builds a warc record for this capture.
     *
     * @param curi a captured url
     * @param concurrentTo implementations should do this:
     * <pre>    if (concurrentTo != null) {
     *        recordInfo.addExtraHeader(HEADER_KEY_CONCURRENT_TO,
     *                "<" + concurrentTo + ">");
     *    }</pre>
     * @return the freshly built warc record
     * @throws IOException
     */
    WARCRecordInfo buildRecord(CrawlURI curi, URI concurrentTo)
            throws IOException;

}