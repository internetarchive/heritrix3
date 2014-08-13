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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.archive.crawler.Heritrix;
import org.archive.io.ArchiveRecordHeader;
import org.archive.io.arc.ARCReaderFactory;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.util.TmpDirTestCase;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.jetty.handler.DefaultHandler;
import org.mortbay.jetty.handler.HandlerList;
import org.mortbay.jetty.handler.ResourceHandler;

/**
 * Base class for 'self tests', integrations tests formatted as unit 
 * tests, where the crawler launches an entire crawl exercising multiple
 * features against a test harness website.
 * 
 * @contributor pjack
 * @contributor gojomo
 */
public abstract class SelfTestBase extends TmpDirTestCase {

    final private Logger LOGGER = 
        Logger.getLogger(SelfTestBase.class.getName());
    
    protected Heritrix heritrix;
    protected Server httpServer;
    
    protected void open() throws Exception {
        // We expect to be run from the project directory.
        // (Both eclipse and maven run junit tests from there).
        String name = getSelfTestName();
        
        // Make sure the project directory contains a selftest profile 
        // and content for the self test.
        File src = getTestDataDir();
        if (!src.exists()) {
            throw new Exception("No selftest directory for " + name);
        }
        
        // Create temporary directories for Heritrix to run in.
        File tmpDir = new File(getTmpDir(), "selftest");
        File tmpTestDir = new File(tmpDir, name);
        
        // If we have an old job lying around from a previous run, delete it.
        File tmpJobs = new File(tmpTestDir, "jobs");
        if (tmpJobs.exists()) {
            FileUtils.deleteDirectory(tmpJobs);
        }
        
        // Copy the selftest's profile in the project directory to the
        // default profile in the temporary Heritrix directory.
        File tmpDefProfile = new File(tmpJobs, "selftest-job");
        File profileTemplate = new File(src, "profile");
        if(profileTemplate.exists()) {
            org.apache.commons.io.FileUtils.copyDirectory(profileTemplate, tmpDefProfile);
        } else {
            org.archive.util.FileUtils.ensureWriteableDirectory(tmpDefProfile);
        }
        
        // Start up a Jetty that serves the selftest's content directory.
        startHttpServer();
        
        // Copy configuration for eg Logging over
        File tmpConfDir = new File(tmpTestDir, "conf");
        org.archive.util.FileUtils.ensureWriteableDirectory(tmpConfDir);
        File srcConf = new File(src.getParentFile(), "conf");
        FileUtils.copyDirectory(srcConf, tmpConfDir);

        String crawlerBeansText = FileUtils.readFileToString(
                new File(srcConf, "selftest-crawler-beans.cxml"));
        crawlerBeansText = changeGlobalConfig(crawlerBeansText);
        File crawlerBeans = new File(tmpDefProfile, "selftest-crawler-beans.cxml");
        FileWriter fw = new FileWriter(crawlerBeans);
        fw.write(crawlerBeansText);
        fw.close();
        
        startHeritrix(tmpTestDir.getAbsolutePath());
        
        waitForCrawlFinish();
    }
    
    
    protected String changeGlobalConfig(String config) {
        config = config.replace(
                "@@URL_VALUE@@","http://crawler.archive.org/selftestcrawl");
        // if not already changed, used default self-test start URL
        config = config.replace(
                "@@SEEDS_VALUE@@", getSeedsString());
        // if not already replaced, remove other placeholder
        config = config.replace("@@MORE_PROPERTIES@@","");
        return config;
    }
    
    /**
     * Get seeds for this test. Should be in form that can be
     * spliced into a Java properties-format string (any internal
     * lineends escaped with '\'). 
     * @return String seeds to use
     */
    protected String getSeedsString() {
        // default barring overrides
        return "http://127.0.0.1:7777/index.html";
    }
    

    protected void close() throws Exception {
        stopHttpServer();
        stopHeritrix();
    }

