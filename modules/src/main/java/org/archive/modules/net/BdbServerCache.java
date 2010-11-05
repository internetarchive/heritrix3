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

import org.archive.bdb.BdbModule;
import org.archive.modules.fetcher.DefaultServerCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.Lifecycle;

import com.sleepycat.je.DatabaseException;

/**
 * ServerCache backed by BDB big maps; the usual choice for crawls.
 * 
 * @contributor pjack
 * @contributor gojomo
 */
public class BdbServerCache extends DefaultServerCache 
implements Lifecycle {

    private static final long serialVersionUID = 1L;

    protected BdbModule bdb;
    @Autowired
    public void setBdbModule(BdbModule bdb) {
        this.bdb = bdb;
    }
    
    public BdbServerCache() {
    }
    
    public void start() {
        if(isRunning()) {
            return;
        }
        try {
            this.servers = bdb.getObjectCache("servers", false, CrawlServer.class, CrawlServer.class);
            this.hosts = bdb.getObjectCache("hosts", false, CrawlHost.class, CrawlHost.class);
        } catch (DatabaseException e) {
            throw new IllegalStateException(e);
        }
        isRunning = true;
    }

    boolean isRunning = false; 
    public boolean isRunning() {
        return isRunning;
    }

    public void stop() {
        isRunning = false; 
        // TODO: release bigmaps? 
    }
    
    
    
}
