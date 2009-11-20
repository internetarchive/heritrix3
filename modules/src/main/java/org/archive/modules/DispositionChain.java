package org.archive.modules;

import java.io.IOException;
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

import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.archive.checkpointing.Checkpoint;
import org.archive.checkpointing.Checkpointable;
import org.springframework.beans.factory.annotation.Autowired;

public class DispositionChain extends ProcessorChain implements Checkpointable {
    protected ReentrantReadWriteLock dispositionInProgressLock = 
        new ReentrantReadWriteLock(true);

    
    public void startCheckpoint(Checkpoint checkpointInProgress) {
        dispositionInProgressLock.writeLock().lock();
    }
    
    public void doCheckpoint(Checkpoint checkpointInProgress) throws IOException {
        // do nothing; this class only participates in checkpointing
        // via the startCheckpoint/finishCheckpoint locking
    }

    public void finishCheckpoint(Checkpoint checkpointInProgress) {
        dispositionInProgressLock.writeLock().unlock();
    }

    @Autowired(required=false)
    public void setRecoveryCheckpoint(Checkpoint checkpoint) {
        // do nothing
    }
    
    @Override
    public void process(CrawlURI curi, ChainStatusReceiver thread) throws InterruptedException {
        dispositionInProgressLock.readLock().lock();
        super.process(curi, thread);
        dispositionInProgressLock.readLock().unlock();
    }

    

}
