/* UriUniqFilterImpl
*
* $Id$
*
* Created on Sep 29, 2005
*
* Copyright (C) 2005 Internet Archive.
*
* This file is part of the Heritrix web crawler (crawler.archive.org).
*
* Heritrix is free software; you can redistribute it and/or modify
* it under the terms of the GNU Lesser Public License as published by
* the Free Software Foundation; either version 2.1 of the License, or
* any later version.
*
* Heritrix is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU Lesser Public License for more details.
*
* You should have received a copy of the GNU Lesser Public License
* along with Heritrix; if not, write to the Free Software
* Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
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