    public void testSomething() throws Exception {
        try {
            boolean fail = false;
            try {
                open();
                verifyCommon();
                verify();
            } finally {
                try {
                    close();
                } catch (Exception e) {
                    e.printStackTrace();
                    fail = true;
                }
            }
            assertFalse(fail);
        } catch (Exception e) {
            // I hate maven.
            e.printStackTrace();
            throw e;
        }
    }
    
    
    protected abstract void verify() throws Exception;
    
    
    protected void stopHttpServer() throws Exception {
        try {
            httpServer.stop();  
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    
    protected void startHttpServer() throws Exception {
        Server server = new Server();
        SocketConnector sc = new SocketConnector();
        sc.setHost("127.0.0.1");
        sc.setPort(7777);
        server.addConnector(sc);
        ResourceHandler rhandler = new ResourceHandler();
        rhandler.setResourceBase(getSrcHtdocs().getAbsolutePath());
        
        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[] { rhandler, new DefaultHandler() });
        server.setHandler(handlers);
        
        this.httpServer = server;
        server.start();
    }
    
    
    protected void startHeritrix(String path) throws Exception {
        String authPassword = 
            (new BigInteger(SecureRandom.getSeed(16))).abs().toString(16);
        String[] args = { "-j", path + "/jobs", "-a", authPassword };
        // TODO: add auth password? 
        heritrix = new Heritrix();
        heritrix.instanceMain(args);
        
        configureHeritrix();

        heritrix.getEngine().requestLaunch("selftest-job");
    }
    
    
    protected void configureHeritrix() throws Exception {
        // by default do nothing
    }
    
    
    protected void stopHeritrix() throws Exception {
        heritrix.getEngine().shutdown();
        heritrix.getComponent().stop(); 
    }
    
    protected void waitForCrawlFinish() throws Exception {
        heritrix.getEngine().waitForNoRunningJobs(0);
    }
    
    protected File getSrcHtdocs() {
        return new File(getTestDataDir(), "htdocs");
    }

    protected File getTestDataDir() {
        File r = new File("testdata");
        if (!r.exists()) {
            r = new File("engine");
            r = new File(r, "testdata");
            if (!r.exists()) {
                throw new IllegalStateException(
                        "Can't find selfest testdata " +
                        "(tried testdata/selftest and " +
                        "heritrix/testdata/selftest)");
            }
        }
        r = new File(r, "selftest");
        r = new File(r, getSelfTestName());
        if (!r.exists()) {
            throw new IllegalStateException("No testdata directory: " 
                    + r.getAbsolutePath());
        }
        return r;
    }
    
    
    protected File getCrawlDir() {
        File tmp = getTmpDir();
        File selftest = new File(tmp, "selftest");
        File crawl = new File(selftest, getSelfTestName());
        return crawl;
    }  
    
    protected File getJobDir() {
        File crawl = getCrawlDir();
        File jobs = new File(crawl, "jobs");
        File theJob = new File(jobs, "selftest-job");
        return theJob;
    }
    
    
    protected File getArcDir() {
        return new File(getJobDir(), "arcs");
    }
    
    
    protected File getLogsDir() {
        return new File(getJobDir(), "logs");
    }



    private String getSelfTestName() {
        String full = getClass().getName();
        int i = full.lastIndexOf('.');
        return full.substring(i + 1);
    }
    
    protected void verifyArcsClosed() {
        File arcsDir = getArcDir();
        if (!arcsDir.exists()) {
            throw new IllegalStateException("Missing arc dir " + 
                    arcsDir.getAbsolutePath());
        }
        for (File f: arcsDir.listFiles()) {
            String fn = f.getName();
            if (fn.endsWith(".open")) {
                throw new IllegalStateException(
                        "Arc file not closed at end of crawl: " + f.getAbsolutePath());
            }
        }
    }
    
    protected void verifyLogFileEmpty(String logFileName) {
        File logsDir = getLogsDir();
        File log = new File(logsDir, logFileName);
        if (log.length() != 0) {
            throw new IllegalStateException("Log " + logFileName + 
                    " isn't empty.");
        }
    }
    
    
    protected void verifyCommon() throws Exception {
        verifyLogFileEmpty("uri-errors.log");
        verifyLogFileEmpty("runtime-errors.log");
        verifyLogFileEmpty("local-errors.log");
        verifyProgressStatistics();
        verifyArcsClosed();
    }
    
    
    protected void verifyProgressStatistics() throws IOException {
        File logs = new File(getJobDir(), "logs");
        File statsFile = new File(logs, "progress-statistics.log");
        String stats = FileUtils.readFileToString(statsFile);
        if (!stats.contains("CRAWL RUNNING - Preparing")) {
            fail("progress-statistics.log has no Prepared line.");
        }
        if (!stats.contains("CRAWL RUNNING - Running")) {
            fail("progress-statistics.log has no Running line.");
        }
        if (!stats.contains("CRAWL ENDING - Finished")) {
            fail("progress-statistics.log has missing/wrong Finished line.");
        }
        if (!stats.contains("doc/s(avg)")) {
            fail("progress-statistics.log has no legend.");
        }
    }
    
    
    protected List<ArchiveRecordHeader> headersInArcs() throws IOException {
        List<ArchiveRecordHeader> result = new ArrayList<ArchiveRecordHeader>();
        File arcsDir = getArcDir();
        if (!arcsDir.exists()) {
            throw new IllegalStateException("Missing arc dir " + 
                    arcsDir.getAbsolutePath());
        }
        File[] files = arcsDir.listFiles();
        if (files == null) {
            return Collections.emptyList();
        }
        for (File f: files) {
            result.addAll(ARCReaderFactory.get(f).validate());
        }
        return result;
    }
    
    
    protected Set<String> filesInArcs() throws IOException {
        List<ArchiveRecordHeader> headers = headersInArcs();
        HashSet<String> result = new HashSet<String>();
        for (ArchiveRecordHeader arh: headers) {
            // ignore 'filedesc:' record
            if(arh.getUrl().startsWith("filedesc:")) {
                continue; 
            }
            UURI uuri = UURIFactory.getInstance(arh.getUrl());
            String path = uuri.getPath();
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (arh.getUrl().startsWith("http:")) {
                result.add(path);
            }
        }
        LOGGER.finest(result.toString());
        return result;
    }
}
