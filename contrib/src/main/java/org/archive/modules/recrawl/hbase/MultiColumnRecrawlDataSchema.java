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
package org.archive.modules.recrawl.hbase;

import java.util.Map;
import java.util.logging.Logger;

import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;
import org.archive.modules.CrawlURI;
import org.archive.modules.fetcher.FetchStatusCodes;
import org.archive.modules.recrawl.FetchHistoryHelper;
import org.archive.modules.recrawl.RecrawlAttributeConstants;

/**
 * RecrawlDataSchema that stores each recrawl data properties in a separate column in single column
 * family, whose name may be configured with {@link #setColumnFamily(String)} (default "f").
 * <ul>
 * <li>{@code s}: fetch status (as integer text)</li>
 * <li>{@code d}: content digest (with {@code sha1:} prefix, Base32 text)</li>
 * <li>{@code e}: ETag (enclosing quotes stripped)</li>
 * <li>{@code m}: last-modified date-time (as integer timestamp, binary format)</li>
 * <li>{@code z}: do-not-crawl flag - loader discards URL if this column has non-empty value.</li> 
 * </ul>
 * 
 * @contributor kenji
 */
public class MultiColumnRecrawlDataSchema extends RecrawlDataSchemaBase implements RecrawlDataSchema, RecrawlAttributeConstants {
    static final Logger logger = Logger.getLogger(MultiColumnRecrawlDataSchema.class.getName());

    public static final byte[] COLUMN_STATUS = Bytes.toBytes("s");
    public static final byte[] COLUMN_CONTENT_DIGEST = Bytes.toBytes("d");
    public static final byte[] COLUMN_ETAG = Bytes.toBytes("e");
    public static final byte[] COLUMN_LAST_MODIFIED = Bytes.toBytes("m");

    /* (non-Javadoc)
     * @see org.archive.modules.hq.recrawl.RecrawlDataSchema#createPut()
     */
    public Put createPut(CrawlURI uri) {
        byte[] uriBytes = rowKeyForURI(uri);
        byte[] key = uriBytes;
        Put p = new Put(key);
        String digest = uri.getContentDigestSchemeString();
        if (digest != null) {
            p.add(columnFamily, COLUMN_CONTENT_DIGEST, Bytes.toBytes(digest));
        }
        p.add(columnFamily, COLUMN_STATUS, Bytes.toBytes(Integer.toString(uri.getFetchStatus())));
        org.apache.commons.httpclient.HttpMethod method = uri.getHttpMethod();
        if (method != null) {
            String etag = FetchHistoryHelper.getHeaderValue(method, RecrawlAttributeConstants.A_ETAG_HEADER);
            if (etag != null) {
                // Etqg is usually quoted
                if (etag.length() >= 2 && etag.charAt(0) == '"' && etag.charAt(etag.length() - 1) == '"')
                    etag = etag.substring(1, etag.length() - 1);
                p.add(columnFamily, COLUMN_ETAG, Bytes.toBytes(etag));
            }
            String lastmod = FetchHistoryHelper.getHeaderValue(method, RecrawlAttributeConstants.A_LAST_MODIFIED_HEADER);
            if (lastmod != null) {
                long lastmod_sec = FetchHistoryHelper.parseHttpDate(lastmod);
                if (lastmod_sec == 0) {
                    try {
                        lastmod_sec = uri.getFetchCompletedTime();
                    } catch (NullPointerException ex) {
                        logger.warning("CrawlURI.getFetchCompletedTime():" + ex + " for " + uri.shortReportLine());
                    }
                }
                if (lastmod_sec != 0)
                    p.add(columnFamily, COLUMN_LAST_MODIFIED, Bytes.toBytes(lastmod_sec));
            } else {
                try {
                    long completed = uri.getFetchCompletedTime();
                    if (completed != 0)
                        p.add(columnFamily, COLUMN_LAST_MODIFIED, Bytes.toBytes(completed));
                } catch (NullPointerException ex) {
                    logger.warning("CrawlURI.getFetchCompletedTime():" + ex + " for " + uri.shortReportLine());
                }
            }
        }
        return p;
    }

    /* (non-Javadoc)
     * @see org.archive.modules.hq.recrawl.RecrawlDataSchema#load(java.util.Map, org.apache.hadoop.hbase.client.Result)
     */
    public void load(Result result, CrawlURI curi) {
        // check for "do-not-crawl" flag - any non-empty data tells not to crawl this
        // URL.
        byte[] nocrawl = result.getValue(columnFamily, COLUMN_NOCRAWL);
        if (nocrawl != null && nocrawl.length > 0) {
            // fetch status set to S_DEEMED_CHAFF, because this do-not-crawl flag
            // is primarily intended for preventing crawler from stepping on traps.
            curi.setFetchStatus(FetchStatusCodes.S_DEEMED_CHAFF);
            curi.getAnnotations().add("nocrawl");
            return;
        }
        // all column should have identical timestamp.
        KeyValue rkv = result.getColumnLatest(columnFamily, COLUMN_STATUS);
        long timestamp = rkv.getTimestamp();
        Map<String, Object> history = FetchHistoryHelper.getFetchHistory(curi, timestamp, historyLength);
        // FetchHTTP ignores history with status <= 0
        byte[] status = result.getValue(columnFamily, COLUMN_STATUS);
        if (status != null) {
            // Note that status is stored as integer text. It's typically three-chars
            // that is less than 4-byte integer bits.
            history.put(RecrawlAttributeConstants.A_STATUS, Integer.parseInt(Bytes.toString(status)));
            byte[] etag = result.getValue(columnFamily, COLUMN_ETAG);
            if (etag != null) {
                history.put(RecrawlAttributeConstants.A_ETAG_HEADER, Bytes.toString(etag));
            }
            byte[] lastmod = result.getValue(columnFamily, COLUMN_LAST_MODIFIED);
            if (lastmod != null) {
                long lastmod_sec = Bytes.toLong(lastmod);
                history.put(RecrawlAttributeConstants.A_LAST_MODIFIED_HEADER, FetchHistoryHelper.formatHttpDate(lastmod_sec));
            }
            byte[] digest = result.getValue(columnFamily, COLUMN_CONTENT_DIGEST);
            if (digest != null) {
                history.put(RecrawlAttributeConstants.A_CONTENT_DIGEST, Bytes.toString(digest));
            }
        }
    }

}
