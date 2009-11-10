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

import it.unimi.dsi.fastutil.longs.LongIterator;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.crawler.datamodel.UriUniqFilter;
import org.archive.modules.CrawlURI;
import org.archive.util.fingerprint.ArrayLongFPCache;

import st.ata.util.FPGenerator;

/**
 * UriUniqFilter based on merging FP arrays (in memory or from disk). 
 * 
 * Inspired by the approach in Najork and Heydon, "High-Performance
 * Web Crawling" (2001), section 3.2, "Efficient Duplicate URL 
 * Eliminators". 
 * 
 * @author gojomo
 */
public abstract class FPMergeUriUniqFilter implements UriUniqFilter {
    /**
     * Represents a long fingerprint and (possibly) its corresponding
     * CrawlURI, awaiting the next merge in a 'pending' state. 
     */
    public class PendingItem implements Comparable<PendingItem> {
        long fp;
        CrawlURI caUri;
        public PendingItem(long fp, CrawlURI value) {
            this.fp = fp;
            this.caUri = value;
        }
        public int compareTo(PendingItem vs) {
            return (fp < vs.fp) ? -1 : ( (fp == vs.fp) ? 0 : 1); 
        }
    }
    
    private static Logger LOGGER =
        Logger.getLogger(FPMergeUriUniqFilter.class.getName());

    protected CrawlUriReceiver receiver;
    protected PrintWriter profileLog;
    
    // statistics
    protected long quickDuplicateCount = 0;
    protected long quickDupAtLast = 0; 
    protected long pendDuplicateCount = 0;
    protected long pendDupAtLast = 0; 
    protected long mergeDuplicateCount = 0;
    protected long mergeDupAtLast = 0; 
    
    /** items awaiting merge
     * TODO: consider only sorting just pre-merge
     * TODO: consider using a fastutil long->Object class
     * TODO: consider actually writing items to disk file,
     * as in Najork/Heydon
     */
    protected TreeSet<PendingItem> pendingSet = new TreeSet<PendingItem>();
    
    /** size at which to force flush of pending items */
    protected int maxPending = DEFAULT_MAX_PENDING;
    public static final int DEFAULT_MAX_PENDING = 10000; 
    // TODO: increase
    
    /**
     * time-based throttle on flush-merge operations
     */
    protected long nextFlushAllowableAfter = 0;
    public static final long FLUSH_DELAY_FACTOR = 100;

    /** cache of most recently seen FPs */
    protected ArrayLongFPCache quickCache = new ArrayLongFPCache();
    // TODO: make cache most-often seen, not just most-recent
    
    public FPMergeUriUniqFilter() {
        super();
        String profileLogFile = 
            System.getProperty(FPMergeUriUniqFilter.class.getName()
                + ".profileLogFile");
        if (profileLogFile != null) {
            setProfileLog(new File(profileLogFile));
        }
    }

    public void setMaxPending(int max) {
        maxPending = max;
    }
    
    public long pending() {
        return pendingSet.size();
    }

    public void setDestination(CrawlUriReceiver receiver) {
        this.receiver = receiver;
    }

    protected void profileLog(String key) {
        if (profileLog != null) {
            profileLog.println(key);
        }
    }
    
    /* (non-Javadoc)
     * @see org.archive.crawler.datamodel.UriUniqFilter#add(java.lang.String, org.archive.crawler.datamodel.CrawlURI)
     */
    public synchronized void add(String key, CrawlURI value) {
        profileLog(key);
        long fp = createFp(key); 
        if(! quickCheck(fp)) {
            quickDuplicateCount++;
            return; 
        }
        pend(fp,value);
        if (pendingSet.size()>=maxPending) {
            flush();
        }
    }

    /**
     * Place the given FP/CrawlURI pair into the pending set, awaiting
     * a merge to determine if it's actually accepted. 
     * 
     * @param fp long fingerprint
     * @param value CrawlURI or null, if fp only needs merging (as when 
     * CrawlURI was already forced in
     */
    protected void pend(long fp, CrawlURI value) {
        // special case for first batch of adds
        if(count()==0) {
            if(pendingSet.add(new PendingItem(fp,null))==false) {
                pendDuplicateCount++; // was already present
            } else {
                // since there's no prior list to merge, push uri along right now
                if(value!=null) {
                    this.receiver.receive(value);
                }
            }
            return;
        }
        if(pendingSet.add(new PendingItem(fp,value))==false) {
            pendDuplicateCount++; // was already present
        }
    }

    /**
     * Evaluate if quick-check cache considers fingerprint novel enough
     * for further consideration. 
     * 
     * @param fp long fingerprint to check
     * @return true if fp deserves consideration; false if it appears in cache
     */
    private boolean quickCheck(long fp) {
        return quickCache.add(fp);
    }

    /**
     * Create a fingerprint from the given key
     * 
     * @param key CharSequence (URI) to fingerprint
     * @return long fingerprint
     */
    public static long createFp(CharSequence key) {
        return FPGenerator.std64.fp(key);
    }


