package org.archive.modules.fetcher;

import static org.archive.modules.fetcher.FetchErrors.LENGTH_TRUNC;
import static org.archive.modules.fetcher.FetchErrors.TIMER_TRUNC;
import static org.archive.modules.fetcher.FetchStatusCodes.S_CONNECT_FAILED;
import static org.archive.modules.fetcher.FetchStatusCodes.S_CONNECT_LOST;
import static org.archive.modules.fetcher.FetchStatusCodes.S_DOMAIN_PREREQUISITE_FAILURE;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.security.MessageDigest;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ClientConnectionOperator;
import org.apache.http.conn.OperatedClientConnection;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.BasicClientConnectionManager;
import org.apache.http.impl.conn.DefaultClientConnection;
import org.apache.http.impl.conn.DefaultClientConnectionOperator;
import org.apache.http.impl.conn.SchemeRegistryFactory;
import org.apache.http.impl.io.AbstractSessionInputBuffer;
import org.apache.http.impl.io.IdentityInputStream;
import org.apache.http.io.SessionInputBuffer;
import org.apache.http.io.SessionOutputBuffer;
import org.apache.http.params.HttpParams;
import org.archive.io.RecorderLengthExceededException;
import org.archive.io.RecorderTimeoutException;
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
        final Recorder rec = curi.getRecorder();

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
        HttpClient httpClient = new DefaultHttpClient() {
            @Override
            protected ClientConnectionManager createClientConnectionManager() {
                return new BasicClientConnectionManager(SchemeRegistryFactory.createDefault()) {
                    @Override
                    protected ClientConnectionOperator createConnectionOperator(SchemeRegistry schreg) {
                        return new DefaultClientConnectionOperator(schreg) {
                            @Override
                            public OperatedClientConnection createConnection() {
                                return new DefaultClientConnection() {
                                    @Override
                                    protected SessionInputBuffer createSessionInputBuffer(Socket socket, int buffersize, HttpParams params) throws IOException {
                                        SessionInputBuffer sessionInputBuffer = super.createSessionInputBuffer(socket, buffersize, params);
                                        InputStream recordingInputStream = rec.inputWrap(new IdentityInputStream(sessionInputBuffer));
                                        return new HcInputWrapper(recordingInputStream, buffersize, params, sessionInputBuffer);
                                    }
                                    
                                    @Override
                                    protected SessionOutputBuffer createSessionOutputBuffer(
                                            Socket socket, int buffersize,
                                            HttpParams params)
                                                    throws IOException {
                                        return super.createSessionOutputBuffer(socket, buffersize, params);
                                    }
                                };
                            }
                        };
                    }
                };

            }
        };
        HttpResponse response = null;
        try {
            response = httpClient.execute(request);
            
            addResponseContent(response, curi);

            // httpClient.execute(request) reads in headers 
            rec.markContentBegin();
        } catch (ClientProtocolException e) {
            failedExecuteCleanup(request, curi, e);
        } catch (IOException e) {
            failedExecuteCleanup(request, curi, e);
        }
        
//        HttpEntity entity = response.getEntity();
//        if (entity != null) {
//            InputStream instream = null;
//            try {
//                instream = entity.getContent();
//            } catch (IllegalStateException e) {
//                cleanup(curi, e, "readFully", S_CONNECT_LOST);
//            } catch (IOException e) {
//                cleanup(curi, e, "readFully", S_CONNECT_LOST);
//            }
//
//            try {
//                // do something useful
//            } finally {
//                IOUtils.closeQuietly(instream);
//            }
//        }

        try {
            rec.getRecordedInput().readFully();
        } catch (RecorderTimeoutException ex) {
            doAbort(curi, request, TIMER_TRUNC);
        } catch (RecorderLengthExceededException ex) {
            doAbort(curi, request, LENGTH_TRUNC);
        } catch (IOException e) {
            cleanup(curi, e, "readFully", S_CONNECT_LOST);
            return;
        } catch (ArrayIndexOutOfBoundsException e) {
            // For weird windows-only ArrayIndex exceptions from native code
            // see http://forum.java.sun.com/thread.jsp?forum=11&thread=378356
            // treating as if it were an IOException
            cleanup(curi, e, "readFully", S_CONNECT_LOST);
            return;
        } finally {
            // ensure recording has stopped
            rec.closeRecorders();
            if (!request.isAborted()) {
                request.releaseConnection();
            }
            // Note completion time
            curi.setFetchCompletedTime(System.currentTimeMillis());
            // Set the response charset into the HttpRecord if available.
//            setCharacterEncoding(curi, rec, method);
//            setSizes(curi, rec);
//            setOtherCodings(curi, rec, method); 
        }

    }

    protected void doAbort(CrawlURI curi, HttpRequestBase request,
            String annotation) {
        curi.getAnnotations().add(annotation);
        curi.getRecorder().close();
        request.abort();
    }

    /**
     * This method populates <code>curi</code> with response status and
     * content type.
     * 
     * @param curi
     *            CrawlURI to populate.
     * @param response
     *            Method to get response status and headers from.
     */
    protected void addResponseContent(HttpResponse response, CrawlURI curi) {
        curi.setFetchStatus(response.getStatusLine().getStatusCode());
        // Header ct = response.getResponseHeader("content-type");
        Header ct = response.getLastHeader("content-type");
        curi.setContentType(ct == null ? null : ct.getValue());
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

    public class HcInputWrapper extends AbstractSessionInputBuffer {

        protected SessionInputBuffer checkMeForDataAvailable;

        @Override
        public boolean isDataAvailable(int timeout) throws IOException {
            return checkMeForDataAvailable.isDataAvailable(timeout);
        }

        public HcInputWrapper(InputStream in, int buffersize, HttpParams params, SessionInputBuffer checkMeForDataAvailable) {
            this.checkMeForDataAvailable = checkMeForDataAvailable;
            this.init(in, buffersize, params);
        }
    }

}
