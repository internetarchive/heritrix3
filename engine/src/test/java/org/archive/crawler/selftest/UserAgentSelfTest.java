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
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.jetty.handler.DefaultHandler;
import org.mortbay.jetty.handler.HandlerList;
import org.mortbay.jetty.handler.ResourceHandler;
import org.mortbay.jetty.servlet.ServletHandler;
import org.mortbay.jetty.servlet.ServletHolder;

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
        
        SocketConnector sc = new SocketConnector();
        sc.setHost("127.0.0.1");
        sc.setPort(7777);
        server.addConnector(sc);
        ResourceHandler rhandler = new ResourceHandler();
        rhandler.setResourceBase(getSrcHtdocs().getAbsolutePath());
        
        ServletHandler servletHandler = new ServletHandler();        
        
        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[] { 
                rhandler, 
                servletHandler,
                new DefaultHandler() });
        server.setHandler(handlers);

        this.servlet = new UserAgentServlet();
        ServletHolder holder = new ServletHolder(servlet);
        servletHandler.addServletWithMapping(holder, "/*");

        this.httpServer = server;
        this.httpServer.start();
    }

    
    
    
}