    /* (non-Javadoc)
     * @see org.archive.crawler.datamodel.UriUniqFilter#addNow(java.lang.String, org.archive.crawler.datamodel.CrawlURI)
     */
    public void addNow(String key, CrawlURI value) {
        add(key, value);
        flush();
    }
    
    /* (non-Javadoc)
     * @see org.archive.crawler.datamodel.UriUniqFilter#addForce(java.lang.String, org.archive.crawler.datamodel.CrawlURI)
     */
    public void addForce(String key, CrawlURI value) {
        add(key,null); // dummy pend
        this.receiver.receive(value);
    }

    /* (non-Javadoc)
     * @see org.archive.crawler.datamodel.UriUniqFilter#note(java.lang.String)
     */
    public void note(String key) {
        add(key,null);
    }

    /* (non-Javadoc)
     * @see org.archive.crawler.datamodel.UriUniqFilter#forget(java.lang.String, org.archive.crawler.datamodel.CrawlURI)
     */
    public void forget(String key, CrawlURI value) {
        throw new UnsupportedOperationException();
    }

    /* (non-Javadoc)
     * @see org.archive.crawler.datamodel.UriUniqFilter#requestFlush()
     */
    public synchronized long requestFlush() {
        if(System.currentTimeMillis()>nextFlushAllowableAfter) {
            return flush();
        } else {
//            LOGGER.info("declining to flush: too soon after last flush");
            return -1; 
        }
    }

    /**
     * Perform a merge of all 'pending' items to the overall fingerprint list. 
     * If the pending item is new, and has an associated CrawlURI, pass that
     * URI along to the 'receiver' (frontier) for queueing. 
     * 
     * @return number of pending items actually added 
     */
    public synchronized long flush() {
        if(pending()==0) {
            return 0;
        }
        long flushStartTime = System.currentTimeMillis();
        long adds = 0; 
        long fpOnlyAdds = 0;
        Long currFp = null; 
        PendingItem currPend = null; 
        
        Iterator<PendingItem> pendIter = pendingSet.iterator();
        LongIterator fpIter = beginFpMerge();

        currPend = (PendingItem) (pendIter.hasNext() ? pendIter.next() : null);
        currFp = (Long) (fpIter.hasNext() ? fpIter.next() : null); 

        while(true) {
            while(currFp!=null && (currPend==null||(currFp.longValue() <= currPend.fp))) {
                addNewFp(currFp.longValue());
                if(currPend!=null && currFp.longValue() == currPend.fp) {
                    mergeDuplicateCount++;
                }
                if(fpIter.hasNext()) {
                    currFp = (Long) fpIter.next();
                } else {
                    currFp = null;
                    break;
                }
            }
            while(currPend!=null && (currFp==null||(currFp.longValue() > currPend.fp))) {
                addNewFp(currPend.fp);
                if(currPend.caUri!=null) {
                    adds++;
                    this.receiver.receive(currPend.caUri);
                } else {
                    fpOnlyAdds++;
                }
                if(pendIter.hasNext()) {
                    currPend = (PendingItem)pendIter.next();
                } else {
                    currPend = null;
                    break;
                }
            }
            if(currFp==null) {
                // currPend must be null too, or while wouldn't have exitted
                // done
                break;
            } 
        }
        // maintain throttle timing
        long flushDuration = System.currentTimeMillis() - flushStartTime;
        nextFlushAllowableAfter = flushStartTime + (FLUSH_DELAY_FACTOR*flushDuration);
        
        // add/duplicate statistics
        if(LOGGER.isLoggable(Level.INFO)) {
            long mergeDups = (mergeDuplicateCount-mergeDupAtLast);
            long pendDups = (pendDuplicateCount-pendDupAtLast);
            long quickDups = (quickDuplicateCount-quickDupAtLast);
            LOGGER.info("flush took "+flushDuration+"ms: "
                    +adds+" adds, "
                    +fpOnlyAdds+" fpOnlydds, "
                    +mergeDups+" mergeDups, "
                    +pendDups+" pendDups, "
                    +quickDups+" quickDups ");
            if(adds==0 && fpOnlyAdds==0 && mergeDups == 0 && pendDups == 0 && quickDups == 0) {
                LOGGER.info("that's odd");
            }
        }
        mergeDupAtLast = mergeDuplicateCount;
        pendDupAtLast = pendDuplicateCount;
        quickDupAtLast = quickDuplicateCount;
        pendingSet.clear();
        finishFpMerge();
        return adds;
    }
    
    /**
     * Begin merging pending candidates with complete list. Return an
     * Iterator which will return all previously-known FPs in turn. 
     * 
     * @return Iterator over all previously-known FPs
     */
    abstract protected LongIterator beginFpMerge();

    
    /**
     * Add an FP (which may be an old or new FP) to the new complete
     * list. Should only be called after beginFpMerge() and before
     * finishFpMerge(). 
     * 
     * @param fp  the FP to add
     */
    abstract protected void addNewFp(long fp);

    /**
     * Complete the merge of candidate and previously-known FPs (closing
     * files/iterators as appropriate). 
     */
    abstract protected void finishFpMerge();

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
