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

import java.util.LinkedList;
import java.util.List;

import org.archive.modules.CrawlURI;

/**
 * Follow a most-favored robots policy -- allowing an URL if either the 
 * conventionally-configured User-Agent, or any of a number of alternate 
 * User-Agents (from the candidateUserAgents list) would be allowed. 
 * 
 * Some possible rationales for using this policy could include: 
 * 
 * (1) the current bot is the successor to a previous differently-named bot, 
 * which could reasonably obey the directives for the previous agent if 
 * they are more permissive; 
 * 
 * (2) a project with negotiated or statutory permission to ignore robots still 
 * wishes only to collect material a site has allowed at least some named 
 * robots to collect. (That is, assume that if any of a number of robots is
 * allowed, it will not be disruptive to collect.) 
 * 
 * @contributor gojomo
 */
public class MostFavoredRobotsPolicy extends RobotsPolicy {
    
    /** list of user-agents to try; if any are allowed, a URI will be crawled */
    protected List<String> candidateUserAgents = new LinkedList<String>();
    public List<String> getCandidateUserAgents() {
        return candidateUserAgents;
    }
    public void setCandidateUserAgents(List<String> candidateUserAgents) {
        this.candidateUserAgents = candidateUserAgents;
    }
    
    /** whether to adopt the user-agent that is allowed for the fetch */
    protected boolean shouldMasquerade = true;
    public boolean getShouldMasquerade() {
        return shouldMasquerade;
    }
    public void setShouldMasquerade(boolean shouldMasquerade) {
        this.shouldMasquerade = shouldMasquerade;
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
        if (robotstxt.getDirectivesFor(userAgent).allows(getPathQuery(curi))) {
            return true;
        }
        for(String candidate : candidateUserAgents) {
            if (robotstxt.getDirectivesFor(candidate).allows(getPathQuery(curi))) {
                if(shouldMasquerade) {
                    curi.setUserAgent(candidate);
                }
                return true;
            }
        }
        // TODO: expand to offer option of following other rules in site's
        // robots.txt, even if they don't match any of candidate set.
        // TBD: which user-agent to use in that case.
        return false;
    }
    
    @Override
    public boolean obeyMetaRobotsNofollow() {
        return obeyMetaRobotsNofollow;
    }
}
