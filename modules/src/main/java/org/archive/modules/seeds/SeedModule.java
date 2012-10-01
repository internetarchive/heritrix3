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

package org.archive.modules.seeds;

import java.io.File;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import org.archive.modules.CrawlURI;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class SeedModule implements Serializable
{
    private static final long serialVersionUID = 1L;
    
    /**
     * Whether to tag seeds with their own URI as a heritable 'source' String,
     * which will be carried-forward to all URIs discovered on paths originating
     * from that seed. When present, such source tags appear in the
     * second-to-last crawl.log field.
     */
    protected boolean sourceTagSeeds = false;
    public boolean getSourceTagSeeds() {
        return sourceTagSeeds;
    }
    public void setSourceTagSeeds(boolean sourceTagSeeds) {
        this.sourceTagSeeds = sourceTagSeeds;
    }
    
    protected Set<SeedListener> seedListeners = 
        new HashSet<SeedListener>();
    public Set<SeedListener> getSeedListeners() {
        return seedListeners;
    }
    @Autowired
    public void setSeedListeners(Set<SeedListener> seedListeners) {
        this.seedListeners.addAll(seedListeners);
    }
    
    protected void publishAddedSeed(CrawlURI curi) {
        for (SeedListener l: seedListeners) {
            l.addedSeed(curi);
        }
    }
    protected void publishNonSeedLine(String line) {
        for (SeedListener l: seedListeners) {
            l.nonseedLine(line);
        }
    }
    protected void publishConcludedSeedBatch() {
        for (SeedListener l: seedListeners) {
            l.concludedSeedBatch();
        }
    }

    public SeedModule() {
        super();
    }
    
    public abstract void announceSeeds();
    
    public abstract void actOn(File f); 
    
    public abstract void addSeed(final CrawlURI curi);

    public void addSeedListener(SeedListener sl) {
        seedListeners.add(sl);
    }
}