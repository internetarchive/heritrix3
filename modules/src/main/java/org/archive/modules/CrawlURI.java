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

import org.archive.url.URIException;
import org.apache.commons.lang3.StringUtils;
import org.archive.bdb.AutoKryo;
import org.archive.modules.credential.Credential;
import org.archive.modules.credential.HttpAuthenticationCredential;
import org.archive.modules.extractor.HTMLLinkContext;
import org.archive.modules.extractor.Hop;
import org.archive.modules.extractor.LinkContext;
import org.archive.modules.revisit.RevisitProfile;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.spring.OverlayContext;
import org.archive.spring.OverlayMapsSource;
import org.archive.util.Base32;
import org.archive.util.Recorder;
import org.archive.util.ReportUtils;
import org.archive.util.Reporter;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.archive.modules.CoreAttributeConstants.*;
import static org.archive.modules.SchedulingConstants.NORMAL;
import static org.archive.modules.fetcher.FetchStatusCodes.*;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_CONTENT_DIGEST_HISTORY;


/**
 * Represents a candidate URI and the associated state it
 * collects as it is crawled.
 *
 * <p>Core state is in instance variables but a flexible
 * attribute list is also available. Use this 'bucket' to carry
 * custom processing extracted data and state across CrawlURI
 * processing.  See {@link #getData()}, etc.
 *
 * <p>
 * Note: getHttpMethod() has been removed starting with Heritrix 3.3.0. HTTP
 * response headers are available using {@link #getHttpResponseHeader(String)}.
 * (HTTP fetchers are responsible for setting the values using
 * {@link #putHttpResponseHeader(String, String)}).
 *
 * @author Gordon Mohr
 */
