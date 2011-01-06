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

import org.archive.crawler.reporting.CrawlerLoggerModule;
import org.archive.modules.CrawlURI;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.annotation.Autowired;

public class DecideRuleSequence extends DecideRule implements BeanNameAware{
    final private static Logger LOGGER = 
        Logger.getLogger(DecideRuleSequence.class.getName());
    private static final long serialVersionUID = 3L;
    
    protected Logger fileLogger = null; 
    
    /**
     * If enabled, log decisions to file named logs/{spring-bean-id}.log. Format is:
     * [timestamp] [decisive-rule-num] [decisive-rule-class] [decision] [uri]
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
    
    @SuppressWarnings("unchecked")
    public List<DecideRule> getRules() {
        return (List<DecideRule>) kp.get("rules");
    }
    public void setRules(List<DecideRule> rules) {
        kp.put("rules", rules);
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

        if (fileLogger != null) {
            fileLogger.info(decisiveRuleNumber + " " + decisiveRule.getClass().getSimpleName() + " " + result + " " + uri);
        }

        return result;
    }
    
    public void start() {
        if (getLogToFile() && fileLogger == null) {
            fileLogger = loggerModule.setupSimpleLog(getBeanName());
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

}
