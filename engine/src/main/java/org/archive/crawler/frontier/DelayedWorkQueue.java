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
package org.archive.crawler.frontier;

import java.io.Serializable;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * A named WorkQueue wrapped with a wake time, perhaps referenced only
 * by name. 
 * 
 * @contributor gojomo
 */
class DelayedWorkQueue implements Delayed, Serializable {
    private static final long serialVersionUID = 1L;

    public String classKey;
    public long wakeTime;
    
    /**
     * Reference to the WorkQueue, perhaps saving a deserialization
     * from allQueues.
     */
    protected transient WorkQueue workQueue;
    
    public DelayedWorkQueue(WorkQueue queue) {
        this.classKey = queue.getClassKey();
        this.wakeTime = queue.getWakeTime();
        this.workQueue = queue;
    }
    
    // TODO: consider if this should be method on WorkQueueFrontier
    public WorkQueue getWorkQueue(WorkQueueFrontier wqf) {
        if (workQueue == null) {
            // This is a recently deserialized DelayedWorkQueue instance
            WorkQueue result = wqf.getQueueFor(classKey);
            this.workQueue = result;
        }
        return workQueue;
    }

    public long getDelay(TimeUnit unit) {
        return unit.convert(
                wakeTime - System.currentTimeMillis(),
                TimeUnit.MILLISECONDS);
    }
    
    public String getClassKey() {
        return classKey;
    }
    
    public long getWakeTime() {
        return wakeTime;
    }
    
    public void setWakeTime(long time) {
        this.wakeTime = time;
    }
    
    public int compareTo(Delayed obj) {
        if (this == obj) {
            return 0; // for exact identity only
        }
        DelayedWorkQueue other = (DelayedWorkQueue) obj;
        if (wakeTime > other.getWakeTime()) {
            return 1;
        }
        if (wakeTime < other.getWakeTime()) {
            return -1;
        }
        // at this point, the ordering is arbitrary, but still
        // must be consistent/stable over time
        return this.classKey.compareTo(other.getClassKey());        
    }
    
}