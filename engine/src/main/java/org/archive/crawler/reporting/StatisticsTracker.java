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

package org.archive.crawler.reporting;

import static org.archive.crawler.datamodel.CoreAttributeConstants.A_SOURCE_TAG;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.bdb.BdbModule;
import org.archive.crawler.datamodel.CrawlURI;
import org.archive.crawler.event.CrawlStateEvent;
import org.archive.crawler.event.CrawlURIDispositionEvent;
import org.archive.crawler.event.StatSnapshotEvent;
import org.archive.crawler.framework.CrawlController;
import org.archive.crawler.framework.Engine;
import org.archive.crawler.util.CrawledBytesHistotable;
import org.archive.crawler.util.TopNSet;
import org.archive.modules.net.ServerCache;
import org.archive.modules.net.ServerCacheUtil;
import org.archive.modules.seeds.SeedModule;
import org.archive.net.UURI;
import org.archive.spring.ConfigPath;
import org.archive.util.ArchiveUtils;
import org.archive.util.MimetypeUtils;
import org.archive.util.PaddingStringBuffer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.Lifecycle;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Lookup;

import com.sleepycat.je.DatabaseException;

/**
 * This is an implementation of the AbstractTracker. It is designed to function
 * with the WUI as well as performing various logging activity.
 * <p>
 * At the end of each snapshot a line is written to the
 * 'progress-statistics.log' file.
 * <p>
 * The header of that file is as follows:
 * <pre> [timestamp] [discovered]    [queued] [downloaded] [doc/s(avg)]  [KB/s(avg)] [dl-failures] [busy-thread] [mem-use-KB]</pre>
 * First there is a <b>timestamp</b>, accurate down to 1 second.
 * <p>
 * <b>discovered</b>, <b>queued</b>, <b>downloaded</b> and <b>dl-failures</b>
 * are (respectively) the discovered URI count, pending URI count, successfully
 * fetched count and failed fetch count from the frontier at the time of the
 * snapshot.
 * <p>
 * <b>KB/s(avg)</b> is the bandwidth usage.  We use the total bytes downloaded
 * to calculate average bandwidth usage (KB/sec). Since we also note the value
 * each time a snapshot is made we can calculate the average bandwidth usage
 * during the last snapshot period to gain a "current" rate. The first number is
 * the current and the average is in parenthesis.
 * <p>
 * <b>doc/s(avg)</b> works the same way as doc/s except it show the number of
 * documents (URIs) rather then KB downloaded.
 * <p>
 * <b>busy-threads</b> is the total number of ToeThreads that are not available
 * (and thus presumably busy processing a URI). This information is extracted
 * from the crawl controller.
 * <p>
 * Finally mem-use-KB is extracted from the run time environment
 * (<code>Runtime.getRuntime().totalMemory()</code>).
 * <p>
 * In addition to the data collected for the above logs, various other data
 * is gathered and stored by this tracker.
 * <ul>
 *   <li> Successfully downloaded documents per fetch status code
 *   <li> Successfully downloaded documents per document mime type
 *   <li> Amount of data per mime type
 *   <li> Successfully downloaded documents per host
 *   <li> Amount of data per host
 *   <li> Disposition of all seeds (this is written to 'reports.log' at end of
 *        crawl)
 *   <li> Successfully downloaded documents per host per source
 * </ul>
 *
 * @contributor Parker Thompson
 * @contributor Kristinn Sigurdsson
 * @contributor gojomo
 */
