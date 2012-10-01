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

package org.archive.modules.recrawl;

/**
 * 
 * @author pjack
 *
 */
public interface RecrawlAttributeConstants {

    /* Duplication-reduction / recrawl / history constants */
    
    /** fetch history array */ 
    public static final String A_FETCH_HISTORY = "fetch-history";
    /** content digest */
    public static final String A_CONTENT_DIGEST = "content-digest";
    /** header name (and AList key) for last-modified timestamp */
    public static final String A_LAST_MODIFIED_HEADER = "last-modified";
    /** header name (and AList key) for ETag */
    public static final String A_ETAG_HEADER = "etag"; 
    /** key for status (when in history) */
    public static final String A_STATUS = "status"; 
    /** reference length (content length or virtual length */
    public static final String A_REFERENCE_LENGTH = "reference-length";
    
    // constants for uri-agnostic content digest based dedupe
    /** content digest history map */
    public static final String A_CONTENT_DIGEST_HISTORY = "content-digest-history";
    /** url that the content payload was written for */
    public static final String A_ORIGINAL_URL = "original-url";
    /** warc record id of warc record with the content payload */
    public static final String A_WARC_RECORD_ID = "warc-record-id";
    /** warc filename containing the content payload */
    public static final String A_WARC_FILENAME = "warc-filename";
    /** offset into warc file of warc record with content payload */
    public static final String A_WARC_FILE_OFFSET = "warc-file-offset";
    /** date content payload was written */
    public static final String A_ORIGINAL_DATE = "content-written-date";
    /** number of times we've seen this content digest (1 original + n duplicates) */
    public static final String A_CONTENT_DIGEST_COUNT = "content-digest-count";

    /**
     * Writer processors of all types are encouraged to put a 'writeTag'
     * (analogous to HTTP 'etag') in the CrawlURI state. Its contents are
     * opaque/private-to-the-writer, but might generally be a
     * WARC-name/offset/UUID/etc, and their mere presence means content is
     * written somewhere. A writer processor that decides not to write fresh
     * content at all, not even a revisit record, because it sees previous
     * sufficient writeTag in history, will usually copy that forward to latest
     * history record. {@link PersistLogProcessor}/{@link PersistStoreProcessor}
     * have an option {@link PersistProcessor#onlyStoreIfWriteTagPresent}, which
     * defaults to true.
     */
   public static final String A_WRITE_TAG = "write-tag";
}
