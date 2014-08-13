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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.mortbay.jetty.Handler;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.jetty.handler.DefaultHandler;
import org.mortbay.jetty.handler.HandlerList;
import org.mortbay.jetty.handler.ResourceHandler;
import org.mortbay.jetty.servlet.ServletHandler;
import org.mortbay.jetty.servlet.ServletHolder;

/**
 * Test form-based authentication
 *
 * @contributor stack
 * @contributor gojomo
 */
public class FormAuthSelfTest
    extends SelfTestBase
{
    /**
     * Files to find as a list.
     */
    final private static Set<String> EXPECTED = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList(new String[] {
            "login/login.html", "success.html", "robots.txt", "favicon.ico"
    })));

    @Override
    protected void verify() throws Exception {
        Set<String> found = this.filesInArcs();
        assertEquals("wrong files in ARCs",EXPECTED,found);
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
        
        ServletHolder holder = new ServletHolder(new FormAuthServlet());
        servletHandler.addServletWithMapping(holder, "/login/*");

        this.httpServer = server;
        this.httpServer.start();
    }

    protected String getSeedsString() {
        return "http://127.0.0.1:7777/login/login.html";
    }
    
    @Override
    protected String changeGlobalConfig(String config) {
        String newCredStore = 
            "<bean id=\"credentialStore\" class=\"org.archive.modules.credential.CredentialStore\">\n" + 
            "  <property name=\"credentials\">\n" + 
            "   <map>\n" + 
            "    <entry key=\"test2\">\n" + 
            "     <bean class=\"org.archive.modules.credential.HtmlFormCredential\">\n" + 
            "     <property name=\"domain\" value=\"127.0.0.1:7777\"/>\n" + 
            "     <property name=\"loginUri\" value=\"http://127.0.0.1:7777/login/login.html\"/>\n" + 
            "     <property name=\"formItems\">\n" + 
            "      <map>\n" + 
            "       <entry key=\"username\" value=\"Mr. Happy Pants\"/>\n" + 
            "       <entry key=\"password\" value=\"xyzzy\"/>\n" + 
            "      </map>\n" + 
            "     </property>\n" + 
            "     </bean>\n" + 
            "    </entry>\n" + 
            "   </map>\n" + 
            "  </property>\n" + 
            "</bean>";
        config = config.replaceFirst(
                "(?s)<bean id=\"credentialStore\".*?</bean>", 
                newCredStore);
        config = config.replace(
                "@@MORE_PROPERTIES@@", 
                "candidatesProcessor.seedsRedirectNewSeeds=false");
        return super.changeGlobalConfig(config);
    }

}

