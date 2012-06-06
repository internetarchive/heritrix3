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
package org.archive.modules.net;

import org.archive.io.ReadSource;
import org.archive.modules.CrawlURI;
import org.archive.spring.ConfigString;

/**
 * Follow a custom-written robots policy, rather than the site's own declarations 
 * 
 * Does not support overlays of different custom-robots; instead it is 
 * recommended each custom policy be declared as a separate bean, with a 
 * distinct name. 
 * 
 * Then, overlay the CrawlMetadata robotsPolicyName for each group of URIs 
 * that needs custom treatment
 * 
 * @contributor gojomo
 */
public class CustomRobotsPolicy extends RobotsPolicy {

    protected Robotstxt customRobotstxt;
    
    /** textual alternate robots.txt rules to follow */ 
    protected ReadSource customRobots = new ConfigString("");
    public ReadSource getCustomRobots() {
        return customRobots;
    }
    public void setCustomRobots(ReadSource customRobots) {
        this.customRobots = customRobots;
        customRobotstxt = new Robotstxt(customRobots); 
    }
    
    /** whether to obey the 'nofollow' directive in an HTML META ROBOTS element */
    protected boolean obeyMetaRobotsNofollow = true; 
    public boolean isObeyMetaRobotsNofollow() {
        return obeyMetaRobotsNofollow;
    }
    public void setObeyMetaRobotsNofollow(boolean obeyMetaRobotsNofollow) {
        this.obeyMetaRobotsNofollow = obeyMetaRobotsNofollow;
    }
    
    @Override
    public boolean allows(String userAgent, CrawlURI curi, Robotstxt robotstxt) {
        return customRobotstxt.getDirectivesFor(userAgent).allows(getPathQuery(curi));
    }

    @Override
    public boolean obeyMetaRobotsNofollow() {
        return obeyMetaRobotsNofollow;
    }
}
