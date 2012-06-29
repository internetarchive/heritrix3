package org.archive.modules.fetcher;

import java.io.IOException;
import java.util.logging.Logger;

import org.archive.modules.CrawlURI;
import org.archive.modules.Processor;
import org.archive.modules.ProcessorTestBase;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.util.Recorder;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.jetty.handler.DefaultHandler;
import org.mortbay.jetty.handler.HandlerList;
import org.mortbay.jetty.handler.ResourceHandler;

public abstract class FetchHTTPTestBase extends ProcessorTestBase {
    
    private static Logger logger = Logger.getLogger(FetchHTTPTestBase.class.getName());

    protected static final String HTDOCS_PATH = FetchHTTPTestBase.class.getResource("testdata").getFile();
    protected Server httpServer;
    protected Processor fetcher;

    abstract protected Processor makeModule() throws IOException;

    public Server startHttpFileServer(String path) throws Exception {
        Server server = new Server();
        SocketConnector sc = new SocketConnector();
        sc.setHost("127.0.0.1");
        sc.setPort(7777);
        server.addConnector(sc);
        ResourceHandler rhandler = new ResourceHandler();
        rhandler.setResourceBase(path);
        
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
        
        UURI uuri = UURIFactory.getInstance("http://localhost:7777/");
        CrawlURI curi = new CrawlURI(uuri);
        Recorder.setHttpRecorder(null)
        curi.setRecorder(Recorder.getHttpRecorder());
        
        getFetcher().process(curi);
        
        logger.info("data=" + curi.getData());
    }
}
