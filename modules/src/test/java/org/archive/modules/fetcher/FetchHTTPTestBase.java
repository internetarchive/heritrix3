package org.archive.modules.fetcher;

import java.io.IOException;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.archive.modules.CrawlURI;
import org.archive.modules.CrawlURI.FetchType;
import org.archive.modules.Processor;
import org.archive.modules.ProcessorTestBase;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.util.Recorder;
import org.archive.util.TmpDirTestCase;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.jetty.handler.DefaultHandler;
import org.mortbay.jetty.handler.HandlerList;
import org.mortbay.jetty.handler.ResourceHandler;
import org.mortbay.log.Log;

public abstract class FetchHTTPTestBase extends ProcessorTestBase {
    
    private static Logger logger = Logger.getLogger(FetchHTTPTestBase.class.getName());

    protected static final String HTDOCS_PATH = FetchHTTPTestBase.class.getResource("testdata").getFile();
    protected Server httpServer;
    protected Processor fetcher;

    abstract protected Processor makeModule() throws IOException;

    public Server startHttpFileServer(String path) throws Exception {
        System.setProperty("org.mortbay.LEVEL", "DEBUG");
        Log.getLogger(Server.class.getCanonicalName()).setDebugEnabled(true);
        Server server = new Server();
        SocketConnector sc = new SocketConnector();
        sc.setHost("127.0.0.1");
        sc.setPort(7777);
        server.addConnector(sc);
        ResourceHandler rhandler = new ResourceHandler();
        rhandler.setResourceBase(path);
        logger.info("serving files from " + path);
        
        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[] {rhandler, new DefaultHandler()});
        server.setHandler(handlers);
        
        server.start();
        
        return server;
    }
    
    protected void ensureHttpServer() throws Exception {
        if (this.httpServer == null) { 
            this.httpServer = startHttpFileServer(HTDOCS_PATH);
        }
    }

    protected Processor getFetcher() throws IOException {
        if (fetcher == null) { 
            fetcher = makeModule();
        }
        
        return fetcher;
    }
    
    public void testSomething() throws Exception {
        ensureHttpServer();
        fetcher = getFetcher();
        
        UURI uuri = UURIFactory.getInstance("http://localhost:7777/test.txt");
        CrawlURI curi = new CrawlURI(uuri);
        curi.setRecorder(getRecorder());
        getFetcher().process(curi);

        byte[] buf = IOUtils.toByteArray(getRecorder().getRecordedOutput().getReplayInputStream());
        // curi.getRecorder().getRecordedOutput().getReplayInputStream().readFullyTo(buf);
        String requestString = new String(buf, "US-ASCII");
        assertTrue(requestString.startsWith("GET /test.txt HTTP/1.0\r\n"));
        // assertTrue(requestString.matches("(?is).*User-Agent: " + Pattern.quote(getUserAgentString()) + "\r\n.*"));
        assertTrue(requestString.matches("(?is).*Connection: close\r\n.*"));
        assertTrue(requestString.matches("(?is).*Accept: " + Pattern.quote("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8") + "\r\n.*"));
        assertTrue(requestString.matches("(?is).*Host: localhost:7777\r\n.*"));
        assertTrue(requestString.endsWith("\r\n\r\n"));
        
        buf = IOUtils.toByteArray(curi.getRecorder().getEntityReplayInputStream());
        String entityString = new String(buf, "US-ASCII");
        assertTrue(entityString.equals("I am an ascii text file 39 bytes long.\n"));
        
        assertEquals(curi.getContentLength(), 39);
        assertEquals(curi.getContentDigestSchemeString(), "sha1:Y6G7VXZWY52LQA774YOVJ7TPZXMOMUY7");
        assertEquals(curi.getContentType(), "text/plain");
        assertTrue(curi.getCredentials().isEmpty());
        assertTrue(curi.getFetchDuration() >= 0);
        assertTrue(curi.getFetchStatus() == 200);
        assertTrue(curi.getFetchType() == FetchType.HTTP_GET);
        assertEquals(curi.getRecordedSize(), curi.getContentSize());
    }

    protected String getUserAgentString() {
        return getClass().getName();
    }

    protected Recorder getRecorder() throws IOException {
        if (Recorder.getHttpRecorder() == null) {
            Recorder httpRecorder = new Recorder(
                    TmpDirTestCase.tmpDir(),
                    "tt12345http", 16 * 1024, 512 * 1024);

            Recorder.setHttpRecorder(httpRecorder);
        }
        
        return Recorder.getHttpRecorder();
    }
}
