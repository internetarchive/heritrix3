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
package org.archive.modules.fetcher;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

import org.archive.checkpointing.Checkpoint;
import org.springframework.beans.factory.annotation.Autowired;

public class SimpleDomainCookieSetStore extends AbstractDomainCookieSetStore {

    @SuppressWarnings("rawtypes")
    protected Map<String, TreeSet> cookiesByHost;

    @SuppressWarnings("rawtypes")
    protected void prepare() {
        cookiesByHost = new LinkedHashMap<String, TreeSet>();
    }

    @SuppressWarnings("rawtypes")
    @Override
    protected Map<String, TreeSet> getCookiesByDomain() {
        return cookiesByHost;
    }
    
    @Override
    public void startCheckpoint(Checkpoint checkpointInProgress) {
        throw new RuntimeException("not implemented");
    }
    @Override
    public void doCheckpoint(Checkpoint checkpointInProgress)
            throws IOException {
        throw new RuntimeException("not implemented");
    }
    @Override
    public void finishCheckpoint(Checkpoint checkpointInProgress) {
        throw new RuntimeException("not implemented");
    }
    @Override
    @Autowired(required=false)
    public void setRecoveryCheckpoint(Checkpoint recoveryCheckpoint) {
        throw new RuntimeException("not implemented");
    }
}
