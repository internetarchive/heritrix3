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

package org.archive.crawler.selftest;

import org.archive.util.ArchiveUtils;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.resource.ResourceFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author pjack
 *
 */
public class UserAgentSelfTest extends SelfTestBase {


    private UserAgentServlet servlet;

    
    final private static String EXPECTED_UA = 
        "Mozilla/5.0 (compatible; heritrix/" + ArchiveUtils.VERSION 
        + " +http://crawler.archive.org/selftestcrawl)";
    
    @Override
    protected void verify() throws Exception {
        assertEquals(EXPECTED_UA, servlet.getUserAgent());
//        assertEquals(EXPECTED_FROM, servlet.getFrom());
    }


    @Override
    protected void startHttpServer() throws Exception {
        Server server = new Server();
        
        ServerConnector sc = new ServerConnector(server);
        sc.setHost("127.0.0.1");
        sc.setPort(7777);
        server.addConnector(sc);
        ResourceHandler rhandler = new ResourceHandler();
        ResourceFactory resourceFactory = ResourceFactory.of(server);
        rhandler.setBaseResource(resourceFactory.newResource(getSrcHtdocs().toPath().toAbsolutePath()));
        
        this.servlet = new UserAgentServlet();
        ServletContextHandler contextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        contextHandler.addServlet(servlet, "/*");

        server.setHandler(new Handler.Sequence(rhandler,
                contextHandler,
                new DefaultHandler()));

        this.httpServer = server;
        this.httpServer.start();
    }

    
    
    
}
