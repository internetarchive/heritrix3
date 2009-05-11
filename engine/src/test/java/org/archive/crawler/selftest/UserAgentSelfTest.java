/* 
 * Copyright (C) 2007 Internet Archive.
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
 * UserAgentSelfTest.java
 *
 * Created on Apr 27, 2007
 *
 * $Id:$
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
        sc.setHost("localhost");
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
