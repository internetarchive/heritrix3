/* Scoper
 * 
 * Created on Jun 6, 2005
 *
 * Copyright (C) 2005 Internet Archive.
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
 */
package org.archive.crawler.framework;

import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.crawler.datamodel.CrawlURI;
import org.archive.crawler.reporting.CrawlerLoggerModule;
import org.archive.crawler.util.LogUtils;
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
    private static Logger LOGGER =
        Logger.getLogger(Scoper.class.getName());
    
    FileHandler fileLogger = null; 
    
    /**
     * If enabled, override default logger for this class (Default logger writes
     * the console). Override logger will instead send all logging to a file
     * named for this class in the job log directory. Set the logging level and
     * other characteristics of the override logger such as rotation size,
     * suffix pattern, etc. in heritrix.properties. This attribute is only
     * checked once, on startup of a job.
     */
    {
        setLogToFile(true);
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
    
    protected DecideRule scope;
    public DecideRule getScope() {
        return this.scope;
    }
    @Autowired
    public void setDecideRule(DecideRule scope) {
        this.scope = scope;
    }
    
    // FIXME: Weirdo log overriding might not work on a per-subclass basis,
    // we may need to cut and paste it to the three subclasses, or eliminate
    // it in favor of java.util.logging best practice.
    //
    // Also, eliminating weirdo log overriding would mean we wouldn't need to
    // tie into the CrawlController; we'd just need the scope.
    
    /**
     * Constructor.
     */
    public Scoper() {
        super();
    }

    boolean isRunning = false; 
    public void start() {
        if(isRunning) {
            return; 
        }
        if (getLogToFile()) {
            // Set up logger for this instance.  May have special directives
            // since this class can log scope-rejected URLs.
            fileLogger = LogUtils.createFileLogger(loggerModule.getPath().getFile(),
                this.getClass().getName(),
                Logger.getLogger(this.getClass().getName()));
        }
        isRunning = true; 
    }
    
    public boolean isRunning() {
        return this.isRunning;
    }
    public void stop() {
        if(fileLogger!=null) {
            fileLogger.close();
        }
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
// FIXME!:        getController().setStateProvider(caUri);
        DecideResult dr = scope.decisionFor(caUri);
        if (dr == DecideResult.ACCEPT) {
            result = true;
            if (LOGGER.isLoggable(Level.FINER)) {
                LOGGER.finer("Accepted: " + caUri);
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
        if (!LOGGER.isLoggable(Level.INFO)) {
            return;
        }
        LOGGER.info(caUri.getUURI().toString());
    }



}
