package org.archive.modules.fetcher;

import static org.archive.modules.fetcher.FetchStatusCodes.S_CONNECT_FAILED;
import static org.archive.modules.fetcher.FetchStatusCodes.S_CONNECT_LOST;
import static org.archive.modules.fetcher.FetchStatusCodes.S_DOMAIN_PREREQUISITE_FAILURE;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;
import org.archive.modules.CrawlURI;
import org.archive.modules.CrawlURI.FetchType;
import org.archive.modules.Processor;
import org.archive.modules.net.CrawlHost;
import org.archive.modules.net.ServerCache;
import org.archive.util.Recorder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.Lifecycle;

public class FetchHTTP2 extends Processor implements Lifecycle {

    protected ServerCache serverCache;
    public ServerCache getServerCache() {
        return this.serverCache;
    }
    /**
     * Used to do DNS lookups.
     */
    @Autowired
    public void setServerCache(ServerCache serverCache) {
        this.serverCache = serverCache;
    }

    /**
     * Whether or not to perform an on-the-fly digest hash of retrieved
     * content-bodies.
     */
    {
        setDigestContent(true);
    }
    public boolean getDigestContent() {
        return (Boolean) kp.get("digestContent");
    }
    public void setDigestContent(boolean digest) {
        kp.put("digestContent",digest);
    }
 
    /**
     * Which algorithm (for example MD5 or SHA-1) to use to perform an
     * on-the-fly digest hash of retrieved content-bodies.
     */
    protected String digestAlgorithm = "sha1"; 
    public String getDigestAlgorithm() {
        return digestAlgorithm;
    }
    public void setDigestAlgorithm(String digestAlgorithm) {
        this.digestAlgorithm = digestAlgorithm;
    }


    /**
     * Can this processor fetch the given CrawlURI. May set a fetch status
     * if this processor would usually handle the CrawlURI, but cannot in
     * this instance.
     * 
     * @param curi
     * @return True if processor can fetch.
     */
    @Override
    protected boolean shouldProcess(CrawlURI curi) {
        String scheme = curi.getUURI().getScheme();
        if (!(scheme.equals("http") || scheme.equals("https"))) {
            // handles only plain http and https
            return false;
        }

        CrawlHost host = serverCache.getHostFor(curi.getUURI());
        if (host.getIP() == null && host.hasBeenLookedUp()) {
            curi.setFetchStatus(S_DOMAIN_PREREQUISITE_FAILURE);
            return false;
        }

        return true;
    }

    @Override
    protected void innerProcess(final CrawlURI curi) throws InterruptedException {
        // Note begin time
        curi.setFetchBeginTime(System.currentTimeMillis());

        // Get a reference to the HttpRecorder that is set into this ToeThread.
        Recorder rec = curi.getRecorder();

        // Shall we get a digest on the content downloaded?
        boolean digestContent = getDigestContent();
        String algorithm = null;
        if (digestContent) {
            algorithm = getDigestAlgorithm();
            rec.getRecordedInput().setDigest(algorithm);
        } else {
            // clear
            rec.getRecordedInput().setDigest((MessageDigest)null);
        }

        String curiString = curi.getUURI().toString();
        HttpRequestBase request = null;
        if (curi.getFetchType() == FetchType.HTTP_POST) {
            request = new HttpPost(curiString);
        } else {
            request = new HttpGet(curiString);
        }
        HttpClient httpClient = new DefaultHttpClient();
        HttpResponse response = null;
        try {
            response = httpClient.execute(request);
        } catch (ClientProtocolException e) {
            failedExecuteCleanup(request, curi, e);
        } catch (IOException e) {
            failedExecuteCleanup(request, curi, e);
        }

        HttpEntity entity = response.getEntity();
        if (entity != null) {
            InputStream instream = null;
            try {
                instream = entity.getContent();
            } catch (IllegalStateException e) {
                cleanup(curi, e, "readFully", S_CONNECT_LOST);
            } catch (IOException e) {
                cleanup(curi, e, "readFully", S_CONNECT_LOST);
            }

            try {
                // do something useful
            } finally {
                IOUtils.closeQuietly(instream);
            }
        }    
    }


    /**
     * Cleanup after a failed method execute.
     * 
     * @param curi
     *            CrawlURI we failed on.
     * @param request
     *            Method we failed on.
     * @param exception
     *            Exception we failed with.
     */
    protected void failedExecuteCleanup(final HttpRequestBase request,
            final CrawlURI curi, final Exception exception) {
        cleanup(curi, exception, "executeMethod", S_CONNECT_FAILED);
        request.reset();
    }
    
    /**
     * Cleanup after a failed method execute.
     * 
     * @param curi
     *            CrawlURI we failed on.
     * @param exception
     *            Exception we failed with.
     * @param message
     *            Message to log with failure. FIXME: Seems ignored
     * @param status
     *            Status to set on the fetch.
     */
    protected void cleanup(final CrawlURI curi, final Exception exception,
            final String message, final int status) {
        // message ignored!
        curi.getNonFatalFailures().add(exception);
        curi.setFetchStatus(status);
        curi.getRecorder().close();
    }
}
