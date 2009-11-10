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
package org.archive.crawler.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.crawler.datamodel.UriUniqFilter;
import org.archive.modules.CrawlURI;

/**
 * UriUniqFilter based on an underlying UriSet (essentially a Set).
 * 
 * @author gojomo
 */
public abstract class SetBasedUriUniqFilter implements UriUniqFilter, Serializable {
    private static Logger LOGGER =
        Logger.getLogger(SetBasedUriUniqFilter.class.getName());

    protected CrawlUriReceiver receiver;
    protected PrintWriter profileLog;
    protected long duplicateCount = 0;
    protected long duplicatesAtLastSample = 0;
    
    public SetBasedUriUniqFilter() {
        super();
        String profileLogFile = 
            System.getProperty(SetBasedUriUniqFilter.class.getName()
                + ".profileLogFile");
        if (profileLogFile != null) {
            setProfileLog(new File(profileLogFile));
        }
    }
    
    protected abstract boolean setAdd(CharSequence key);

    protected abstract boolean setRemove(CharSequence key);

    protected abstract long setCount();
    
    public long count() {
        return setCount();
    }

    public long pending() {
        // no items pile up in this implementation
        return 0;
    }

    public void setDestination(CrawlUriReceiver receiver) {
        this.receiver = receiver;
    }

    protected void profileLog(String key) {
        if (profileLog != null) {
            profileLog.println(key);
        }
    }
    
    public void add(String key, CrawlURI value) {
        profileLog(key);
        if (setAdd(key)) {
            this.receiver.receive(value);
            if (setCount() % 50000 == 0) {
                LOGGER.log(Level.FINE, "count: " + setCount() + " totalDups: "
                        + duplicateCount + " recentDups: "
                        + (duplicateCount - duplicatesAtLastSample));
                duplicatesAtLastSample = duplicateCount;
            }
        } else {
            duplicateCount++;
        }
    }

    public void addNow(String key, CrawlURI value) {
        add(key, value);
    }
    
    public void addForce(String key, CrawlURI value) {
        profileLog(key);
        setAdd(key);
        this.receiver.receive(value);
    }

    public void note(String key) {
        profileLog(key);
        setAdd(key);
    }

    public void forget(String key, CrawlURI value) {
        setRemove(key);
    }

    public long requestFlush() {
        // unnecessary; all actions with set-based uniqfilter are immediate
        return 0;
    }

    public void close() {
        if (profileLog != null) {
            profileLog.close();
        }
    }

    public void setProfileLog(File logfile) {
        try {
            profileLog = new PrintWriter(new BufferedOutputStream(
                    new FileOutputStream(logfile)));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
