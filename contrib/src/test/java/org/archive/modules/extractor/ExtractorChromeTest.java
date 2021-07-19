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

package org.archive.modules.extractor;

import org.archive.crawler.framework.CrawlController;
import org.archive.crawler.framework.Frontier;
import org.archive.crawler.reporting.CrawlerLoggerModule;
import org.archive.modules.CrawlURI;
import org.archive.modules.DispositionChain;
import org.archive.modules.Processor;
import org.archive.net.UURIFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.stream.Collectors.toList;
import static org.archive.modules.CrawlURI.FetchType.HTTP_GET;
import static org.archive.modules.CrawlURI.FetchType.HTTP_POST;
import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeNoException;

public class ExtractorChromeTest {
    private static Server server;

    @BeforeClass
    public static void startServer() throws Exception {
        server = new Server(InetSocketAddress.createUnresolved("127.0.0.1", 7778));
        server.setHandler(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                switch (target) {
                    case "/":
                        response.setContentType("text/html");
                        response.getWriter().write("<a href=http://example.org/page2.html>link</a>" +
                                "<img src=/blue.png>" +
                                "<script>fetch('/post', {method: 'POST', body: 'hello'});</script>");
                        baseRequest.setHandled(true);
                        break;
                    case "/blue.png":
                        response.setContentType("image/png");
                        response.getWriter().write("bogus png");
                        baseRequest.setHandled(true);
                        break;
                    case "/post":
                        response.setContentType("plain/text");
                        response.getWriter().write("method=" + request.getMethod());
                        baseRequest.setHandled(true);
                        break;
                    default:
                        System.err.println("Unhandled target: " + target);
                        break;
                }

            }
        });
        server.start();
    }

    @AfterClass
    public static void stopServer() throws Exception {
        server.stop();
    }

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void test() throws IOException {
        List<CrawlURI> processedURIs = Collections.synchronizedList(new ArrayList<>());
        CrawlController controller = new CrawlController();
        DispositionChain dispositionChain = new DispositionChain();
        dispositionChain.setProcessors(Arrays.asList(new Processor() {
            @Override
            protected boolean shouldProcess(CrawlURI uri) {
                return true;
            }

            @Override
            protected void innerProcess(CrawlURI uri) {
                processedURIs.add(uri);
            }
        }));
        controller.setDispositionChain(dispositionChain);
        controller.setLoggerModule(new CrawlerLoggerModule() {
            @Override
            public Logger getUriProcessing() {
                Logger logger = Logger.getAnonymousLogger();
                logger.setLevel(Level.WARNING);
                return logger;
            }
        });
        Frontier frontier = createMock(Frontier.class);
        frontier.considerIncluded(anyObject());
        expectLastCall().anyTimes();
        frontier.beginDisposition(anyObject());
        expectLastCall().anyTimes();
        frontier.endDisposition();
        expectLastCall().anyTimes();
        frontier.finished(anyObject());
        expectLastCall().anyTimes();
        replay(frontier);
        controller.setFrontier(frontier);

        ExtractorChrome extractor = new ExtractorChrome(controller, event -> { /* ignored */ });
        try {
            extractor.start();
        } catch (RuntimeException e) {
            assumeNoException("Unable to start Chrome", e);
        }
        try {
            CrawlURI curi = new CrawlURI(UURIFactory.getInstance("http://127.0.0.1:7778/"));
            extractor.innerExtract(curi);
            List<String> outLinks = curi.getOutLinks().stream().map(CrawlURI::toString).sorted().collect(toList());
            assertEquals(Collections.singletonList("http://example.org/page2.html"), outLinks);
        } finally {
            extractor.stop();
        }

        assertEquals(3, processedURIs.size());
        for (CrawlURI curi: processedURIs) {
            assertEquals(200, curi.getFetchStatus());
            assertEquals(curi.getUURI().getPath().equals("/post") ? HTTP_POST : HTTP_GET, curi.getFetchType());
            assertNotNull(curi.getContentDigest());
            assertTrue(curi.getContentSize() > 0);
        }
    }
}