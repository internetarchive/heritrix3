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

import java.io.Serializable;

import org.archive.util.PrefixSet;

/**
 * Represents the directives that apply to a user-agent (or set of
 * user-agents)
 */
public class RobotsDirectives implements Serializable {
    private static final long serialVersionUID = 5386542759286155383L;
    
    PrefixSet disallows = new PrefixSet();
    PrefixSet allows = new PrefixSet();
    float crawlDelay = -1; 

    public boolean allows(String path) {
        if(disallows.containsPrefixOf(path)) {
            return allows.containsPrefixOf(path);
        }
        return true;
    }

    public void addDisallow(String path) {
        if(path.length()==0) {
            // ignore empty-string disallows 
            // (they really mean allow, when alone)
            return;
        }
        disallows.add(path);
    }

    public void addAllow(String path) {
        allows.add(path);
    }

    public void setCrawlDelay(float i) {
        crawlDelay=i;
    }

    public float getCrawlDelay() {
        return crawlDelay;
    }
}