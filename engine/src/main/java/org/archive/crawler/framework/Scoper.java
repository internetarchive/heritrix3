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

package org.archive.crawler.framework;

import java.util.logging.Logger;

import org.archive.crawler.reporting.CrawlerLoggerModule;
import org.archive.modules.CrawlURI;
import org.archive.modules.Processor;
import org.archive.modules.deciderules.DecideResult;
import org.archive.modules.deciderules.DecideRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.Lifecycle;

/**
 * Base class for Scopers.
 * Scopers test CrawlURIs against a scope.
 * Scopers allow logging of rejected CrawlURIs.
 * @author stack
 * @version $Date$, $Revision$
 */
public abstract class Scoper extends Processor implements Lifecycle {
    
    protected DecideRule scope;
    public DecideRule getScope() {
        return this.scope;
    }
    @Autowired
    public void setScope(DecideRule scope) {
        this.scope = scope;
    }
    
    protected Logger fileLogger = null;

    /**
     * If enabled, log decisions to file named logs/{spring-bean-id}.log. Format
     * is "[timestamp] [decision] [uri]" where decision is 'ACCEPT' or 'REJECT'.
     */
    {
        setLogToFile(false);
    }
    public boolean getLogToFile() {
        return (Boolean) kp.get("logToFile");
    }
    public void setLogToFile(boolean enabled) {
        kp.put("logToFile",enabled);
    }

    protected CrawlerLoggerModule loggerModule;
    public CrawlerLoggerModule getLoggerModule() {
        return this.loggerModule;
    }
    @Autowired
    public void setLoggerModule(CrawlerLoggerModule loggerModule) {
        this.loggerModule = loggerModule;
    }
    
    /**
     * Constructor.
     */
    public Scoper() {
        super();
    }

    protected boolean isRunning = false; 
    public void start() {
        if(isRunning) {
            return; 
        }
        if (getLogToFile() && fileLogger == null) {
            fileLogger = loggerModule.setupSimpleLog(getBeanName());
        }
        isRunning = true; 
    }
    public boolean isRunning() {
        return this.isRunning;
    }
    public void stop() {
        isRunning = false; 
    }

    /**
     * Schedule the given {@link CrawlURI CrawlURI} with the Frontier.
     * @param caUri The CrawlURI to be scheduled.
     * @return true if CrawlURI was accepted by crawl scope, false
     * otherwise.
     */
    protected boolean isInScope(CrawlURI caUri) {
        boolean result = false;
        DecideResult dr = scope.decisionFor(caUri);
        if (dr == DecideResult.ACCEPT) {
            result = true;
            if (fileLogger != null) {
                fileLogger.info("ACCEPT " + caUri); 
            }
        } else {
            outOfScope(caUri);
        }
        return result;
    }
    
    /**
     * Called when a CrawlURI is ruled out of scope.
     * Override if you don't want logs as coming from this class.
     * @param caUri CrawlURI that is out of scope.
     */
    protected void outOfScope(CrawlURI caUri) {
        if (fileLogger != null) {
            fileLogger.info("REJECT " + caUri); 
        }
    }
}
