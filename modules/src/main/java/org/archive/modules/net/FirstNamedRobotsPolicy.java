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
 * Working from an ordered list of potential User-Agents, consisting of first 
 * the regularly-configured User-Agent and then those in the candidateUserAgents
 * list, consider each potential agent in order. As soon as a matching 
 * (not '*' wildcard) set of directives is found, follow those. If none are 
 * followed, use the wildcard directives (if any). 
 * 
 * For example, is the usual User-Agent is 'A', and the candidateUserAgents
 * are 'B' and 'C', any rules applying to 'A' will be followed if found. 
 * If not, rules applying to 'B' will be followed if found. If not, rules
 * applying to 'C' will be followed if found. If not, wildcard User-Agent
 * rules will be followed if found. 
 * 
 * Offers the option of adjusting the outgoing request to declare the 
 * User-Agent whose rules are being followed. (This option is the default 
 * and recommended.) 
 * 
 * (With an empty candidateUserAgents list, should behave same as the 
 * ObeyRobotsPolicy, but offers the a setting for obeyMetaRobotsNofollow.)
 * 
 * @contributor gojomo
 */
public class FirstNamedRobotsPolicy extends RobotsPolicy {
    
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
        RobotsDirectives directives = robotstxt.getDirectivesFor(userAgent, false);
        if(directives!=null) {
            return directives.allows(getPathQuery(curi));
        }
        
        for(String candidate : candidateUserAgents) {
            directives = robotstxt.getDirectivesFor(candidate, false);
            if(directives!=null) {
                if(shouldMasquerade) {
                    curi.setUserAgent(candidate);
                }
                return directives.allows(getPathQuery(curi));
            }
        }
        return robotstxt.getDirectivesFor(userAgent).allows(getPathQuery(curi));
    }
    
    @Override
    public boolean obeyMetaRobotsNofollow() {
        return obeyMetaRobotsNofollow;
    }
}