public class CrawlURI 
implements Reporter, Serializable, OverlayContext, Comparable<CrawlURI> {
    private static final long serialVersionUID = 4L;

    private static final Logger logger =
        Logger.getLogger(CrawlURI.class.getName());

    public static final int UNCALCULATED = -1;
    /** fetch history array */
    public static final String A_FETCH_HISTORY = "fetch-history";
    
    public static enum FetchType { HTTP_GET, HTTP_POST, UNKNOWN };

    /**
     * The URI being crawled.  It's transient to save space when storing to BDB.
     */
    private UURI uuri;

    
    /** Seed status */
    private boolean isSeed = false;

    
    /** String of letters indicating how this URI was reached from a seed.
     * <pre>
     * P precondition
     * R redirection
     * E embedded (as frame, src, link, codebase, etc.)
     * X speculative embed (as from javascript, some alternate-format extractors
     * L link</pre>
     * For example LLLE (an embedded image on a page 3 links from seed).
     */
    private String pathFromSeed;
    
    /**
     * Where this URI was (presently) discovered. . Transient to allow
     * more efficient custom serialization
     */
    private UURI via;

    /**
     * Context of URI's discovery, as per the 'context' in Link
     */
    private LinkContext viaContext;

    
    private int schedulingDirective = NORMAL;

    
    /**
     * Frontier/Scheduler lifecycle info.
     * This is an identifier set by the Frontier for its
     * purposes. Usually its the name of the Frontier queue
     * this URI gets queued to.  Values can be host + port
     * or IP, etc.
     */
    private String classKey;

    /** assigned precedence */
    private int precedence;
    
    // Processing progress
    private int fetchStatus = 0;    // default to unattempted
    private int deferrals = 0;     // count of postponements for prerequisites
    private int fetchAttempts = 0; // the number of fetch attempts that have been made
    transient private int threadNumber;

    // User agent to masquerade as when crawling this URI. If null, globals should be used
    private String userAgent = null;

    // Once a link extractor has finished processing this curi this will be
    // set as true
    transient private boolean linkExtractorFinished = false;
   
    transient private int discardedOutlinks = 0; 
    
    private long contentSize = UNCALCULATED;
    private long contentLength = UNCALCULATED;

    
    /**
     * Flexible dynamic attributes list.
     * <p>
     * The attribute list is a flexible map of key/value pairs for storing
     * status of this URI for use by other processors. By convention the
     * attribute list is keyed by constants found in the
     * {@link CoreAttributeConstants} interface.  Use this list to carry
     * data or state produced by custom processors rather change the
     * classes {@link CrawlURI} or this class, CrawlURI.
     */
    protected Map<String,Object> data;

    private boolean forceRevisit = false; // even if already visited

    
    /**
     * Current http recorder.
     *
     * Gets set upon successful request.  Reset at start of processing chain.
     */
    private transient Recorder httpRecorder = null;

    /**
     * Content type of a successfully fetched URI.
     *
     * May be null even on successfully fetched URI.
     */
    private String contentType = "unknown";

    /**
     * True if this CrawlURI has been deemed a prerequisite by the
     * org.archive.crawler.prefetch.PreconditionEnforcer.
     *
     * This flag is used at least inside in the precondition enforcer so that
     * subsequent prerequisite tests know to let this CrawlURI through because
     * its a prerequisite needed by an earlier prerequisite tests (e.g. If
     * this is a robots.txt, then the subsequent login credentials prereq
     * test must not throw it out because its not a login curi).
     */
    private boolean prerequisite = false;
    
    /** specified fetch-type: GET, POST, or not-yet-known */ 
    private FetchType fetchType = FetchType.UNKNOWN;

    /** 
     * Monotonically increasing number within a crawl;
     * useful for tending towards breadth-first ordering.
     * Will sometimes be truncated to 48 bits, so behavior
     * over 281 trillion instantiated CrawlURIs may be 
     * buggy
     */
    protected long ordinal;

    /** maximum length for pathFromSeed/hopsPath; longer truncated with leading counter **/ 
    private static final int MAX_HOPS_DISPLAYED = 50;

    /**
     * A digest (hash, usually SHA1) of retrieved content-body. 
     * 
     */
    private byte[] contentDigest = null;
    private String contentDigestScheme = null;

    
    /**
     * If this value is non-null, a determination has been made that this CrawlURI instance is a revisit or 
     * recrawl. Details are provided by the RevisitProfile object. 
     */
    transient private RevisitProfile revisitProfile = null;

    /**
     * Create a new instance of CrawlURI from a {@link UURI}.
     *
     * @param uuri the UURI to base this CrawlURI on.
     */
    public CrawlURI(UURI uuri) {
        this.uuri = uuri;
        this.pathFromSeed = "";
    }

    public static CrawlURI fromHopsViaString(String uriHopsViaContext) throws URIException {
        UURI u;
        String args[] = uriHopsViaContext.split("\\s+");
        u = UURIFactory.getInstance(args[0]);
        String pathFromSeed = (args.length > 1)?
            args[1].toString() : "";
        UURI via = (args.length > 2 && args[2].length()>1) ?
            UURIFactory.getInstance(args[2].toString()):
            null;
        LinkContext viaContext = (args.length > 3 && args[2].length()>1) ?
                HTMLLinkContext.get(args[3].toString()): null;
        CrawlURI caUri = new CrawlURI(u, pathFromSeed, via, viaContext);
        return caUri;
    }
    
    /**
     * @param u uuri instance this CrawlURI wraps.
     * @param pathFromSeed
     * @param via
     * @param viaContext
     */
    public CrawlURI(UURI u, String pathFromSeed, UURI via,
            LinkContext viaContext) {
        this.uuri = u;
        if (pathFromSeed != null) {
            this.pathFromSeed = pathFromSeed;
        } else {
            this.pathFromSeed = "";
        }
        this.via = via;
        this.viaContext = viaContext;
    }

    /**
     * @return Returns the schedulingDirective.
     */
    public int getSchedulingDirective() {
        return schedulingDirective;
    }


    /** 
     * @param priority The schedulingDirective to set.
     */
    public void setSchedulingDirective(int priority) {
        this.schedulingDirective = priority;
    }

    
    public boolean containsDataKey(String key) {
        if (data == null) {
            return false;
        }
        return data.containsKey(key);
    }


    /**
     * Takes a status code and converts it into a human readable string.
     *
     * @param code the status code
     * @return a human readable string declaring what the status code is.
     */
    public static String fetchStatusCodesToString(int code){
        switch(code){
            // DNS
            case S_DNS_SUCCESS : return "DNS-1-OK";
            // HTTP Informational 1xx
            case 100  : return "HTTP-100-Info-Continue";
            case 101  : return "HTTP-101-Info-Switching Protocols";
            // HTTP Successful 2xx
            case 200  : return "HTTP-200-Success-OK";
            case 201  : return "HTTP-201-Success-Created";
            case 202  : return "HTTP-202-Success-Accepted";
            case 203  : return "HTTP-203-Success-Non-Authoritative";
            case 204  : return "HTTP-204-Success-No Content ";
            case 205  : return "HTTP-205-Success-Reset Content";
            case 206  : return "HTTP-206-Success-Partial Content";
            // HTTP Redirection 3xx
            case 300  : return "HTTP-300-Redirect-Multiple Choices";
            case 301  : return "HTTP-301-Redirect-Moved Permanently";
            case 302  : return "HTTP-302-Redirect-Found";
            case 303  : return "HTTP-303-Redirect-See Other";
            case 304  : return "HTTP-304-Redirect-Not Modified";
            case 305  : return "HTTP-305-Redirect-Use Proxy";
            case 307  : return "HTTP-307-Redirect-Temporary Redirect";
            // HTTP Client Error 4xx
            case 400  : return "HTTP-400-ClientErr-Bad Request";
            case 401  : return "HTTP-401-ClientErr-Unauthorized";
            case 402  : return "HTTP-402-ClientErr-Payment Required";
            case 403  : return "HTTP-403-ClientErr-Forbidden";
            case 404  : return "HTTP-404-ClientErr-Not Found";
            case 405  : return "HTTP-405-ClientErr-Method Not Allowed";
            case 407  : return "HTTP-406-ClientErr-Not Acceptable";
            case 408  : return "HTTP-407-ClientErr-Proxy Authentication Required";
            case 409  : return "HTTP-408-ClientErr-Request Timeout";
            case 410  : return "HTTP-409-ClientErr-Conflict";
            case 406  : return "HTTP-410-ClientErr-Gone";
            case 411  : return "HTTP-411-ClientErr-Length Required";
            case 412  : return "HTTP-412-ClientErr-Precondition Failed";
            case 413  : return "HTTP-413-ClientErr-Request Entity Too Large";
            case 414  : return "HTTP-414-ClientErr-Request-URI Too Long";
            case 415  : return "HTTP-415-ClientErr-Unsupported Media Type";
            case 416  : return "HTTP-416-ClientErr-Requested Range Not Satisfiable";
            case 417  : return "HTTP-417-ClientErr-Expectation Failed";
            // HTTP Server Error 5xx
            case 500  : return "HTTP-500-ServerErr-Internal Server Error";
            case 501  : return "HTTP-501-ServerErr-Not Implemented";
            case 502  : return "HTTP-502-ServerErr-Bad Gateway";
            case 503  : return "HTTP-503-ServerErr-Service Unavailable";
            case 504  : return "HTTP-504-ServerErr-Gateway Timeout";
            case 505  : return "HTTP-505-ServerErr-HTTP Version Not Supported";
            // Heritrix internal codes (all negative numbers
            case S_BLOCKED_BY_USER:
                return "Heritrix(" + S_BLOCKED_BY_USER + ")-Blocked by user";
            case S_BLOCKED_BY_CUSTOM_PROCESSOR:
                return "Heritrix(" + S_BLOCKED_BY_CUSTOM_PROCESSOR +
                ")-Blocked by custom prefetch processor";
            case S_DELETED_BY_USER:
                return "Heritrix(" + S_DELETED_BY_USER + ")-Deleted by user";
            case S_CONNECT_FAILED:
                return "Heritrix(" + S_CONNECT_FAILED + ")-Connection failed";
            case S_CONNECT_LOST:
                return "Heritrix(" + S_CONNECT_LOST + ")-Connection lost";
            case S_DEEMED_CHAFF:
                return "Heritrix(" + S_DEEMED_CHAFF + ")-Deemed chaff";
            case S_DEFERRED:
                return "Heritrix(" + S_DEFERRED + ")-Deferred";
            case S_DOMAIN_UNRESOLVABLE:
                return "Heritrix(" + S_DOMAIN_UNRESOLVABLE
                        + ")-Domain unresolvable";
            case S_OUT_OF_SCOPE:
                return "Heritrix(" + S_OUT_OF_SCOPE + ")-Out of scope";
            case S_DOMAIN_PREREQUISITE_FAILURE:
                return "Heritrix(" + S_DOMAIN_PREREQUISITE_FAILURE
                        + ")-Domain prerequisite failure";
            case S_ROBOTS_PREREQUISITE_FAILURE:
                return "Heritrix(" + S_ROBOTS_PREREQUISITE_FAILURE
                        + ")-Robots prerequisite failure";
            case S_OTHER_PREREQUISITE_FAILURE:
                return "Heritrix(" + S_OTHER_PREREQUISITE_FAILURE
                        + ")-Other prerequisite failure";
            case S_PREREQUISITE_UNSCHEDULABLE_FAILURE:
                return "Heritrix(" + S_PREREQUISITE_UNSCHEDULABLE_FAILURE
                        + ")-Prerequisite unschedulable failure";
            case S_ROBOTS_PRECLUDED:
                return "Heritrix(" + S_ROBOTS_PRECLUDED + ")-Robots precluded";
            case S_RUNTIME_EXCEPTION:
                return "Heritrix(" + S_RUNTIME_EXCEPTION
                        + ")-Runtime exception";
            case S_SERIOUS_ERROR:
                return "Heritrix(" + S_SERIOUS_ERROR + ")-Serious error";
            case S_TIMEOUT:
                return "Heritrix(" + S_TIMEOUT + ")-Timeout";
            case S_TOO_MANY_EMBED_HOPS:
                return "Heritrix(" + S_TOO_MANY_EMBED_HOPS
                        + ")-Too many embed hops";
            case S_TOO_MANY_LINK_HOPS:
                return "Heritrix(" + S_TOO_MANY_LINK_HOPS
                        + ")-Too many link hops";
            case S_TOO_MANY_RETRIES:
                return "Heritrix(" + S_TOO_MANY_RETRIES + ")-Too many retries";
            case S_UNATTEMPTED:
                return "Heritrix(" + S_UNATTEMPTED + ")-Unattempted";
            case S_UNFETCHABLE_URI:
                return "Heritrix(" + S_UNFETCHABLE_URI + ")-Unfetchable URI";
            case S_PROCESSING_THREAD_KILLED:
                return "Heritrix(" + S_PROCESSING_THREAD_KILLED + ")-" +
                    "Processing thread killed";
            // Unknown return code
            default : return Integer.toString(code);
        }
    }


    /**
     * Return the overall/fetch status of this CrawlURI for its
     * current trip through the processing loop.
     *
     * @return a value from FetchStatusCodes
     */
    public int getFetchStatus(){
        return fetchStatus;
    }

    /**
     * Set the overall/fetch status of this CrawlURI for
     * its current trip through the processing loop.
     *
     * @param newstatus a value from FetchStatusCodes
     */
    public void setFetchStatus(int newstatus){
        fetchStatus = newstatus;
    }

    /**
     * Get the count of attempts (trips through the processing 
     * loop) at getting the document referenced by this URI. 
     * Compared against a configured maximum to determine when
     * to stop retrying. 
     * 
     * TODO: Consider renaming as something more generic, as all
     * processing-loops do not necessarily include an attempted
     * network-fetch (for example, when processing is aborted 
     * early to enqueue a prerequisite), and this counter may be 
     * reset if a URI is starting a fresh series of tries (as when
     * rescheduled at a future time). Perhaps simply 'tryCount'
     * or 'attempts'?
     *
     * @return attempts count
     */
    public int getFetchAttempts() {
        return fetchAttempts;
    }

    /**
     * Increment the count of attempts (trips through the processing 
     * loop) at getting the document referenced by this URI.
     */
    public void incrementFetchAttempts() {
        fetchAttempts++;
    }

    /**
     * Reset fetchAttempts counter.
     */
    public void resetFetchAttempts() {
        this.fetchAttempts = 0;
    }

    /**
     * Reset deferrals counter.
     */
    public void resetDeferrals() {
        this.deferrals = 0;
    }

    /**
     * Set a prerequisite for this URI.
     * <p>
     * A prerequisite is a URI that must be crawled before this URI can be
     * crawled.
     *
     * @param pre Link to set as prereq.
     */
    public void setPrerequisiteUri(CrawlURI pre) {
        getData().put(A_PREREQUISITE_URI, pre);
    }

    /**
     * Get the prerequisite for this URI.
     * <p>
     * A prerequisite is a URI that must be crawled before this URI can be
     * crawled.
     *
     * @return the prerequisite for this URI or null if no prerequisite.
     */
    public CrawlURI getPrerequisiteUri() {
        return (CrawlURI) getData().get(A_PREREQUISITE_URI);
    }
    
    /**
     * Clear prerequisite, if any.
     */
    public CrawlURI clearPrerequisiteUri() {
        return (CrawlURI) getData().remove(A_PREREQUISITE_URI);
    }
    
    /**
     * @return True if this CrawlURI has a prerequisite.
     */
    public boolean hasPrerequisiteUri() {
        return containsDataKey(A_PREREQUISITE_URI);
    }

    /**
     * Returns true if this CrawlURI is a prerequisite.
     *
     * TODO:FIXME: code elsewhere is confused whether this means 
     * that this CrawlURI is a prerquisite for another, or *has* a 
     * prequisite; clean up and rename as necessary. 
     * @return true if this CrawlURI is a prerequisite.
     */
    public boolean isPrerequisite() {
        return this.prerequisite;
    }

    /**
     * Set if this CrawlURI is itself a prerequisite URI.
     *
     * @param prerequisite True if this CrawlURI is itself a prerequiste uri.
     */
    public void setPrerequisite(boolean prerequisite) {
        this.prerequisite = prerequisite;
    }

    /**
     * Get the content type of this URI.
     *
     * @return Fetched URIs content type.  May be null.
     */
    public String getContentType() {
        return this.contentType;
    }

    /**
     * Set a fetched uri's content type.
     *
     * @param ct Contenttype.
     */
    public void setContentType(String ct) {
        if (ct == null) {
            ct = "unknown";
        }
        this.contentType = ct;
    }

    /**
     * Set the number of the ToeThread responsible for processing this uri.
     *
     * @param i the ToeThread number.
     */
    public void setThreadNumber(int i) {
        threadNumber = i;
    }

    /**
     * Get the number of the ToeThread responsible for processing this uri.
     *
     * @return the ToeThread number.
     */
    public int getThreadNumber() {
        return threadNumber;
    }

    /**
     * Increment the deferral count.
     *
     */
    public void incrementDeferrals() {
        deferrals++;
    }

    /**
     * Get the deferral count.
     *
     * @return the deferral count.
     */
    public int getDeferrals() {
        return deferrals;
    }

    /**
     * Remove all attributes set on this uri.
     * <p>
     * This methods removes the attribute list.
     */
    public void stripToMinimal() {
        data = null;
    }

    /**
     * Get the size in bytes of this URI's recorded content, inclusive
     * of things like protocol headers. It is the responsibility of the 
     * classes which fetch the URI to set this value accordingly -- it is 
     * not calculated/verified within CrawlURI. 
     * 
     * This value is consulted in reporting/logging/writing-decisions.
     * 
     * @see #setContentSize(long)
     * @return contentSize
     */
    public long getContentSize(){
        return contentSize;
    }

    /**
     * Get the annotations set for this uri.
     *
     * @return the annotations set for this uri.
     */
    public Collection<String> getAnnotations() {
        @SuppressWarnings("unchecked")
        Collection<String> annotations = (Collection<String>)getData().get(A_ANNOTATIONS);
        if (annotations == null) {
            annotations = new LinkedHashSet<String>();
            getData().put(A_ANNOTATIONS, annotations);
        }
        return annotations;
    }

    /**
     * Get total hops from seed. 
     * @return int hops count
     */
    public int getHopCount() {
        if(pathFromSeed.length()<=MAX_HOPS_DISPLAYED) {
            return pathFromSeed.length();
        }
        int plusIndex = pathFromSeed.indexOf('+');
        if(plusIndex<0) {
            // just in case old-style hops-paths slip through
            return pathFromSeed.length(); 
        }
        // return overflow number + remainder (remainder will be 
        // MAX_HOPS_DISPLAYED unless an exceptional condition)
        return Integer.parseInt(pathFromSeed.substring(0,plusIndex)) 
            + pathFromSeed.length()-(plusIndex+1);
    }
    
    /**
     * Get the embed hop count.
     *
     * @return the embed hop count.
     */
    public int getEmbedHopCount() {
        int embedHops = 0;
        for(int i = pathFromSeed.length()-1; i>=0; i--) {
            if(pathFromSeed.charAt(i)==Hop.NAVLINK.getHopChar()) {
                break;
            }
            embedHops++;
        }
        return embedHops;
    }

    /**
     * Get the link hop count.
     *
     * @return the link hop count.
     */
    public int getLinkHopCount() {
        int linkHops = 0;
        for(int i = pathFromSeed.length()-1; i>=0; i--) {
            if(pathFromSeed.charAt(i)==Hop.NAVLINK.getHopChar()) {
                linkHops++;
            }
        }
        return linkHops;
    }

    /**
     * Get the user agent to use for crawling this URI.
     *
     * If null the global setting should be used.
     *
     * @return user agent or null
     */
    public String getUserAgent() {
        return userAgent;
    }

    /**
     * Set the user agent to use when crawling this URI.
     *
     * If not set the global settings should be used.
     *
     * @param string user agent to use
     */
    public void setUserAgent(String string) {
        userAgent = string;
    }


    /**
     * For completed HTTP transactions, the length of the content-body.
     *
     * @return For completed HTTP transactions, the length of the content-body.
     */
    public long getContentLength() {
        if (this.contentLength < 0) {
            this.contentLength = (getRecorder() != null)?
                getRecorder().getResponseContentLength(): 0;
        }
        return this.contentLength;
    }
    
    /**
     * Get size of data recorded (transferred)
     * 
     * @return recorded data size
     */
    public long getRecordedSize() {
        return (getRecorder() != null) ? getRecorder()
                .getRecordedInput().getSize()
                // if unavailable fall back on content-size
                : getContentSize();
    }

    /**
     * Sets the 'content size' for the URI, which is considered inclusive of all
     * of all recorded material (such as protocol headers) or even material
     * 'virtually' considered (as in material from a previous fetch 
     * confirmed unchanged with a server). (In contrast, content-length 
     * matches the HTTP definition, that of the enclosed content-body.)
     * 
     * Should be set by a fetcher or other processor as soon as the final size
     * of recorded content is known. Setting to an artificial/incorrect value
     * may affect other reporting/processing.
     */
    public void setContentSize(long l) {
        contentSize = l;
    }

    /**
     * If true then a link extractor has already claimed this CrawlURI and
     * performed link extraction on the document content. This does not
     * preclude other link extractors that may have an interest in this
     * CrawlURI from also doing link extraction.
     * 
     * <p>There is an onus on link extractors to set this flag if they have
     * run.
     * 
     * @return True if a processor has performed link extraction on this
     * CrawlURI
     *
     * @see #linkExtractorFinished()
     */
    public boolean hasBeenLinkExtracted(){
        return linkExtractorFinished;
    }

    /**
     * Note that link extraction has been performed on this CrawlURI. A processor
     * doing link extraction should invoke this method once it has finished it's
     * work. It should invoke it even if no links are extracted. It should only
     * invoke this method if the link extraction was performed on the document
     * body (not the HTTP headers etc.).
     *
     * @see #hasBeenLinkExtracted()
     */
    public void linkExtractorFinished() {
        linkExtractorFinished = true;
        if(discardedOutlinks>0) {
            getAnnotations().add("dol:"+discardedOutlinks);
        }
    }

    /**
     * Notify CrawlURI it is about to be logged; opportunity
     * for self-annotation
     */
    public void aboutToLog() {
        if (fetchAttempts>1) {
            getAnnotations().add(fetchAttempts + "t");
        }
    }

    /**
     * Get the http recorder associated with this uri.
     *
     * @return Returns the httpRecorder.  May be null but its set early in
     * FetchHttp so there is an issue if its null.
     */
    public Recorder getRecorder() {
        return httpRecorder;
    }

    /**
     * Set the http recorder to be associated with this uri.
     *
     * @param httpRecorder The httpRecorder to set.
     */
    public void setRecorder(Recorder httpRecorder) {
        this.httpRecorder = httpRecorder;
    }

    /**
     * Return true if this is a http transaction.
     *
     * @return True if this is a http transaction.
     */
    public boolean isHttpTransaction() {
        return getFetchType().equals(FetchType.HTTP_GET) || getFetchType().equals(FetchType.HTTP_POST);
    }

    /**
     * Clean up after a run through the processing chain.
     *
     * Called on the end of processing chain by Frontier#finish.  Null out any
     * state gathered during processing.
     */
    public void processingCleanup() {
        this.httpRecorder = null;
        this.fetchStatus = S_UNATTEMPTED;
        this.setPrerequisite(false);
        this.clearPrerequisiteUri();
        this.contentSize = UNCALCULATED;
        this.contentLength = UNCALCULATED;
        // Clear 'links extracted' flag.
        this.linkExtractorFinished = false;
        
        extraInfo = null;
        outLinks = null;
        
        this.revisitProfile = null;
        
        // XXX er uh surprised this wasn't here before?
        fetchType = FetchType.UNKNOWN;
    }

    /**
     * @return Credential avatars.  Null if none set.
     */
    public Set<Credential> getCredentials() {
        @SuppressWarnings("unchecked")
        Set<Credential> r = (Set<Credential>)getData().get(A_CREDENTIALS_KEY);
        if (r == null) {
            r = new HashSet<Credential>();
            getData().put(A_CREDENTIALS_KEY, r);
        }
        return r;
    }

    /**
     * @return True if there are avatars attached to this instance.
     */
    public boolean hasCredentials() {
        return containsDataKey(A_CREDENTIALS_KEY);
    }


    /**
     * Ask this URI if it was a success or not.
     *
     * Only makes sense to call this method after execution of
     * HttpMethod#execute. Regard any status larger then 0 as success
     * except for below caveat regarding 401s.  Use {@link #is2XXSuccess()} if
     * looking for a status code in the 200 range.
     *
     * <p>401s caveat: If any rfc2617 credential data present and we got a 401
     * assume it got loaded in FetchHTTP on expectation that we're to go around
     * the processing chain again. Report this condition as a failure so we
     * get another crack at the processing chain only this time we'll be making
     * use of the loaded credential data.
     *
     * @return True if ths URI has been successfully processed.
     * @see #is2XXSuccess()
     */
    public boolean isSuccess() {
        boolean result = false;
        int statusCode = this.fetchStatus;
        if (statusCode == 401 && hasRfc2617Credential()) {
            result = false;
        } else {
            result = (statusCode > 0);
        }
        return result;
    }
    
    /**
     * @return True if status code is in the 2xx range.
     * @see #isSuccess()
     */
    public boolean is2XXSuccess() {
        return this.fetchStatus >= 200 && this.fetchStatus < 300;
    }

    /**
     * @return True if we have an rfc2617 payload.
     */
    public boolean hasRfc2617Credential() {
        Set<Credential> credentials = getCredentials();
        if (credentials != null && credentials.size() > 0) {
            for (Credential credential : credentials) {
                if(credential instanceof HttpAuthenticationCredential) {
                    return true; 
                }
            }
        }
        return false; 
    }

    /**
     * Set the retained content-digest value (usu. SHA1). 
     * 
     * @param digestValue
     * @deprecated Use {@link #setContentDigest(String, byte[])}
     */
    @Deprecated
    public void setContentDigest(byte[] digestValue) {
        setContentDigest("SHA1", digestValue);
    }
    
    public void setContentDigest(final String scheme,
            final byte [] digestValue) {
        this.contentDigest = digestValue;
        this.contentDigestScheme = scheme;
    }
    
    public String getContentDigestSchemeString() {
        if (this.contentDigest == null) {
            return null;
        }
        return this.contentDigestScheme + ":" + getContentDigestString();
    }

    /**
     * Return the retained content-digest value, if any.
     * 
     * @return Digest value.
     */
    public byte[] getContentDigest() {
        return contentDigest;
    }
    
    public String getContentDigestString() {
        if (this.contentDigest == null) {
            return null;
        }
        return Base32.encode(this.contentDigest);
    }

    transient protected Object holder;
    transient protected Object holderKey;

    /**
     * Remember a 'holder' to which some enclosing/queueing
     * facility has assigned this CrawlURI
     * .
     * @param obj
     */
    public void setHolder(Object obj) {
        holder=obj;
    }

    /**
     * Return the 'holder' for the convenience of 
     * an external facility.
     *
     * @return holder
     */
    public Object getHolder() {
        return holder;
    }

    /**
     * Remember a 'holderKey' which some enclosing/queueing
     * facility has assigned this CrawlURI
     * .
     * @param obj
     */
    public void setHolderKey(Object obj) {
        holderKey=obj;
    }
    /**
     * Return the 'holderKey' for convenience of 
     * an external facility (Frontier).
     * 
     * @return holderKey 
     */
    public Object getHolderKey() {
        return holderKey;
    }

    /**
     * Get the ordinal (serial number) assigned at creation.
     * 
     * @return ordinal
     */
    public long getOrdinal() {
        return ordinal;
    }
    
    
    public void setOrdinal(long o) {
        this.ordinal = o;
    }

    /** spot for an integer cost to be placed by external facility (frontier).
     *  cost is truncated to 8 bits at times, so should not exceed 255 */
    protected int holderCost = UNCALCULATED;
    /**
     * Return the 'holderCost' for convenience of external facility (frontier)
     * @return value of holderCost
     */
    public int getHolderCost() {
        return holderCost;
    }

    /**
     * Remember a 'holderCost' which some enclosing/queueing
     * facility has assigned this CrawlURI
     * @param cost value to remember
     */
    public void setHolderCost(int cost) {
        holderCost = cost;
    }

    /** 
     * All discovered outbound urls as CrawlURIs (navlinks, embeds, etc.) 
     */
    protected transient Collection<CrawlURI> outLinks;
    
    /**
     * Returns discovered links.  The returned collection might be empty if
     * no links were discovered, or if something like LinksScoper promoted
     * the links to CrawlURIs.
     * 
     * @return Collection of all discovered outbound links
     */
    public Collection<CrawlURI> getOutLinks() {
    	if (outLinks==null) {
    		outLinks = new LinkedHashSet<CrawlURI>();
    	}
        return outLinks;
    }
    
   
    /**
     * Set the (HTML) Base URI used for derelativizing internal URIs. 
     * 
     * @param baseHref String base href to use
     * @throws URIException if supplied string cannot be interpreted as URI
     */
    public void setBaseURI(String baseHref) throws URIException {
        getData().put(A_HTML_BASE, UURIFactory.getInstance(baseHref));
    }
      
    /**
     * Get the (HTML) Base URI used for derelativizing internal URIs. 
     *
     * @return UURI base URI previously set 
     */  
    public UURI getBaseURI() {
        if (!containsDataKey(A_HTML_BASE)) {
            return getUURI();
        }
        return (UURI)getData().get(A_HTML_BASE);
    }

    private void writeObject(ObjectOutputStream stream) throws IOException {
        stream.defaultWriteObject();
        stream.writeObject((data==null || data.isEmpty()) ? null : data);
      }
    
    private void readObject(ObjectInputStream stream) throws IOException,
    ClassNotFoundException {
        stream.defaultReadObject();
        @SuppressWarnings("unchecked")
        Map<String,Object> temp = (Map<String,Object>)stream.readObject();
        this.data = temp;
    }

    /**
     * Read a UURI from a String, handling a null or URIException
     * 
     * @param u String or null from which to create UURI
     * @return the best UURI instance creatable
     */
    protected UURI readUuri(String u) {
        if (u == null) {
            return null;
        }
        try {
            return UURIFactory.getInstance(u);
        } catch (URIException ux) {
            // simply continue to next try
        }
        try {
            // try adding an junk scheme
            return UURIFactory.getInstance("invalid:" + u);
        } catch (URIException ux) {
            ux.printStackTrace();
            // ignored; method continues
        }
        try {
            // return total junk
            return UURIFactory.getInstance("invalid:");
        } catch (URIException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Returns the IP address the request was fetched against or null if unavailable.
     */
    public String getServerIP() {
        if (data == null) return null;
        return (String) data.get(A_SERVER_IP);
    }

    public long getFetchBeginTime() {
        if (containsDataKey(CoreAttributeConstants.A_FETCH_BEGAN_TIME)) {
            return (Long)getData().get(CoreAttributeConstants.A_FETCH_BEGAN_TIME);
        } else {
            return 1L;
        }
    }

    public long getFetchCompletedTime() {
        if (containsDataKey(A_FETCH_COMPLETED_TIME)) {
            return (Long)getData().get(A_FETCH_COMPLETED_TIME);
        } else {
            return 0L;
        }
    }

    public long getFetchDuration() {
        if (!containsDataKey(A_FETCH_COMPLETED_TIME)) {
            return -1;
        }

        long completedTime = getFetchCompletedTime();
        long beganTime = getFetchBeginTime();
        return completedTime - beganTime;
    }
    
    public FetchType getFetchType() {
        return fetchType;
    }
    
    public Collection<Throwable> getNonFatalFailures() {
        @SuppressWarnings("unchecked")
        List<Throwable> list = (List<Throwable>)getData().get(A_NONFATAL_ERRORS);
        if (list == null) {
            list = new ArrayList<Throwable>();
            getData().put(A_NONFATAL_ERRORS, list);
        }
        
        // FIXME: Previous code automatically added annotation when "localized error"
        // was added, override collection to implement that?
        return list;
    }

    public void setServerIP(String serverIP) {
        getData().put(A_SERVER_IP, serverIP);
    }

    public void setError(String msg) {
        // TODO: Figure out where this is read, if ever.
        getData().put("error", msg);
    }

    public void setFetchBeginTime(long time) {
        getData().put(CoreAttributeConstants.A_FETCH_BEGAN_TIME, time);
    }

    public void setFetchCompletedTime(long time) {
        getData().put(A_FETCH_COMPLETED_TIME, time);
    }

    public void setFetchType(FetchType type) {
        fetchType = type;
    }

    public void setForceRetire(boolean b) {
        getData().put(A_FORCE_RETIRE, b);
    }

    public void setBaseURI(UURI base) {
        getData().put(A_HTML_BASE, base);
    }
    
    public Map<String,Object> getData() {
        if (data == null) {
            data = new HashMap<String,Object>();
        }
        return data;
    }
    
    /**
     * Convenience method: return (creating if necessary) list at 
     * given data key
     * @param key
     * @return List
     */
    @SuppressWarnings("unchecked")
    public List<Object> getDataList(String key) {
        if (!containsDataKey(key)) {
            getData().put(key, new ArrayList<Object>());
        }
        return (List<Object>) getData().get(key);
    }

    /**
     * Set the <tt>isSeed</tt> attribute of this URI.
     * @param b Is this URI a seed, true or false.
     */
    public void setSeed(boolean b) {
        this.isSeed = b;
        if (this.isSeed) {
            if(pathFromSeed==null) {
                this.pathFromSeed = "";
            }
//          seeds created on redirect must have a via to be recognized; don't clear
//            setVia(null);
        }
    }

    /**
     * @return Whether seeded.
     */
    public boolean isSeed() {
        return this.isSeed;
    }
  
    /**
     * @return UURI
     */
    public UURI getUURI() {
        return this.uuri;
    }

    /**
     * @return String of URI
     */
    public String getURI() {
        return getUURI().toCustomString();
    }
    /**
     * @return path (hop-types) from seed
     */
    public String getPathFromSeed() {
        return this.pathFromSeed;
    }
    
    /** convenience access to last hop character, as string */
    public String getLastHop() {
        return StringUtils.isEmpty(pathFromSeed) ? "" : pathFromSeed.substring(pathFromSeed.length()-1);
    }

    /**
     * @return URI via which this one was discovered
     */
    public UURI getVia() {
        return this.via;
    }
    
    
    public void setVia(UURI via) {
        this.via = via;
    }


    /**
     * @return CharSequence context in which this one was discovered
     */
    public LinkContext getViaContext() {
        return this.viaContext;
    }

    
    /**
     * @return True if this CrawlURI was result of a redirect:
     * i.e. Its parent URI redirected to here, this URI was what was in 
     * the 'Location:' or 'Content-Location:' HTTP Header.
     */
    public boolean isLocation() {
        return this.pathFromSeed != null && this.pathFromSeed.length() > 0 &&
            this.pathFromSeed.charAt(this.pathFromSeed.length() - 1) ==
                Hop.REFER.getHopChar();
    }

    
    //
    // Reporter implementation
    //

    public String shortReportLine() {
        return ReportUtils.shortReportLine(this);
    }
    
    @Override
    public Map<String, Object> shortReportMap() {
        Map<String,Object> map = new LinkedHashMap<String, Object>();
        map.put("class", getClass().getName());
        map.put("uri", getUURI().toString());
        map.put("pathFromSeed", pathFromSeed);
        map.put("flattenVia", flattenVia());
        return map;
    }

    @Override
    public void shortReportLineTo(PrintWriter w) {
        String className = this.getClass().getName();
        className = className.substring(className.lastIndexOf(".")+1);
        w.print(className);
        w.print(" ");
        w.print(getUURI().toString());
        w.print(" ");
        w.print(pathFromSeed);
        w.print(" ");
        w.print(flattenVia());
    }

    /* (non-Javadoc)
     * @see org.archive.util.Reporter#singleLineLegend()
     */
    @Override
    public String shortReportLegend() {
        return "className uri hopsPath viaUri";
    }

    /* (non-Javadoc)
     * @see org.archive.util.Reporter#reportTo(java.io.Writer)
     */
    @Override
    public void reportTo(PrintWriter writer) throws IOException {
        shortReportLineTo(writer);
        writer.print("\n");
    }

    
    /**
     * Method returns string version of this URI's referral URI.
     * @return String version of referral URI
     */
    public String flattenVia() {
        return (via == null)? "": via.toString();
    }

    
    public String getSourceTag() {
        return (String)getData().get(A_SOURCE_TAG);
    }
    
    
    public void setSourceTag(String sourceTag) {
        getData().put(A_SOURCE_TAG, sourceTag);
        makeHeritable(A_SOURCE_TAG);
    }

    
    /** Make the given key 'heritable', meaning its value will be 
     * added to descendant CrawlURIs. Only keys with immutable
     * values should be made heritable -- the value instance may 
     * be shared until the data map is serialized/deserialized. 
     * 
     * @param key to make heritable
     */
    public void makeHeritable(String key) {
        @SuppressWarnings("unchecked")
        HashSet<String> heritableKeys = (HashSet<String>)data.get(A_HERITABLE_KEYS);
        if (heritableKeys == null) {
            heritableKeys = new HashSet<String>();
            heritableKeys.add(A_HERITABLE_KEYS);
            data.put(A_HERITABLE_KEYS, heritableKeys);
        }
        heritableKeys.add(key);
    }
    
    /** Make the given key non-'heritable', meaning its value will 
     * not be added to descendant CrawlURIs. Only meaningful if
     * key was previously made heritable.  
     * 
     * @param key to make non-heritable
     */
    public void makeNonHeritable(String key) {
        @SuppressWarnings("unchecked")
        HashSet<String> heritableKeys = (HashSet<String>)data.get(A_HERITABLE_KEYS);
        if(heritableKeys == null) {
            return;
        }
        heritableKeys.remove(key);
        if(heritableKeys.size()==1) {
            // only remaining heritable key is itself; disable completely
            data.remove(A_HERITABLE_KEYS);
        }
    }

    
    /**
     * Get the token (usually the hostname + port) which indicates
     * what "class" this CrawlURI should be grouped with,
     * for the purposes of ensuring only one item of the
     * class is processed at once, all items of the class
     * are held for a politeness period, etc.
     *
     * @return Token (usually the hostname) which indicates
     * what "class" this CrawlURI should be grouped with.
     */
    public String getClassKey() {
        return classKey;
    }

    public void setClassKey(String key) {
        classKey = key;
    }

    
    /**
     * If this method returns true, this URI should be fetched even though
     * it already has been crawled. This also implies
     * that this URI will be scheduled for crawl before any other waiting
     * URIs for the same host.
     *
     * This value is used to refetch any expired robots.txt or dns-lookups.
     *
     * @return true if crawling of this URI should be forced
     */
    public boolean forceFetch() {
        return forceRevisit;
    }

   /**
     * Method to signal that this URI should be fetched even though
     * it already has been crawled. Setting this to true also implies
     * that this URI will be scheduled for crawl before any other waiting
     * URIs for the same host.
     *
     * This value is used to refetch any expired robots.txt or dns-lookups.
     *
     * @param b set to true to enforce the crawling of this URI
     */
    public void setForceFetch(boolean b) {
        forceRevisit = b;
    }

    
    /**
     * Tally up the number of transitive (non-simple-link) hops at
     * the end of this CrawlURI's pathFromSeed.
     * 
     * {@link Hop#NAVLINK} and {@link Hop#MANIFEST} are regarded as 
     * "simple links". All other hop types are regarded as transitive. 
     * 
     * In some cases, URIs with greater than zero but less than some
     * threshold such hops are treated specially. 
     * 
     * <p>TODO: consider moving link-count in here as well, caching
     * calculation, and refactoring CrawlScope.exceedsMaxHops() to use this. 
     * 
     * @return Transhop count.
     */
    public int getTransHops() {
        String path = getPathFromSeed();
        int transCount = 0;
        for(int i=path.length()-1;i>=0;i--) {
            if(path.charAt(i)==Hop.NAVLINK.getHopChar()
            		|| path.charAt(i)==Hop.MANIFEST.getHopChar()) {
                break;
            }
            transCount++;
        }
        return transCount;
    }

    
    /**
     * Inherit (copy) the relevant keys-values from the ancestor. 
     * 
     * @param ancestor
     */
    protected void inheritFrom(CrawlURI ancestor) {
        Map<String,Object> adata = ancestor.getData();
        @SuppressWarnings("unchecked")
        HashSet<String> heritableKeys = (HashSet<String>)adata.get(A_HERITABLE_KEYS);
        Map<String,Object> thisData = getData();
        if (heritableKeys != null) {
            for (String key: heritableKeys) {
                thisData.put(key, adata.get(key));
            }
        }
    }
    
    /**
     * Utility method for creating CrawlURIs that were found as out links from the current CrawlURI
     * links from this CrawlURI. 
     * <p>
     * Any relative URIs will be treated as relative to this CrawlURI's UURI.
	 * @param destination The new URI, possibly a relative URI
	 * @param context
	 * @param hop 
     * @return New CrawlURI with the current CrawlURI set as the one it inherits from
     * @throws URIException
     */
    public CrawlURI createCrawlURI(UURI destination, LinkContext context, Hop hop)
    			throws URIException {
        return createCrawlURI(destination.toString(), context, hop);
    }

    public CrawlURI createCrawlURI(String destination, LinkContext context, Hop hop) 
    		throws URIException {
        UURI u = UURIFactory.getInstance(this.getBaseURI(), destination);
        CrawlURI newCaURI = new CrawlURI(
        		u, 
                extendHopsPath(getPathFromSeed(), 
                		hop.getHopChar()),
                this.getUURI(), 
                context);
        newCaURI.inheritFrom(this);
        return newCaURI;
    }

    /**
     * Extend a 'hopsPath' (pathFromSeed string of single-character hop-type symbols),
     * keeping the number of displayed hop-types under MAX_HOPS_DISPLAYED. For longer
     * hops paths, precede the string with a integer and '+', then the displayed 
     * hops. 
     * 
     * @param pathFromSeed
     * @param hopChar
     */
    public static String extendHopsPath(String pathFromSeed, char hopChar) {
        if(pathFromSeed.length()<MAX_HOPS_DISPLAYED) {
            return pathFromSeed + hopChar;
        }
        int plusIndex = pathFromSeed.indexOf('+');
        int prevOverflow = (plusIndex<0) ? 0 : Integer.parseInt(pathFromSeed.substring(0,plusIndex));
        return (prevOverflow+1)+"+"+pathFromSeed.substring(plusIndex+2)+hopChar; 
    }

    /**
     * Utility method for creation of CrawlURIs found extracting
     * links from this CrawlURI.
     */
    public CrawlURI createCrawlURI(UURI destination, LinkContext context, Hop hop,
        int scheduling, boolean seed)
    throws URIException {
        final CrawlURI caURI = createCrawlURI(destination, context, hop);
        caURI.setSchedulingDirective(scheduling);
        caURI.setSeed(seed);
        return caURI;
    }

    
    /**
     * @return The UURI this CandidateURI wraps as a string 
     */
    public String toString() {
        return getUURI().toCustomString();
    }

    
    public void incrementDiscardedOutLinks() {
        discardedOutlinks++;
    }

    /**
     * @return the precedence
     */
    public int getPrecedence() {
        return precedence;
    }

    /**
     * @param precedence the precedence to set
     */
    public void setPrecedence(int precedence) {
        this.precedence = precedence;
    }
    
    /**
     * Get the UURI that should be used as the basis of policy/overlay
     * decisions. In the case of prerequisites, this is the URI that 
     * triggered the prerequisite -- the 'via' -- so that the prerequisite
     * lands in the same queue, with the same overlay values, as the 
     * triggering URI. 
     * @return UURI to use for policy decisions
     */
    public UURI getPolicyBasisUURI() {
        UURI effectiveuuri = null;
        // always use 'via' of prerequisite URIs, if available, so
        // prerequisites go to same queue as trigger URI
        if (getPathFromSeed().endsWith(Hop.PREREQ.getHopString())) {
            effectiveuuri = getVia();
        }
        if(effectiveuuri==null) {
            effectiveuuri = getUURI();
        }
        return effectiveuuri;
    }
    
    
    //
    // OverridesSource implementation
    //
    transient protected ArrayList<String> overlayNames = null;
    transient protected OverlayMapsSource overlayMapsSource; 
    public boolean haveOverlayNamesBeenSet() {
        return overlayNames != null;
    }
    
    public ArrayList<String> getOverlayNames() {
        if(overlayNames == null) {
            overlayNames = new ArrayList<String>(); 
        }
        return overlayNames;
    }

    public Map<String, Object> getOverlayMap(String name) {
        return overlayMapsSource.getOverlayMap(name);
    }

    public void setOverlayMapsSource(OverlayMapsSource overrideMapsSource) {
        this.overlayMapsSource = overrideMapsSource;
    }

    protected String canonicalString; 
    public void setCanonicalString(String canonical) {
        this.canonicalString = canonical; 
    }
    public String getCanonicalString() {
        if(StringUtils.isEmpty(canonicalString)){
            logger.warning("canonicalString unset, returning uncanonicalized " + getURI());
            return getURI();
        }
        return canonicalString;
    }

    protected long politenessDelay = -1; 
    public void setPolitenessDelay(long polite) {
        this.politenessDelay = polite; 
    }
    public long getPolitenessDelay() {
        if(politenessDelay<0) {
            logger.warning("politessDelay unset, returning default 5000 for " + this);
            return 5000;
        }
        return this.politenessDelay;
    }

    transient protected CrawlURI fullVia; 
    public void setFullVia(CrawlURI curi) {
        this.fullVia = curi; 
    }
    public CrawlURI getFullVia() {
        return fullVia;
    }

    /**
     * A future time at which this CrawlURI should be reenqueued.
     */
    protected long rescheduleTime = -1;
    public void setRescheduleTime(long time) {
        this.rescheduleTime = time;
    }
    public long getRescheduleTime() {
        return this.rescheduleTime;
    }

    /**
     * Reset state that that should not persist when a URI is
     * rescheduled for a specific future time.
     */
    public void resetForRescheduling() {
        // retries from now don't count against future try
        resetFetchAttempts();
        // deferrals from now don't count against future try
        resetDeferrals();
    }

    public boolean includesRetireDirective() {
        return containsDataKey(A_FORCE_RETIRE) 
         && (Boolean)getData().get(A_FORCE_RETIRE);
    }

    protected JSONObject extraInfo;

    public JSONObject getExtraInfo() {
        if (extraInfo == null) {
            extraInfo = new JSONObject();
        }
        return extraInfo;
    }

    public void addExtraInfo(String key, Object value) {
        try {
            getExtraInfo().put(key, value);
        } catch (JSONException e) {
            logger.log(Level.WARNING, "failed to add extra info", e);
        }
    }
    
    // Kryo support
    @SuppressWarnings("unused")
    private CrawlURI() {}
    public static void autoregisterTo(AutoKryo kryo) {
//        kryo.register(CrawlURI.class,new DeflateCompressor(kryo.newSerializer(CrawlURI.class)));
        kryo.register(CrawlURI.class);
        kryo.autoregister(byte[].class); 
        kryo.autoregister(java.util.HashSet.class); 
        kryo.autoregister(java.util.HashMap.class); 
        kryo.autoregister(org.archive.net.UURI.class); 
        kryo.autoregister(org.archive.modules.extractor.HTMLLinkContext.class); 
        kryo.autoregister(org.archive.modules.extractor.LinkContext.SimpleLinkContext.class);
        kryo.autoregister(java.util.HashMap[].class); 
        kryo.autoregister(org.archive.modules.credential.HttpAuthenticationCredential.class);
        kryo.autoregister(org.archive.modules.credential.HtmlFormCredential.class);
        kryo.autoregister(org.apache.http.NameValuePair.class);
        kryo.autoregister(org.apache.http.NameValuePair[].class);
        kryo.autoregister(FetchType.class);
    }
    
    /**
     * Do all actions associated with setting a <code>CrawlURI</code> as
     * requiring a prerequisite.
     * @return the newly created prerequisite CrawlURI
     * @throws URIException
     */
    public CrawlURI markPrerequisite(String preq) 
    throws URIException {
        CrawlURI caUri = createCrawlURI(preq, LinkContext.PREREQ_MISC, Hop.PREREQ);
        caUri.setPrerequisite(true);
        // TODO: consider moving some of this to configurable candidate-handling
        int prereqPriority = getSchedulingDirective() - 1;
        if (prereqPriority < 0) {
            prereqPriority = 0;
            logger.severe("Unable to promote prerequisite " + caUri + " above " + this);
        }
        caUri.setSchedulingDirective(prereqPriority);
        caUri.setForceFetch(true);
        setPrerequisiteUri(caUri);
        incrementDeferrals();
        setFetchStatus(S_DEFERRED);
        
        return caUri;
    }
    
    public boolean containsContentTypeCharsetDeclaration() {
        // TODO can this regex be improved? should the test consider if its legal? 
        return getContentType().matches("(?i).*charset=.*");
    }

    /**
     * @param key http response header key (case-insensitive)
     * @return value of the header or null if there is no such header
     * @since 3.3.0
     */
    public String getHttpResponseHeader(String key) {
        @SuppressWarnings("unchecked")
        Map<String, String> httpResponseHeaders = (Map<String, String>) getData().get(A_HTTP_RESPONSE_HEADERS);
        if (httpResponseHeaders == null) {
            return null;
        }
        return httpResponseHeaders.get(key.toLowerCase());
    }

    /**
     * @since 3.3.0
     */
    public void putHttpResponseHeader(String key, String value) {
        @SuppressWarnings("unchecked")
        Map<String, String> httpResponseHeaders = (Map<String, String>) getData().get(A_HTTP_RESPONSE_HEADERS);
        if (httpResponseHeaders == null) {
            httpResponseHeaders = new HashMap<String, String>();
            getData().put(A_HTTP_RESPONSE_HEADERS, httpResponseHeaders);
        }
        httpResponseHeaders.put(key.toLowerCase(), value);
    }

    @SuppressWarnings("unchecked")
    public Map<String,String> getHttpAuthChallenges() {
        return (Map<String, String>) getData().get(A_HTTP_AUTH_CHALLENGES);
    }

    public void setHttpAuthChallenges(Map<String, String> httpAuthChallenges) {
        getData().put(A_HTTP_AUTH_CHALLENGES, httpAuthChallenges);
    }
    
    @SuppressWarnings("unchecked")
    public HashMap<String, Object>[] getFetchHistory() {
        return (HashMap<String,Object>[]) getData().get(A_FETCH_HISTORY);
    }

    public void setFetchHistory(Map<String, Object>[] history) {
        getData().put(A_FETCH_HISTORY, history);
    }
        
    public HashMap<String, Object> getContentDigestHistory() {
        @SuppressWarnings("unchecked")
        HashMap<String, Object> contentDigestHistory = (HashMap<String, Object>) getData().get(A_CONTENT_DIGEST_HISTORY);
        
        if (contentDigestHistory == null) {
            contentDigestHistory = new HashMap<String, Object>();
            getData().put(A_CONTENT_DIGEST_HISTORY, contentDigestHistory);
        }
        
        return contentDigestHistory;
    }

    public boolean hasContentDigestHistory() {
        return getData().get(A_CONTENT_DIGEST_HISTORY) != null;
    }
    
    /**
     * Indicates if this CrawlURI object has been deemed a revisit.
     */
    public boolean isRevisit() {
    	return revisitProfile!=null;
    }

	public RevisitProfile getRevisitProfile() {
		return revisitProfile;
	}

	public void setRevisitProfile(RevisitProfile revisitProfile) {
		this.revisitProfile = revisitProfile;
	}
    

    // brought over from old Link class
    @Override
    public int compareTo(CrawlURI o) {
        int cmp = compare(via.toString(), o.via.toString());
        if (cmp == 0) {
            cmp = compare(uuri.toString(), o.uuri.toString());
        }
        if (cmp == 0) {
            cmp = compare(viaContext.toString(), o.viaContext.toString());
        }
        if (cmp == 0) {
            cmp = compare(pathFromSeed, o.pathFromSeed);
        }
        return cmp;
    }

    // brought over from old Link class
    @Override
    public int hashCode() {
        int r = 37;
        String viaString = (via == null) ? "" : via.toString();
        String viaContextString = (viaContext == null) ? "" : viaContext.toString();
        String pathFromSeedString = (pathFromSeed == null) ? "" : pathFromSeed.toString();
        return r ^ hash(viaString) ^ hash(uuri.toString())
                ^ hash(viaContextString) ^ hash(pathFromSeedString);
    }

    // handles nulls
    private static int hash(String a) {
        return a == null ? 0 : a.hashCode();
    }

    // handles nulls
    private static boolean equals(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    // handles nulls
    private static int compare(String a, String b) {
        if (a == null && b == null) {
            return 0;
        } else if (a == null && b != null) {
            return -1;
        } else if (a != null && b == null) {
            return 1;
        } else {
            return a.compareTo(b);
        }
    }

    // brought over from old Link class
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CrawlURI)) {
            return false;
        }
        CrawlURI u = (CrawlURI) o;
        return equals(via, u.via) && equals(uuri, u.uuri)
                && equals(viaContext, u.viaContext)
                && equals(pathFromSeed, u.pathFromSeed);
    }
}
