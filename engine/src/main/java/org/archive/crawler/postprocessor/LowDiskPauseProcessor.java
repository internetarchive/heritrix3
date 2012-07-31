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
package org.archive.crawler.postprocessor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.archive.crawler.framework.CrawlController;
import org.archive.modules.ProcessResult;
import org.archive.modules.Processor;
import org.archive.modules.CrawlURI;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Processor module which uses 'df -k', where available and with
 * the expected output format (on Linux), to monitor available 
 * disk space and pause the crawl if free space on  monitored 
 * filesystems falls below certain thresholds.
 * 
 * @deprecated Is highly system dependant. 
 *             Use {@link org.archive.crawler.monitor.DiskSpaceMonitor} instead.
 */
@Deprecated
public class LowDiskPauseProcessor extends Processor {

    @SuppressWarnings("unused")
    private static final long serialVersionUID = 3L;

    /**
     * Logger.
     */
    private static final Logger logger =
        Logger.getLogger(LowDiskPauseProcessor.class.getName());


    protected CrawlController controller;
    public CrawlController getCrawlController() {
        return this.controller;
    }
    @Autowired
    public void setCrawlController(CrawlController controller) {
        this.controller = controller;
    }
    
    /**
     * List of filessystem mounts whose 'available' space should be monitored
     * via 'df' (if available).
     */
    protected List<String> monitorMounts = new ArrayList<String>();
    public List<String> getMonitorMounts() {
        return this.monitorMounts;
    }
    public void setMonitorMounts(List<String> monitorMounts) {
        this.monitorMounts = monitorMounts;
    }

    /**
     * When available space on any monitored mounts falls below this threshold,
     * the crawl will be paused.
     */
    protected int pauseThresholdKb = 500*1024; // 500MB 
    public int getPauseThresholdKb() {
        return this.pauseThresholdKb;
    }
    public void setPauseThresholdKb(int pauseThresholdKb) {
        this.pauseThresholdKb = pauseThresholdKb;
    }
    
    /**
     * Available space via 'df' is rechecked after every increment of this much
     * content (uncompressed) is observed.
     */
    protected int recheckThresholdKb = 200*1024; // 200MB 
    public int getRecheckThresholdKb() {
        return this.recheckThresholdKb;
    }
    public void setRecheckThresholdKb(int recheckThresholdKb) {
        this.recheckThresholdKb = recheckThresholdKb;
    }
    
    protected int contentSinceCheck = 0;
    
    public static final Pattern VALID_DF_OUTPUT = 
        Pattern.compile("(?s)^Filesystem\\s+1K-blocks\\s+Used\\s+Available\\s+Use%\\s+Mounted on\\n.*");
    public static final Pattern AVAILABLE_EXTRACTOR = 
        Pattern.compile("(?m)\\s(\\d+)\\s+\\d+%\\s+(\\S+)$");
    
    /**
     * @param name Name of this writer.
     */
    public LowDiskPauseProcessor() {
    } 
    
    
    @Override
    protected boolean shouldProcess(CrawlURI curi) {
        return true;
    }

    @Override
    protected void innerProcess(CrawlURI uri) {
        throw new AssertionError();
    }
    
    /**
     * Notes a CrawlURI's content size in its running tally. If the 
     * recheck increment of content has passed through since the last
     * available-space check, checks available space and pauses the 
     * crawl if any monitored mounts are below the configured threshold. 
     * 
     * @param curi CrawlURI to process.
     */
    @Override
    protected ProcessResult innerProcessResult(CrawlURI curi) {
        synchronized (this) {
            contentSinceCheck += curi.getContentSize();
            if (contentSinceCheck/1024 > getRecheckThresholdKb()) {
                ProcessResult r = checkAvailableSpace(curi);
                contentSinceCheck = 0;
                return r;
            } else {
                return ProcessResult.PROCEED;
            }
        }
    }


    /**
     * Probe via 'df' to see if monitored mounts have fallen
     * below the pause available threshold. If so, request a 
     * crawl pause. 
     * @param curi Current context.
     */
    private ProcessResult checkAvailableSpace(CrawlURI curi) {
        try {
            String df = IOUtils.toString(Runtime.getRuntime().exec(
                    "df -k").getInputStream());
            Matcher matcher = VALID_DF_OUTPUT.matcher(df);
            if(!matcher.matches()) {
                logger.severe("'df -k' output unacceptable for low-disk checking");
                return ProcessResult.PROCEED;
            }
            List<String> monitoredMounts = getMonitorMounts();
            matcher = AVAILABLE_EXTRACTOR.matcher(df);
            while (matcher.find()) {
                String mount = matcher.group(2);
                if (monitoredMounts.contains(mount)) {
                    long availKilobytes = Long.parseLong(matcher.group(1));
                    int thresholdKilobytes = getPauseThresholdKb();
                    if (availKilobytes < thresholdKilobytes ) {
                        logger.log(Level.SEVERE, "Low Disk Pause",
                                availKilobytes + "K available on " + mount
                                        + " (below threshold "
                                        + thresholdKilobytes + "K)");
                        controller.requestCrawlPause();
                        return ProcessResult.PROCEED;
                    }
                }
            }
        } catch (IOException e) {
            curi.getNonFatalFailures().add(e);
        }
        return ProcessResult.PROCEED;
    }
}
