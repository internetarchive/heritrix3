package org.archive.modules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.URIException;
import org.archive.modules.credential.CredentialAvatar;
import org.archive.modules.extractor.Link;
import org.archive.modules.extractor.LinkContext;
import org.archive.modules.net.CrawlHost;
import org.archive.modules.net.CrawlServer;
import org.archive.modules.net.RobotsHonoringPolicy;
import org.archive.net.UURI;
import org.archive.util.Recorder;


public class DefaultProcessorURI 
implements ProcessorURI {

    
    /**
     * An exception and an error message.  Used for keeping list of errors.
     */
    public static class ExceptionHolder {

        /** The exception. */
        final public Throwable exception;
        
        /** The error message. */
        final public String description;
        
        /**
         * Constructor.
         * 
         * @param e      the exception
         * @param desc   the description
         */
        public ExceptionHolder(Throwable e, String desc) {
            this.exception = e;
            this.description = desc;
        }
    }
    
    
    final private Collection<String> annotations = new ArrayList<String>();
    final private Set<CredentialAvatar> avatars = 
        new HashSet<CredentialAvatar>();
    final private List<Throwable> nonFatalFailures = 
        new ArrayList<Throwable>();
    final private List<Link> outLinks = new ArrayList<Link>();
    final private List<ExceptionHolder> uriErrors 
     = new ArrayList<ExceptionHolder>();

    private int discardedOutLinks;

    private String from;
    private String userAgent;
    
    private long length;
    private long size;
    private String contentType;

    private long startTime;
    private long endTime;

    private int fetchStatus;
    private FetchType fetchType = FetchType.UNKNOWN;
    private HttpMethod httpMethod;
    
    private String pathFromSeed;
    
    private UURI base;

    private Map<String,Object> data = new HashMap<String,Object>();
    
    private Recorder recorder;
    
    private String resolvedName;
    private RobotsHonoringPolicy robotsHonoringPolicy;
    
    private UURI uuri;
    private UURI via;
    private LinkContext viaContext;
    
    private boolean linkExtractionFinished;
    private boolean location;
    private boolean prereq;
    private boolean seed;

    private boolean forceFetch;
    
    
    public DefaultProcessorURI(UURI uuri, UURI via, LinkContext context) {
        this.uuri = uuri;
        this.via = via;
        this.viaContext = context;
    }
    
    public DefaultProcessorURI(UURI uuri, LinkContext context) {
        this(uuri, null, context);
    }    
    
    public void addUriError(URIException e, String uri) {
        uriErrors.add(new ExceptionHolder(e, uri));
    }
    
    public List<ExceptionHolder> getUriErrors() {
        return uriErrors;
    }

    public boolean attachRfc2617Credential(String realm) {
        // TODO Auto-generated method stub
        return false;
    }

    public boolean detachRfc2617Credential(String realm) {
        // TODO Auto-generated method stub
        return false;
    }

    public Collection<String> getAnnotations() {
        return annotations;
    }

    public UURI getBaseURI() {
        return base != null ? base : getUURI(); 
    }

    public long getContentLength() {
        return length;
    }
    
    
    public void setContentLength(long len) {
        this.length = len;
    }

    public long getContentSize() {
        return size;
    }

    public String getContentType() {
        return contentType;
    }

    public CrawlHost getCrawlHost() {
        // TODO Auto-generated method stub
        return null;
    }
    
    
    public CrawlServer getCrawlServer(String serverKey) {
        // TODO
        return null;
    }

    
    public boolean hasCredentialAvatars() {
        return getCredentialAvatars().size() > 0;
    }
    
    
    public Set<CredentialAvatar> getCredentialAvatars() {
        return avatars;
    }

    public String getDNSServerIPLabel() {
        // TODO Auto-generated method stub
        return null;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public long getFetchBeginTime() {
        return startTime;
    }

    public long getFetchCompletedTime() {
        return endTime;
    }

    public int getFetchStatus() {
        return fetchStatus;
    }

    public FetchType getFetchType() {
        return fetchType;
    }

    public String getFrom() {
        return from;
    }

    public Collection<Throwable> getNonFatalFailures() {
        return nonFatalFailures;
    }

    public List<Link> getOutLinks() {
        return outLinks;
    }

    public String getPathFromSeed() {
        return pathFromSeed;
    }

    public Recorder getRecorder() {
        return recorder;
    }
    
    public void setRecorder(Recorder r) {
        this.recorder = r;
    }

    public String getResolvedName() {
        return resolvedName; // FIXME: CrawlHost
    }

    public RobotsHonoringPolicy getRobotsHonoringPolicy() {
        return robotsHonoringPolicy;
    }

    public UURI getUURI() {
        return uuri;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String ua) {
        this.userAgent = ua;
    }

    public UURI getVia() {
        return via;
    }

    public LinkContext getViaContext() {
        return viaContext;
    }

    public boolean hasBeenLinkExtracted() {
        return linkExtractionFinished;
    }

    public boolean isLocation() {
        return location;
    }

    public boolean isPrerequisite() {
        return prereq;
    }

    public boolean isSeed() {
        return seed;
    }

    public boolean passedDNS() {
        // TODO Auto-generated method stub
        return false;
    }

    public boolean populateCredentials(HttpMethod method) {
        // TODO Auto-generated method stub
        return false;
    }

    public void promoteCredentials() {
        // TODO Auto-generated method stub

    }

    public void requestCrawlPause() {
        // TODO Auto-generated method stub

    }

    public void setBaseURI(UURI base) {
        this.base = base;
    }

    public void setContentDigest(String algorithm, byte[] digest) {
        // TODO Auto-generated method stub

    }

    public void setContentSize(long size) {
        this.size = size;
    }

    public void setContentType(String mimeType) {
        this.contentType = mimeType;
    }

    public void setDNSServerIPLabel(String label) {
        // TODO Auto-generated method stub

    }

    public void setError(String msg) {
        // TODO Auto-generated method stub
    }

    public void setFetchBeginTime(long time) {
        startTime = time;
    }

    public void setFetchCompletedTime(long time) {
        endTime = time;
    }

    public void setFetchStatus(int status) {
        this.fetchStatus = status;
    }

    public void setFetchType(FetchType type) {
        if (type == null) {
            throw new IllegalArgumentException("fetchType is non-null");
        }
        this.fetchType = type;
    }

    public void setHttpMethod(HttpMethod method) {
        this.httpMethod = method;
    }

    public void linkExtractorFinished() {
        linkExtractionFinished = true;
    }

    public void setPrerequisite(boolean prereq) {
        this.prereq = prereq;
    }

    public void setSeed(boolean seed) {
        this.seed = seed;
    }

    public void skipToPostProcessing() {
        // TODO Auto-generated method stub

    }


    public HttpMethod getHttpMethod() {
        return this.httpMethod;
    }


    public int getFetchAttempts() {
        return 1; // FIXME
    }

    
    public boolean containsDataKey(String k) {
        return data.containsKey(k);
    }


    public byte[] getContentDigest() {
        return null; // FIXME
    }


    public String getContentDigestSchemeString() {
        // FIXME
        return null;
    }

    public void makeHeritable(String attr) {
        // FIXME? -- maybe irrelevant

    }

    public boolean isSuccess() {
        // FIXME?
        return false;
    }

    public Map<String, Object> getPersistentDataMap() {
        // FIXME?
        return null;
    }
    
    public void addPersistentDataMapKey(String s) {
        
    }

    
    public void incrementDiscardedOutLinks() {
        discardedOutLinks++;
    }

    
    public boolean forceFetch() {
        return forceFetch;
    }
    
    public void setForceFetch(boolean b) {
        this.forceFetch = b;
    }
    
    public String getSourceTag() {
        return "";
    }
}
