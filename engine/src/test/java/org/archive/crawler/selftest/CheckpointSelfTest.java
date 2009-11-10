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


import java.io.IOException;

import org.archive.crawler.framework.CrawlJob;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.jetty.servlet.ServletHandler;
import org.mortbay.jetty.servlet.ServletHolder;


/**
 * Assumes checkpoint was run during the SelfTest.
 * @author stack
 * @version $Date$ $Version$
 */
public class CheckpointSelfTest extends SelfTestBase {

    final private static String HOST = "127.0.0.1";
    
    final private static int MIN_PORT = 7000;
    
    final private static int MAX_PORT = 7010;
    
    final private static int MAX_HOPS = 1;
    

    private Server[] servers;
    
    
    public CheckpointSelfTest() {
    }

    protected String getSeedsString() {
        String seedsString = "";
        for(int p = MIN_PORT; p <= MAX_PORT; p++) {
            seedsString += "http://127.0.0.1:"+p+"/random\\\n";
        }
        return seedsString;
    }
    
    @Override
    protected void stopHttpServer() {
        boolean fail = false;
        for (int i = 0; i < servers.length; i++) try {
            servers[i].stop();
        } catch (Exception e) {
            fail = true;
            e.printStackTrace();
        }
        
        if (fail) {
            throw new AssertionError();
        }
    }
    
    @Override
    protected void startHttpServer() throws Exception {
        this.servers = new Server[MAX_PORT - MIN_PORT];
        for (int i = 0; i < servers.length; i++) {
            servers[i] = makeHttpServer(i + MIN_PORT);
            servers[i].start();
        }
    }
    
    
    private Server makeHttpServer(int port) throws Exception {
        Server server = new Server();
        SocketConnector sc = new SocketConnector();
        sc.setHost(HOST);
        sc.setPort(port);
        server.addConnector(sc);
        ServletHandler servletHandler = new ServletHandler();
        server.setHandler(servletHandler);

        RandomServlet random = new RandomServlet();
        random.setHost(HOST);
        random.setMinPort(MIN_PORT);
        random.setMaxPort(MAX_PORT);
        random.setMaxHops(MAX_HOPS);
        random.setPathRoot("random");

        ServletHolder holder = new ServletHolder(random);
        servletHandler.addServletWithMapping(holder, "/random/*");
        server.start();
        return server;
    }


//    @Override
    protected void waitForCrawlFinish() throws Exception {

        Thread.sleep(2000);
        CrawlJob cj = heritrix.getEngine().getJob("selftest-job");
        
        // TODO: pause, checkpoint, launch new job, yadda yadda
        
        // for now, just kill & wait
        cj.terminate();
        super.waitForCrawlFinish();
        
//        invokeAndWait("basic", "requestCrawlPause", CrawlStatus.PAUSED);
//        invokeAndWait("basic", "requestCrawlCheckpoint", CrawlStatus.PAUSED);
//        invokeAndWait("basic", "requestCrawlStop", CrawlStatus.FINISHED);
//        waitFor("org.archive.crawler:*,name=basic,type=org.archive.crawler.framework.CrawlController", false);
//        stopHeritrix();
//        Set<ObjectName> set = dumpMBeanServer();
//        if (!set.isEmpty()) {
//            throw new Exception("Mbeans lived on after stopHeritrix: " + set);
//        }
//        this.heritrixThread = new HeritrixThread(new String[] {
//            "-j", getCrawlDir().getAbsolutePath() + "/jobs", "-n"
//        });
//        this.heritrixThread.start();
//        
//        ObjectName cjm = getEngine();
//        String[] checkpoints = (String[])server.invoke(
//                cjm,
//                "listCheckpoints", 
//                new Object[] { "completed-basic" },
//                new String[] { "java.lang.String" });
//
//        assertEquals(1, checkpoints.length);
//        File recoverLoc = new File(getCompletedJobDir().getParentFile(), "recovered");
//        FileUtils.deleteDir(recoverLoc);
//        String[] oldPath = new String[] { getCompletedJobDir().getAbsolutePath() };
//        String[] newPath = new String[] { recoverLoc.getAbsolutePath() };
//        server.invoke(
//                cjm,
//                "recoverCheckpoint", 
//                new Object[] {
//                        "completed-basic",
//                        "active-recovered",
//                        checkpoints[0], 
//                        oldPath, 
//                        newPath
//                },
//                new String[] { 
//                        String.class.getName(),
//                        String.class.getName(),
//                        String.class.getName(),
//                        "java.lang.String[]",
//                        "java.lang.String[]"
//                        });
//        ObjectName cc = getCrawlController("recovered");
//        waitFor(cc);
//        invokeAndWait("recovered", "requestCrawlResume", CrawlStatus.FINISHED);
//        
//        server.invoke(
//                cjm, 
//                "closeSheetManagerStub", 
//                new Object[] { "completed-basic" },
//                new String[] { "java.lang.String" });
    }


    

    @Override
    protected void verifyCommon() throws IOException {
        // checkpointing rotated the logs so default behavior won't work here
        // FIXME: Make this work :)
        
    }


    protected void verify() throws Exception {
        // FIXME: Complete test.
//        assertTrue("neither feature nor test yet implemented",false);
    }

    /**
     * Repeat core testSomething 100 times. Rename to JUNit convention
     * to enable. 
     */
    public void xestSomething100() {
        for(int i = 0; i < 100; i++) {
            try {
                testSomething();
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } 
        }
    }
//    @Override
//    public void testSomething() throws Exception {
//
//    }

    
    
}
