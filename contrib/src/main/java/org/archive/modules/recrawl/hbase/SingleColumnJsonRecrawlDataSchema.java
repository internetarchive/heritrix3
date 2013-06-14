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
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.httpclient.HttpMethod;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;
import org.archive.modules.CrawlURI;
import org.archive.modules.fetcher.FetchStatusCodes;
import org.archive.modules.recrawl.FetchHistoryHelper;
import org.archive.modules.recrawl.RecrawlAttributeConstants;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * {@linkplain SingleColumnJsonRecrawlDataSchema} stores all re-crawl data properties in a single column,
 * in JSON format. As HBase stores each column paired with the row key, it takes a lot of space to store
 * each re-crawl data property in its own column.
 * <ul>
 * <li>{@code r}: re-crawl data in JSON format</li>
 * <li>{@code z}: do-not-crawl flag - loader discards URL if this column has non-empty value.</li>
 * </ul> 
 * @contributor Kenji Nagahashi
 */
public class SingleColumnJsonRecrawlDataSchema extends RecrawlDataSchemaBase
implements RecrawlDataSchema {
    static final Logger logger = Logger.getLogger(SingleColumnJsonRecrawlDataSchema.class.getName());

    public static byte[] DEFAULT_COLUMN = Bytes.toBytes("r");

    // JSON property names for re-crawl data properties
    public static final String PROPERTY_STATUS = "s";
    public static final String PROPERTY_CONTENT_DIGEST = "d";
    public static final String PROPERTY_ETAG = "e";
    public static final String PROPERTY_LAST_MODIFIED = "m";

    // SHA1 scheme is assumed.
    public static final String CONTENT_DIGEST_SCHEME = "sha1:";

    // single column for storing JSON of re-crawl data
    protected byte[] column = DEFAULT_COLUMN;
    public void setColumn(String column) {
        this.column = Bytes.toBytes(column);
    }
    public String getColumn() {
        return Bytes.toString(column);
    }

    /* (non-Javadoc)
     * @see org.archive.modules.hq.recrawl.RecrawlDataSchema#createPut(org.archive.modules.CrawlURI)
     */
    public Put createPut(CrawlURI uri) {
        byte[] key = rowKeyForURI(uri);
        Put p = new Put(key);
        JSONObject jo = new JSONObject();
        try {
            // TODO should we post warning message when scheme != "sha1"?
            String digest = uri.getContentDigestString();
            if (digest != null) {
                jo.put(PROPERTY_CONTENT_DIGEST, digest);
            }
            jo.put(PROPERTY_STATUS, uri.getFetchStatus());
            HttpMethod method = uri.getHttpMethod();
            if (method != null) {
                String etag = FetchHistoryHelper.getHeaderValue(method, RecrawlAttributeConstants.A_ETAG_HEADER);
                if (etag != null) {
                    // Etag is usually quoted
                    if (etag.length() >= 2 && etag.charAt(0) == '"' && etag.charAt(etag.length() - 1) == '"')
                        etag = etag.substring(1, etag.length() - 1);
                    jo.put(PROPERTY_ETAG, etag);
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
                } else {
                    try {
                        long completed = uri.getFetchCompletedTime();
                        if (completed != 0)
                            jo.put(PROPERTY_LAST_MODIFIED, completed);
                    } catch (NullPointerException ex) {
                        logger.warning("CrawlURI.getFetchCompletedTime():" + ex + " for " + uri.shortReportLine());
                    }
                }
            }
        } catch (JSONException ex) {
            // should not happen - all values are either primitive or String.
            logger.log(Level.SEVERE, "JSON translation failed", ex);
        }
        p.add(columnFamily, column, Bytes.toBytes(jo.toString()));
        return p;
    }

    /* (non-Javadoc)
     * @see org.archive.modules.hq.recrawl.RecrawlDataSchema#load(org.apache.hadoop.hbase.client.Result)
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

        KeyValue rkv = result.getColumnLatest(columnFamily, column);
        long timestamp = rkv.getTimestamp();
        Map<String, Object> history = FetchHistoryHelper.getFetchHistory(curi, timestamp, historyLength);
        if (history == null) {
            // crawl history array is fully occupied by crawl history entries
            // newer than timestamp.
            return;
        }
        byte[] jsonBytes = rkv.getValue();
        if (jsonBytes != null) {
            JSONObject jo = null;
            try {
                jo = new JSONObject(Bytes.toString(jsonBytes));
            } catch (JSONException ex) {
                logger.warning(String.format("JSON parsing failed for key %1s: %2s", 
                        result.getRow(), ex.getMessage()));
            }
            if (jo != null) {
                int status = jo.optInt(PROPERTY_STATUS, -1);
                if (status >= 0) {
                    history.put(RecrawlAttributeConstants.A_STATUS, status);
                }
                String digest = jo.optString(PROPERTY_CONTENT_DIGEST);
                if (digest != null) {
                    history.put(RecrawlAttributeConstants.A_CONTENT_DIGEST, CONTENT_DIGEST_SCHEME + digest);
                }
                String etag = jo.optString(PROPERTY_ETAG);
                if (etag != null) {
                    history.put(RecrawlAttributeConstants.A_ETAG_HEADER, etag);
                }
                long lastmod = jo.optLong(PROPERTY_LAST_MODIFIED);
                if (lastmod > 0) {
                    history.put(RecrawlAttributeConstants.A_LAST_MODIFIED_HEADER, FetchHistoryHelper.formatHttpDate(lastmod));
                }
            }
        }
    }

}
