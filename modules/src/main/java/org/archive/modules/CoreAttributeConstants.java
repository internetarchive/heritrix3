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
package org.archive.modules;


/**
 * Attribute keys and constant strings used by the core crawler
 * classes.
 *
 * @contributor gojomo
 * @contributor pjack
 */
public interface CoreAttributeConstants {

    /**
     * Extracted MIME type of fetched content; should be
     * set immediately by fetching module if possible
     * (rather than waiting for a later analyzer)
     */
    public static String A_CONTENT_TYPE = "content-type";

    /**
     * Multiplier of last fetch duration to wait before
     * fetching another item of the same class (eg host)
     */
    public static String A_DELAY_FACTOR = "delay-factor";
    /**
     * Minimum delay before fetching another item of th
     * same class (eg host). Even if lastFetchTime*delayFactor
     * is less than this, this period will be waited.
     */
    public static String A_MINIMUM_DELAY = "minimum-delay";

    public static String A_RRECORD_SET_LABEL = "dns-records";
    public static String A_DNS_FETCH_TIME    = "dns-fetch-time";
    public static String A_DNS_SERVER_IP_LABEL = "dns-server-ip";
    public static final String A_FETCH_BEGAN_TIME= "fetch-began-time";
    public static String A_FETCH_COMPLETED_TIME = "fetch-completed-time";

    public static String A_RUNTIME_EXCEPTION = "runtime-exception";
    public static String A_NONFATAL_ERRORS = "nonfatal-errors";

    /** shorthand string tokens indicating notable occurrences,
     * separated by commas */
    public static String A_ANNOTATIONS = "annotations";

    public static String A_PREREQUISITE_URI = "prerequisite-uri";
    public static String A_DISTANCE_FROM_SEED = "distance-from-seed";
    public static String A_HTML_BASE = "html-base-href";
    public static String A_RETRY_DELAY = "retry-delay";

    /** 
     * Define for org.archive.crawler.writer.MirrorWriterProcessor.
     */
    public static String A_MIRROR_PATH = "mirror-path";

    /**
     * Key to get credential avatars from A_LIST.
     */
    public static final String A_CREDENTIALS_KEY = "credentials";
    
    /** a 'source' (usu. URI) that's inherited by discovered URIs */
    public static String A_SOURCE_TAG = "source";
    
    /**
     * Key to (optional) attribute specifying a list of keys that
     * are passed to CandidateURIs that 'descend' (are discovered 
     * via) this URI. 
     */
    public static final String A_HERITABLE_KEYS = "heritable";
    
    /** flag indicating the containing queue should be retired */ 
    public static final String A_FORCE_RETIRE = "force-retire";
    
    /** key to attribute containing pre-calculated precedence */
    public static final String A_PRECALC_PRECEDENCE = "precalc-precedence";
    
    /** local override of proxy host */ 
    public static final String A_HTTP_PROXY_HOST = "http-proxy-host";
    /** local override of proxy port */ 
    public static final String A_HTTP_PROXY_PORT = "http-proxy-port";

    /**
     * Fetch truncation codes present in {@link CrawlURI} annotations.
     * All truncation annotations have a <code>TRUNC_SUFFIX</code> suffix (TODO:
     * Make for-sure unique or redo truncation so definitive flag marked
     * against {@link CrawlURI}).
     */
    public static final String TRUNC_SUFFIX = "Trunc";
    // headerTrunc
    public static final String HEADER_TRUNC = "header" + TRUNC_SUFFIX; 
    // timeTrunc
    public static final String TIMER_TRUNC = "time" + TRUNC_SUFFIX;
    // lenTrunc
    public static final String LENGTH_TRUNC = "len" + TRUNC_SUFFIX;

    public static final String A_FTP_CONTROL_CONVERSATION = "ftp-control-conversation";
    public static final String A_FTP_FETCH_STATUS = "ftp-fetch-status";

    public static final String A_WHOIS_SERVER_IP = "whois-server-ip";
    
    public static final String A_HTTP_AUTH_CHALLENGES = "http-auth-challenges";
    
    // FORMS support - a persistent member (survives frontier enqueue/dequeue/retries)
    public static final String A_SUBMIT_DATA = "submit-data";
    
    // arbitrary additions to WARC response record headers
    public static final String A_WARC_RESPONSE_HEADERS = "warc-response-headers";

}
