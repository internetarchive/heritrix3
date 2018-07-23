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

package org.archive.modules.deciderules;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.modules.CrawlURI;
import org.archive.modules.SimpleFileLoggerProvider;
import org.archive.modules.net.CrawlHost;
import org.archive.modules.net.ServerCache;
import org.json.JSONObject;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.Lifecycle;

public class DecideRuleSequence extends DecideRule implements BeanNameAware, Lifecycle {
    final private static Logger LOGGER = 
            Logger.getLogger(DecideRuleSequence.class.getName());
    private static final long serialVersionUID = 3L;

    protected transient Logger fileLogger = null;

    /**
     * If enabled, log decisions to file named logs/{spring-bean-id}.log. Format
     * is: [timestamp] [decisive-rule-num] [decisive-rule-class] [decision]
     * [uri] [extraInfo]
     * 
     * Relies on Spring Lifecycle to initialize the log. Only top-level
     * beans get the Lifecycle treatment from Spring, so bean must be top-level
     * for logToFile to work. (This is true of other modules that support
     * logToFile, and anything else that uses Lifecycle, as well.)
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

    /**
     * Whether to include the "extra info" field for each entry in crawl.log.
     * "Extra info" is a json object with entries "host", "via", "source" and
     * "hopPath".
     */
    protected boolean logExtraInfo = false;
    public boolean getLogExtraInfo() {
        return logExtraInfo;
    }
    public void setLogExtraInfo(boolean logExtraInfo) {
        this.logExtraInfo = logExtraInfo;
    }

    // provided by CrawlerLoggerModule which is in heritrix-engine, inaccessible
    // from here, thus the need for the SimpleFileLoggerProvider interface
    protected SimpleFileLoggerProvider loggerModule;
    public SimpleFileLoggerProvider getLoggerModule() {
        return this.loggerModule;
    }
    @Autowired
    public void setLoggerModule(SimpleFileLoggerProvider loggerModule) {
        this.loggerModule = loggerModule;
    }

    @SuppressWarnings("unchecked")
    public List<DecideRule> getRules() {
        return (List<DecideRule>) kp.get("rules");
    }
    public void setRules(List<DecideRule> rules) {
        kp.put("rules", rules);
    }

    protected ServerCache serverCache;
    public ServerCache getServerCache() {
        return this.serverCache;
    }
    @Autowired
    public void setServerCache(ServerCache serverCache) {
        this.serverCache = serverCache;
    }

    public DecideResult innerDecide(CrawlURI uri) {
        DecideRule decisiveRule = null;
        int decisiveRuleNumber = -1;
        DecideResult result = DecideResult.NONE;
        List<DecideRule> rules = getRules();
        int max = rules.size();

        for (int i = 0; i < max; i++) {
            DecideRule rule = rules.get(i);
            if (rule.onlyDecision(uri) != result) {
                DecideResult r = rule.decisionFor(uri);
                if (LOGGER.isLoggable(Level.FINEST)) {
                    LOGGER.finest("DecideRule #" + i + " " + 
                            rule.getClass().getName() + " returned " + r + " for url: " + uri);
                }
                if (r != DecideResult.NONE) {
                    result = r;
                    decisiveRule = rule;
                    decisiveRuleNumber = i;
                }
            }
        }

        decisionMade(uri, decisiveRule, decisiveRuleNumber, result);

        return result;
    }

    protected void decisionMade(CrawlURI uri, DecideRule decisiveRule,
            int decisiveRuleNumber, DecideResult result) {
        if (fileLogger != null) {
            JSONObject extraInfo = null;
            if (logExtraInfo) {
                CrawlHost crawlHost = getServerCache().getHostFor(uri.getUURI());
                String host = "-";
                if (crawlHost != null) {
                    host  = crawlHost.fixUpName();
                }

                extraInfo = new JSONObject();
                extraInfo.put("hopPath", uri.getPathFromSeed());
                extraInfo.put("via", uri.getVia());
                extraInfo.put("seed", uri.getSourceTag());
                extraInfo.put("host", host);
            }

            fileLogger.info(decisiveRuleNumber 
                    + " " + decisiveRule.getClass().getSimpleName() 
                    + " " + result 
                    + " " + uri
                    + (extraInfo != null ? " " + extraInfo : ""));
        }
    }

    protected String beanName;
    public String getBeanName() {
        return this.beanName;
    }
    @Override
    public void setBeanName(String name) {
        this.beanName = name;
    }

    protected boolean isRunning = false;
    @Override
    public boolean isRunning() {
        return isRunning;
    }
    @Override
    public void start() {
        if (getLogToFile() && fileLogger == null) {
            // loggerModule.start() creates the log directory, and evidently
            // it's possible for this module to start before loggerModule,
            // so we need to run this here to prevent an exception
            loggerModule.start();
            fileLogger = loggerModule.setupSimpleLog(getBeanName());
        }
        isRunning = true;
    }
    @Override
    public void stop() {
        isRunning = false;
    }
}
