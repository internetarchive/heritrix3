/* Copyright (C) 2003 Internet Archive.
 *
 * This file is part of the Heritrix web crawler (crawler.archive.org).
 *
 * Heritrix is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * any later version.
 *
 * Heritrix is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser Public License
 * along with Heritrix; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * FetchStatusCodes.java
 * Created on Jun 19, 2003
 *
 * $Header$
 */
package org.archive.modules.fetcher;

/**
 * Constant flag codes to be used, in lieu of per-protocol
 * codes (like HTTP's 200, 404, etc.), when network/internal/
 * out-of-band conditions occur.
 *
 * The URISelector may use such codes, along with user-configured
 * options, to determine whether, when, and how many times
 * a CrawlURI might be reattempted.
 *
 * @author gojomo
 *
 */
public interface FetchStatusCodes {
    /** fetch never tried (perhaps protocol unsupported or illegal URI) */
    public static final int S_UNATTEMPTED = 0;
    /** DNS lookup failed */
    public static final int S_DOMAIN_UNRESOLVABLE = -1;  //
    /** HTTP connect failed */
    public static final int S_CONNECT_FAILED = -2;       //
    /** HTTP connect broken */
    public static final int S_CONNECT_LOST = -3;         //
    /** HTTP timeout (before any meaningful response received) */
    public static final int S_TIMEOUT = -4;              //
    /** Unexpected runtime exception; see runtime-errors.log */
    public static final int S_RUNTIME_EXCEPTION = -5;    //
    /** DNS prerequisite failed, precluding attempt */
    public static final int S_DOMAIN_PREREQUISITE_FAILURE = -6; //
    /** URI recognized as unsupported or illegal)  */
    public static final int S_UNFETCHABLE_URI = -7;      //
    /** multiple retries all failed */
    public static final int S_TOO_MANY_RETRIES = -8;     //

    /** temporary status assigned URIs awaiting preconditions; appearance in
     *  logs is a bug */
    public static final int S_DEFERRED = -50;
    /** URI could not be queued in Frontier; when URIs are properly
     * filtered for format, should never occur */
    public static final int S_UNQUEUEABLE = -60;
    
    /** Robots prerequisite failed, precluding attempt */
    public static final int S_ROBOTS_PREREQUISITE_FAILURE = -61; //
    /** DNS prerequisite failed, precluding attempt */
    public static final int S_OTHER_PREREQUISITE_FAILURE = -62; //
    /** DNS prerequisite failed, precluding attempt */
    public static final int S_PREREQUISITE_UNSCHEDULABLE_FAILURE = -63; //
    
    /** severe java 'Error' conditions (OutOfMemoryError, StackOverflowError,
     *  etc.) during URI processing */
    public static final int S_SERIOUS_ERROR = -3000;     //

    /** 'chaff' detection of traps/content of negligible value applied */
    public static final int S_DEEMED_CHAFF = -4000;
    /** overstepped link hops */
    public static final int S_TOO_MANY_LINK_HOPS = -4001;
    /** overstepped embed/trans hops */
    public static final int S_TOO_MANY_EMBED_HOPS = -4002;
    /** out-of-scope upoin reexamination (only when scope changes during
     *  crawl) */
    public static final int S_OUT_OF_SCOPE = -5000;
    /** blocked from fetch by user setting. */
    public static final int S_BLOCKED_BY_USER = -5001;
    /**
     * Blocked by custom prefetcher processor.
     * A check against scope or against filters in a custom prefetch
     * processor rules CrawlURI should not be crawled.
     * TODO: Add to documentation and help page.
     */
    public static final int S_BLOCKED_BY_CUSTOM_PROCESSOR = -5002;
    /**
     * Blocked due to exceeding an established quota.
     * TODO: Add to documentation and help page.
     */
    public static final int S_BLOCKED_BY_QUOTA = -5003;
    /**
     * Blocked due to exceeding an established runtime.
     * TODO: Add to documentation and help page.
     */
    public static final int S_BLOCKED_BY_RUNTIME_LIMIT = -5004;
    /** deleted from frontier by user */
    public static final int S_DELETED_BY_USER = -6000;

    /** Processing thread was killed */
    public static final int S_PROCESSING_THREAD_KILLED = -7000;

    /** robots rules precluded fetch */
    public static final int S_ROBOTS_PRECLUDED = -9998;

    /** DNS success */
    public static final int S_DNS_SUCCESS = 1;
    /** InetAddress.getByName success */
    public static final int S_GETBYNAME_SUCCESS = 1001;
}