public class StatisticsTracker 
    implements 
        ApplicationContextAware, 
        ApplicationListener,
        Lifecycle, 
        Runnable, 
        Serializable {
    private static final long serialVersionUID = 5L;

    protected SeedModule seeds;
    public SeedModule getSeeds() {
        return this.seeds;
    }
    @Autowired
    public void setSeeds(SeedModule seeds) {
        this.seeds = seeds;
    }

    protected BdbModule bdb;
    @Autowired
    public void setBdbModule(BdbModule bdb) {
        this.bdb = bdb;
    }

    protected ConfigPath reportsDir = new ConfigPath(Engine.REPORTS_DIR_NAME,"reports");
    public ConfigPath getReportsDir() {
        return reportsDir;
    }
    public void setReportsDir(ConfigPath reportsDir) {
        this.reportsDir = reportsDir;
    }
    
    protected ServerCache serverCache;
    public ServerCache getServerCache() {
        return this.serverCache;
    }
    @Autowired
    public void setServerCache(ServerCache serverCache) {
        this.serverCache = serverCache;
    }
    
    protected int liveHostReportSize = 20;
    public int getLiveHostReportSize() {
        return liveHostReportSize;
    }
    public void setLiveHostReportSize(int liveHostReportSize) {
        this.liveHostReportSize = liveHostReportSize;
    }
    
    ApplicationContext appCtx;
    public void setApplicationContext(ApplicationContext appCtx) throws BeansException {
        this.appCtx = appCtx;
    }
    
    /**
     * Messages from the StatisticsTracker.
     */
    private final static Logger logger =
        Logger.getLogger(StatisticsTracker.class.getName());

    /**
     * All report types, for selection by name
     */
    @SuppressWarnings("unchecked")
    public static final Class[] ALL_REPORTS = {
        CrawlSummaryReport.class,
        SeedsReport.class, 
        HostsReport.class, 
        SourceTagsReport.class,
        MimetypesReport.class, 
        ResponseCodeReport.class, 
        ProcessorsReport.class,
        FrontierSummaryReport.class, 
        FrontierNonemptyReport.class, 
        ToeThreadsReport.class,
    };
    
    /**
     * Live report types, for iteration dump at end
     */
    @SuppressWarnings("unchecked")
    public static final Class[] LIVE_REPORTS = {
        CrawlSummaryReport.class,
        SeedsReport.class, 
        HostsReport.class, 
        SourceTagsReport.class,
        MimetypesReport.class, 
        ResponseCodeReport.class, 
        ProcessorsReport.class,
        FrontierSummaryReport.class, 
        ToeThreadsReport.class,
    };
    /**
     * End-of-crawl report types, for iteration dump at end
     */
    @SuppressWarnings("unchecked")
    public static final Class[] END_REPORTS = {
        CrawlSummaryReport.class,
        SeedsReport.class, 
        HostsReport.class, 
        SourceTagsReport.class,
        MimetypesReport.class, 
        ResponseCodeReport.class, 
        ProcessorsReport.class,
        FrontierSummaryReport.class, 
        ToeThreadsReport.class,
    };
    
    /**
     * The interval between writing progress information to log.
     */
    int intervalSeconds = 20;
    public int getIntervalSeconds() {
        return this.intervalSeconds;
    }
    public void setIntervalSeconds(int interval) {
        this.intervalSeconds = interval;
    }
    
    /**
     * Number of crawl-stat sample snapshots to keep for calculation 
     * purposes.
     */
    int keepSnapshotsCount = 5;
    public int getKeepSnapshotsCount() {
        return this.keepSnapshotsCount;
    }
    public void setKeepSnapshotsCount(int count) {
        this.keepSnapshotsCount = count;
    }
    
    protected CrawlController controller;
    public CrawlController getCrawlController() {
        return this.controller;
    }
    @Autowired
    public void setCrawlController(CrawlController controller) {
        this.controller = controller;
    }

    /** wall-clock time the crawl started */
    long crawlStartTime;
    /** wall-clock time the crawl ended */
    long crawlEndTime = -1; // Until crawl ends, this value is -1.
    /** wall-clock time of last pause, while pause in progres */ 
    long crawlPauseStarted = 0;
    /** duration tally of all time spent in paused state */ 
    long crawlTotalPausedTime = 0;

    /** snapshots of crawl tallies and rates */
    LinkedList<CrawlStatSnapshot> snapshots = new LinkedList<CrawlStatSnapshot>();
    
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    
    /*
     * Cumulative data
     */
    /** tally sizes novel, verified (same hash), vouched (not-modified) */ 
    CrawledBytesHistotable crawledBytes = new CrawledBytesHistotable();
    
    // TODO: fortify these against key explosion with bigmaps like other tallies
    /** Keep track of the file types we see (mime type -> count) */
    protected ConcurrentMap<String,AtomicLong> mimeTypeDistribution
     = new ConcurrentHashMap<String,AtomicLong>();
    protected ConcurrentMap<String,AtomicLong> mimeTypeBytes
     = new ConcurrentHashMap<String,AtomicLong>();
    
    /** Keep track of fetch status codes */
    protected ConcurrentMap<String,AtomicLong> statusCodeDistribution
     = new ConcurrentHashMap<String,AtomicLong>();
    
    /** Keep track of hosts. 
     * 
     */
    protected ConcurrentMap<String,AtomicLong> hostsDistribution = 
        new ConcurrentHashMap<String, AtomicLong>(); // temp dummy
    protected ConcurrentMap<String,AtomicLong> hostsBytes = 
        new ConcurrentHashMap<String, AtomicLong>(); // temp dummy
    protected ConcurrentMap<String,AtomicLong> hostsLastFinished = 
        new ConcurrentHashMap<String, AtomicLong>(); // temp dummy

    /** Keep track of URL counts per host per seed */
    protected  
    ConcurrentMap<String,ConcurrentMap<String,AtomicLong>> sourceHostDistribution = 
        new ConcurrentHashMap<String, ConcurrentMap<String,AtomicLong>>(); // temp dummy;

    /* Keep track of 'top' hosts for live reports */
    protected TopNSet hostsDistributionTop;
    protected TopNSet hostsBytesTop;
    protected TopNSet hostsLastFinishedTop;
    
    /**
     * Record of seeds' latest actions.
     */
    protected ConcurrentMap<String,SeedRecord> processedSeedsRecords = 
        new ConcurrentHashMap<String, SeedRecord>();
    long seedsTotal = -1; 
    long seedsCrawled = -1;
    
    public StatisticsTracker() {
        
    }
    
    boolean isRunning = false; 
    public boolean isRunning() {
        return isRunning;
    }
    public void stop() {
        isRunning = false;
        executor.shutdownNow();
        dumpReports();
    }
    
    public void start() {
        isRunning = true;
        try {
            this.sourceHostDistribution = bdb.getBigMap("sourceHostDistribution",
            	    false, String.class, ConcurrentMap.class);
            this.hostsDistribution = bdb.getBigMap("hostsDistribution",
                false, String.class, AtomicLong.class);
            this.hostsBytes = bdb.getBigMap("hostsBytes", false, String.class,
                AtomicLong.class);
            this.hostsLastFinished = bdb.getBigMap("hostsLastFinished",
                false, String.class, AtomicLong.class);
            this.processedSeedsRecords = bdb.getBigMap("processedSeedsRecords",
                    false, String.class, SeedRecord.class);
            
            this.hostsDistributionTop = new TopNSet(getLiveHostReportSize());
            this.hostsBytesTop = new TopNSet(getLiveHostReportSize());
            this.hostsLastFinishedTop = new TopNSet(getLiveHostReportSize());
        } catch (DatabaseException e) {
            throw new IllegalStateException(e);
        }
        // Log the legend
        this.controller.logProgressStatistics(progressStatisticsLegend());
        executor.scheduleAtFixedRate(this, 0, getIntervalSeconds(), TimeUnit.SECONDS);
    }

    /**
     * Start thread.  Will call logActivity() at intervals specified by
     * logInterval
     *
     */
    public void run() {
        progressStatisticsEvent();
    }

    /**
     * @return legend for progress-statistics lines/log
     */
    public String progressStatisticsLegend() {
        return "           timestamp" +
            "  discovered   " +
            "   queued   downloaded       doc/s(avg)  KB/s(avg) " +
            "  dl-failures   busy-thread   mem-use-KB  heap-size-KB " +
            "  congestion   max-depth   avg-depth";
    }

    /**
     * Notify tracker that crawl has begun. Must be called
     * outside tracker's own thread, to ensure it is noted
     * before other threads start interacting with tracker. 
     */
    public void noteStart() {
        if (this.crawlStartTime == 0) {
            // Note the time the crawl starts (only if not already set)
            this.crawlStartTime = System.currentTimeMillis();
        }
    }

    /**
     * A method for logging current crawler state.
     *
     * This method will be called by run() at intervals specified in
     * the crawl order file.  It is also invoked when pausing or
     * stopping a crawl to capture the state at that point.  Default behavior is
     * call to {@link CrawlController#logProgressStatistics} so CrawlController
     * can act on progress statistics event.
     * <p>
     * It is recommended that for implementations of this method it be
     * carefully considered if it should be synchronized in whole or in
     * part
     * @param e Progress statistics event.
     */
    protected synchronized void progressStatisticsEvent() {
        CrawlStatSnapshot snapshot = getSnapshot();
       
        if (this.controller != null) {
            this.controller.logProgressStatistics(snapshot.getProgressStatisticsLine());
        }
        snapshots.addFirst(snapshot);
        while(snapshots.size()>getKeepSnapshotsCount()) {
            snapshots.removeLast();
        }
        
        // publish app event 
        appCtx.publishEvent(new StatSnapshotEvent(this,snapshot));
        
        // temporary workaround for 
        // [ 996161 ] Fix DNSJava issues (memory) -- replace with JNDI-DNS?
        // http://sourceforge.net/support/tracker.php?aid=996161
        Lookup.getDefaultCache(DClass.IN).clearCache();
    }
    
    public CrawlStatSnapshot getSnapshot() {
        // TODO: take snapshot implementation from a spring prototype?
        CrawlStatSnapshot snapshot = new CrawlStatSnapshot();
        snapshot.collect(controller,this); 
        return snapshot;
    }
    
    public CrawlStatSnapshot getLastSnapshot() {
        CrawlStatSnapshot snap = snapshots.peek();
        return snap == null ? getSnapshot() : snap;
    }

    public long getCrawlElapsedTime() {
        if (crawlStartTime == 0) {
            // if no start time set yet, consider elapsed time zero
            return 0;
        }
        if (crawlPauseStarted != 0) {
            // currently paused, calculate time up to last pause
            return crawlPauseStarted - crawlTotalPausedTime - crawlStartTime;
        }
        
        // not paused, calculate total time to end or (if running) now
        return ((crawlEndTime>0)?crawlEndTime:System.currentTimeMillis()) 
            - crawlTotalPausedTime - crawlStartTime;
    }

    public void crawlPausing(String statusMessage) {
        logNote("CRAWL WAITING - " + statusMessage);
    }

    protected void logNote(final String note) {
        this.controller.logProgressStatistics(new PaddingStringBuffer()
                     .append(ArchiveUtils.getLog14Date(new Date()))
                     .append(" ")
                     .append(note)
                     .toString());
    }

    public void crawlPaused(String statusMessage) {
        crawlPauseStarted = System.currentTimeMillis();
        progressStatisticsEvent();
        logNote("CRAWL PAUSED - " + statusMessage);
    }

    public void crawlResuming(String statusMessage) {
        tallyCurrentPause();
        if (this.crawlStartTime == 0) {
            noteStart();
        }
        logNote("CRAWL RESUMED - " + statusMessage);
    }

    /**
     * For a current pause (if any), add paused time to total and reset
     */
    protected void tallyCurrentPause() {
        if (this.crawlPauseStarted > 0) {
            // Ok, we managed to actually pause before resuming.
            this.crawlTotalPausedTime
                += (System.currentTimeMillis() - this.crawlPauseStarted);
        }
        this.crawlPauseStarted = 0;
    }

    public void crawlEnding(String sExitMessage) {
        logNote("CRAWL ENDING - " + sExitMessage);
    }

    public void crawlEnded(String sExitMessage) {
        // Note the time when the crawl stops.
        crawlEndTime = System.currentTimeMillis();
        progressStatisticsEvent();
        logNote("CRAWL ENDED - " + sExitMessage);
    }

    /**
     * Returns how long the current crawl has been running *including*
     * time paused (contrast with getCrawlElapsedTime()).
     *
     * @return The length of time - in msec - that this crawl has been running.
     */
    public long getCrawlDuration() {
        return ((crawlEndTime>0)?crawlEndTime:System.currentTimeMillis()) 
             - crawlStartTime;
    }

    /** Returns a HashMap that contains information about distributions of
     *  encountered mime types.  Key/value pairs represent
     *  mime type -> count.
     * <p>
     * <b>Note:</b> All the values are wrapped with a {@link AtomicLong AtomicLong}
     * @return mimeTypeDistribution
     */
    public ConcurrentMap<String,AtomicLong> getFileDistribution() {
        return mimeTypeDistribution;
    }


    /**
     * Increment a counter for a key in a given HashMap. Used for various
     * aggregate data.
     * 
     * @param map The Map or ConcurrentMap
     * @param key The key for the counter to be incremented, if it does not
     *               exist it will be added (set to 1).  If null it will
     *            increment the counter "unknown".
     */
    protected static void incrementMapCount(ConcurrentMap<String,AtomicLong> map, 
            String key) {
    	incrementMapCount(map,key,1);
    }

    /**
     * Increment a counter for a key in a given HashMap by an arbitrary amount.
     * Used for various aggregate data. The increment amount can be negative.
     *
     *
     * @param map
     *            The HashMap
     * @param key
     *            The key for the counter to be incremented, if it does not exist
     *            it will be added (set to equal to <code>increment</code>).
     *            If null it will increment the counter "unknown".
     * @param increment
     *            The amount to increment counter related to the <code>key</code>.
     */
    protected static void incrementMapCount(ConcurrentMap<String,AtomicLong> map, 
            String key, long increment) {
        if (key == null) {
            key = "unknown";
        }
        AtomicLong lw = (AtomicLong)map.get(key);
        if(lw == null) {
            lw = new AtomicLong();
            AtomicLong prevVal = map.putIfAbsent(key, lw);
            if(prevVal != null) {
                lw = prevVal;
            }
        } 
        lw.addAndGet(increment);
    }

    /**
     * Sort the entries of the given HashMap in descending order by their
     * values, which must be longs wrapped with <code>AtomicLong</code>.
     * <p>
     * Elements are sorted by value from largest to smallest. Equal values are
     * sorted in an arbitrary, but consistent manner by their keys. Only items
     * with identical value and key are considered equal.
     *
     * If the passed-in map requires access to be synchronized, the caller
     * should ensure this synchronization. 
     * 
     * @param mapOfAtomicLongValues
     *            Assumes values are wrapped with AtomicLong.
     * @return a sorted set containing the same elements as the map.
     */
    public TreeMap<String,AtomicLong> getReverseSortedCopy(
            final Map<String,AtomicLong> mapOfAtomicLongValues) {
        TreeMap<String,AtomicLong> sortedMap = 
          new TreeMap<String,AtomicLong>(new Comparator<String>() {
            public int compare(String e1, String e2) {
                long firstVal = mapOfAtomicLongValues.get(e1).get();
                long secondVal = mapOfAtomicLongValues.get(e2).get();
                if (firstVal < secondVal) {
                    return 1;
                }
                if (secondVal < firstVal) {
                    return -1;
                }
                // If the values are the same, sort by keys.
                return e1.compareTo(e2);
            }
        });
        try {
            sortedMap.putAll(mapOfAtomicLongValues);
        } catch (UnsupportedOperationException e) {
            Iterator<String> i = mapOfAtomicLongValues.keySet().iterator();
            for (;i.hasNext();) {
                // Ok. Try doing it the slow way then.
                String key = i.next();
                sortedMap.put(key, mapOfAtomicLongValues.get(key));
            }
        }
        return sortedMap;
    }

    /**
     * Return a HashMap representing the distribution of status codes for
     * successfully fetched curis, as represented by a hashmap where key -&gt;
     * val represents (string)code -&gt; (integer)count.
     * 
     * <b>Note: </b> All the values are wrapped with a
     * {@link AtomicLong AtomicLong}
     * 
     * @return statusCodeDistribution
     */
    public ConcurrentMap<String,AtomicLong> getStatusCodeDistribution() {
        return statusCodeDistribution;
    }
    
    /**
     * Returns the time (in millisec) when a URI belonging to a given host was
     * last finished processing. 
     * 
     * @param host The host to look up time of last completed URI.
     * @return Returns the time (in millisec) when a URI belonging to a given 
     * host was last finished processing. If no URI has been completed for host
     * -1 will be returned. 
     */
    public AtomicLong getHostLastFinished(String host){
        AtomicLong fini = hostsLastFinished.get(host);
        if(fini==null) {
            String hkey = new String(host); // ensure private minimal key
            fini = new AtomicLong(-1);
            AtomicLong prevVal = hostsLastFinished.putIfAbsent(hkey, fini);
            if(prevVal!=null) {
                fini = prevVal;
            }
        }
        return fini;
    }

    /**
     * Returns the accumulated number of bytes downloaded from a given host.
     * @param host name of the host
     * @return the accumulated number of bytes downloaded from a given host
     */
    public long getBytesPerHost(String host){
        return getReportValue(hostsBytes, host);
    }

    /**
     * Returns the accumulated number of bytes from files of a given file type.
     * @param filetype Filetype to check.
     * @return the accumulated number of bytes from files of a given mime type
     */
    public long getBytesPerFileType(String filetype){
        return getReportValue(mimeTypeBytes, filetype);
    }

    /**
     * Get the total number of ToeThreads (sleeping and active)
     *
     * @return The total number of ToeThreads
     */
    public int threadCount() {
        return this.controller != null? controller.getToeCount(): 0;
    }
            
    public String crawledBytesSummary() {
        return crawledBytes.summary();
    }
    
    /**
     * If the curi is a seed, we update the processedSeeds table.
     *
     * @param curi The CrawlURI that may be a seed.
     * @param disposition The disposition of the CrawlURI.
     */
    private void handleSeed(CrawlURI curi, String disposition) {
        if(curi.isSeed()){
            SeedRecord sr = processedSeedsRecords.get(curi.getURI());
            if(sr==null) {
                sr = new SeedRecord(curi, disposition);
                SeedRecord prevVal = processedSeedsRecords.putIfAbsent(sr.getUri(), sr);
                if(prevVal!=null) {
                    sr = prevVal;
                    sr.updateWith(curi,disposition); 
                }
            }
        }
    }

    public void crawledURISuccessful(CrawlURI curi) {
        handleSeed(curi,"Seed successfully crawled");
        // save crawled bytes tally
        crawledBytes.accumulate(curi);

        // Save status codes
        incrementMapCount(statusCodeDistribution,
            Integer.toString(curi.getFetchStatus()));

        // Save mime types
        String mime = MimetypeUtils.truncate(curi.getContentType());
        incrementMapCount(mimeTypeDistribution, mime);
        incrementMapCount(mimeTypeBytes, mime, curi.getContentSize());

        // Save hosts stats.
        ServerCache sc = serverCache;
        saveHostStats((curi.getFetchStatus() == 1)? "dns:":
                ServerCacheUtil.getHostFor(sc, curi.getUURI()).getHostName(),
                curi.getContentSize());
        
        if (curi.getData().containsKey(A_SOURCE_TAG)) {
        	saveSourceStats((String)curi.getData().get(A_SOURCE_TAG),
                        ServerCacheUtil.getHostFor(sc, curi.getUURI()).
                    getHostName()); 
        }
    }
         
    protected void saveSourceStats(String source, String hostname) {
        synchronized(sourceHostDistribution) {
            ConcurrentMap<String,AtomicLong> hostUriCount = 
                sourceHostDistribution.get(source);
            if (hostUriCount == null) {
                hostUriCount = new ConcurrentHashMap<String,AtomicLong>();
                ConcurrentMap<String,AtomicLong> prevVal = 
                    sourceHostDistribution.putIfAbsent(source, hostUriCount);
                if(prevVal != null) {
                    hostUriCount = prevVal;
                }
            }
            incrementMapCount(hostUriCount, hostname);
        }
    }
    
    protected void saveHostStats(String hostname, long size) {
        incrementMapCount(hostsDistribution, hostname);
        hostsDistributionTop.update(
                hostname, getReportValue(hostsDistribution, hostname)); 

        incrementMapCount(hostsBytes, hostname, size);
        hostsBytesTop.update(hostname, 
                getReportValue(hostsBytes, hostname));
        
        long time = new Long(System.currentTimeMillis());
        getHostLastFinished(hostname).set(time); 
        hostsLastFinishedTop.update(hostname, time);
    }

    public void crawledURINeedRetry(CrawlURI curi) {
        handleSeed(curi,"Failed to crawl seed, will retry");
    }

    public void crawledURIDisregard(CrawlURI curi) {
        handleSeed(curi,"Seed was disregarded");
    }

    public void crawledURIFailure(CrawlURI curi) {
        handleSeed(curi,"Failed to crawl seed");
    }
    
    /**
     * Get a seed iterator for the job being monitored. 
     * 
     * <b>Note:</b> This iterator will iterate over a list of <i>strings</i> not
     * UURIs like the Scope seed iterator. The strings are equal to the URIs'
     * getURIString() values.
     * @return the seed iterator
     * FIXME: Consider using TransformingIterator here
     */
    public Iterator<String> getSeedsIterator() {
        List<String> seedsCopy = new Vector<String>();
        Iterator<UURI> i = seeds.seedsIterator();
        while (i.hasNext()) {
            seedsCopy.add(i.next().toString());
        }
        return seedsCopy.iterator();
    }

    public Iterator<SeedRecord> getSeedRecordsSortedByStatusCode() {
        return getSeedRecordsSortedByStatusCode(getSeedsIterator());
    }
    
    protected Iterator<SeedRecord> getSeedRecordsSortedByStatusCode(
            Iterator<String> i) {
        TreeSet<SeedRecord> sortedSet = 
          new TreeSet<SeedRecord>(new Comparator<SeedRecord>() {
            public int compare(SeedRecord sr1, SeedRecord sr2) {
                int code1 = sr1.getStatusCode();
                int code2 = sr2.getStatusCode();
                if (code1 == code2) {
                    // If the values are equal, sort by URIs.
                    return sr1.getUri().compareTo(sr2.getUri());
                }
                // mirror and shift the nubmer line so as to
                // place zero at the beginning, then all negatives 
                // in order of ascending absolute value, then all 
                // positives descending
                code1 = -code1 - Integer.MAX_VALUE;
                code2 = -code2 - Integer.MAX_VALUE;
                
                return new Integer(code1).compareTo(new Integer(code2));
            }
        });
        while (i.hasNext()) {
            String seed = i.next();
            SeedRecord sr = (SeedRecord) processedSeedsRecords.get(seed);
            if(sr==null) {
                sr = new SeedRecord(seed,"Seed has not been processed");
                // no need to retain synthesized record
            }
            sortedSet.add(sr);
        }
        return sortedSet.iterator();
    }
    
    /**
     * Return a copy of the hosts distribution in reverse-sorted (largest first)
     * order.
     * 
     * @return SortedMap of hosts distribution
     */
    public SortedMap<String,AtomicLong> getReverseSortedHostCounts(
            Map<String,AtomicLong> hostCounts) {
        synchronized(hostCounts){
            return getReverseSortedCopy(hostCounts);
        }
    }

    /**
     * Return a copy of the hosts distribution in reverse-sorted
     * (largest first) order. 
     * @return SortedMap of hosts distribution
     */
    public SortedMap<String,AtomicLong> getReverseSortedHostsDistribution() {
        synchronized(hostsDistribution){
            return getReverseSortedCopy(hostsDistribution);
        }
    }

    public File writeReportFile(String reportName) {
        for(Class<Report> reportClass : ALL_REPORTS) {
            if(reportClass.getSimpleName().equals(reportName)) {
             return writeReportFile(reportClass);
            }
        }
        return null; 
    }
    
    protected File writeReportFile(Class<Report> reportClass) {
        Report r;
        try {
            r = reportClass.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        r.setStats(this);
        File f = new File(getReportsDir().getFile(), r.getFilename());
        
        if(f.exists() && !controller.isRunning() && controller.hasStarted()) {
            // controller already started and stopped and file exists
            // so, don't overwrite
            logger.warning("reusing report: " + f.getAbsolutePath());
            return f;
        }
        
        f.getParentFile().mkdirs();
        try {
            PrintWriter bw = new PrintWriter(new FileWriter(f));
            r.write(bw);
            bw.close();
            addToManifest(f.getAbsolutePath(),
                CrawlerLoggerModule.MANIFEST_REPORT_FILE, true);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Unable to write " + f.getAbsolutePath() +
                " at the end of crawl.", e);
        }
        logger.info("wrote report: " + f.getAbsolutePath());
        return f; 
    }
    
    protected void addToManifest(String absolutePath, char manifest_report_file, boolean b) {
        // TODO Auto-generated method stub
        
    }
    
    /**
     * Run the reports.
     */
    public void dumpReports() {
        // TODO: sooner than here! Add all files mentioned in the crawl 
        // order to the manifest set.
        //controller.addOrderToManifest();
        
        for(Class<Report> reportClass : END_REPORTS) {
            writeReportFile(reportClass);
        }
    }

    public void crawlCheckpoint(/*StateProvider*/ Object def, File cpDir) throws Exception {
        // CrawlController is managing the checkpointing of this object.
        logNote("CRAWL CHECKPOINTING TO " + cpDir.toString());
    }
  
    private long getReportValue(Map<String,AtomicLong> map, String key) {
        if (key == null) {
            return -1;
        }
        Object o = map.get(key);
        if (o == null) {
            return -2;
        }
        if (!(o instanceof AtomicLong)) {
            throw new IllegalStateException("Expected AtomicLong but got " 
                    + o.getClass() + " for " + key);
        }
        return ((AtomicLong)o).get();
    }
    
    public void onApplicationEvent(ApplicationEvent event) {
        if(event instanceof CrawlStateEvent) {
            CrawlStateEvent event1 = (CrawlStateEvent)event;
            switch(event1.getState()) {
                case PAUSED:
                    this.crawlPaused(event1.getMessage());
                    break;
                case RUNNING:
                    this.crawlResuming(event1.getMessage());
                    break;
                case PAUSING:
                    this.crawlPausing(event1.getMessage());
                    break;
                case STOPPING:
                    this.crawlEnding(event1.getMessage());
                    break;
                case FINISHED:
                    this.crawlEnded(event1.getMessage());
                    break;
                case PREPARING:
                    this.crawlResuming(event1.getMessage());
                    break;
                default:
                    throw new RuntimeException("Unknown state: " + event1.getState());
            }
        }

        if(event instanceof CrawlURIDispositionEvent) {
            CrawlURIDispositionEvent dvent = (CrawlURIDispositionEvent)event;
            switch(dvent.getDisposition()) {
                case SUCCEEDED:
                    this.crawledURISuccessful(dvent.getCrawlURI());
                    break;
                case FAILED:
                    this.crawledURIFailure(dvent.getCrawlURI());
                    break;
                case DISREGARDED:
                    this.crawledURIDisregard(dvent.getCrawlURI());
                    break;
                case DEFERRED_FOR_RETRY:
                    this.crawledURINeedRetry(dvent.getCrawlURI());
                    break;
                default:
                    throw new RuntimeException("Unknown disposition: " + dvent.getDisposition());
            }
        }
    }
    
    public void tallySeeds() {
        seedsTotal = 0; 
        seedsCrawled = 0; 
        if(processedSeedsRecords==null) {
            // nothing to tally
            return; 
        }
        for (Iterator<String> i = getSeedsIterator();i.hasNext();) {
            SeedRecord sr = processedSeedsRecords.get(i.next());
            seedsTotal++;
            if(sr!=null &&(sr.getStatusCode() > 0)) {
                seedsCrawled++;
            }
        }
    }
}
