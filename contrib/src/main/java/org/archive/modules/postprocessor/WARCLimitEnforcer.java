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
package org.archive.modules.postprocessor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import org.archive.crawler.framework.CrawlController;
import org.archive.crawler.framework.CrawlStatus;
import org.archive.modules.CrawlURI;
import org.archive.modules.Processor;
import org.archive.modules.writer.WARCWriterProcessor;
import org.springframework.beans.factory.annotation.Autowired;

public class WARCLimitEnforcer extends Processor {

    private final static Logger log =
            Logger.getLogger(WARCLimitEnforcer.class.getName());

    protected Map<String, Map<String, Long>> limits = new HashMap<String, Map<String, Long>>();
    /**
     * Should match structure of {@link WARCWriterProcessor#getStats()}
     * @param limits
     */
    public void setLimits(Map<String, Map<String, Long>> limits) {
        this.limits = limits;
    }
    public Map<String, Map<String, Long>> getLimits() {
        return limits;
    }

    protected WARCWriterProcessor warcWriter;
    @Autowired
    public void setWarcWriter(WARCWriterProcessor warcWriter) {
        this.warcWriter = warcWriter;
    }
    public WARCWriterProcessor getWarcWriter() {
        return warcWriter;
    }

    protected CrawlController controller;
    public CrawlController getCrawlController() {
        return this.controller;
    }
    @Autowired
    public void setCrawlController(CrawlController controller) {
        this.controller = controller;
    }

    @Override
    protected boolean shouldProcess(CrawlURI uri) {
        return true;
    }

    @Override
    protected void innerProcess(CrawlURI uri) throws InterruptedException {
        for (String j: limits.keySet()) {
            for (String k: limits.get(j).keySet()) {
                Long limit = limits.get(j).get(k);

                Map<String, AtomicLong> valueBucket = warcWriter.getStats().get(j);
                if (valueBucket != null) {
                    AtomicLong value = valueBucket.get(k);
                    if (value != null
                            && value.get() >= limit) {
                        log.info("stopping crawl because warcwriter stats['" + j + "']['" + k + "']=" + value.get() + " exceeds limit " + limit);
                        controller.requestCrawlStop(CrawlStatus.FINISHED_WRITE_LIMIT);
                    }
                }
            }
        }
    }

}
