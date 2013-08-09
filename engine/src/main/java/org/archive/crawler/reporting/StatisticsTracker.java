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

import static org.archive.modules.CoreAttributeConstants.A_SOURCE_TAG;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.collections.Closure;
import org.archive.bdb.BdbModule;
import org.archive.bdb.DisposableStoredSortedMap;
import org.archive.checkpointing.Checkpoint;
import org.archive.checkpointing.Checkpointable;
import org.archive.crawler.event.CrawlStateEvent;
import org.archive.crawler.event.CrawlURIDispositionEvent;
import org.archive.crawler.event.StatSnapshotEvent;
import org.archive.crawler.framework.CrawlController;
import org.archive.crawler.framework.Engine;
import org.archive.crawler.util.CrawledBytesHistotable;
import org.archive.crawler.util.TopNSet;
import org.archive.modules.CrawlURI;
import org.archive.modules.net.CrawlHost;
import org.archive.modules.net.ServerCache;
import org.archive.modules.seeds.SeedListener;
import org.archive.modules.seeds.SeedModule;
import org.archive.spring.ConfigPath;
import org.archive.util.ArchiveUtils;
import org.archive.util.FileUtils;
import org.archive.util.JSONUtils;
import org.archive.util.MimetypeUtils;
import org.archive.util.ObjectIdentityCache;
import org.archive.util.ObjectIdentityMemCache;
import org.archive.util.PaddingStringBuffer;
import org.archive.util.Supplier;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanNameAware;
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
        ApplicationListener<ApplicationEvent>,
        SeedListener,
        Lifecycle,
        Runnable, 
        Checkpointable,
        BeanNameAware {
    @SuppressWarnings("unused")
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

    protected ConfigPath reportsDir = new ConfigPath(Engine.REPORTS_DIR_NAME,"${launchId}/reports");
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
    
    protected ApplicationContext appCtx;
    public void setApplicationContext(ApplicationContext appCtx) throws BeansException {
        this.appCtx = appCtx;
    }
    
    /**
     * Messages from the StatisticsTracker.
     */
    private final static Logger logger =
        Logger.getLogger(StatisticsTracker.class.getName());

    /**
     * Whether to maintain seed disposition records (expensive in 
     * crawls with millions of seeds)
     */
    protected boolean trackSeeds = true;
    public boolean getTrackSeeds() {
        return this.trackSeeds;
    }
    public void setTrackSeeds(boolean trackSeeds) {
        this.trackSeeds = trackSeeds;
    }
    
    /**
     * Whether to maintain hosts-per-source-tag records for; very expensive in 
     * crawls with large numbers of source-tags (seeds) or large crawls 
     * over many hosts
     */
    protected boolean trackSources = true;
    public boolean getTrackSources() {
        return this.trackSources;
    }
    public void setTrackSources(boolean trackSources) {
        this.trackSources = trackSources;
    }
            
    /**
     * The interval between writing progress information to log.
     */
    protected int intervalSeconds = 20;
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
    protected int keepSnapshotsCount = 5;
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
    protected long crawlStartTime;
    /** wall-clock time the crawl ended */
    protected long crawlEndTime = -1; // Until crawl ends, this value is -1.
    /** wall-clock time of last pause, while pause in progres */ 
    protected long crawlPauseStarted = 0;
    /** duration tally of all time spent in paused state */ 
    protected long crawlTotalPausedTime = 0;

    /** snapshots of crawl tallies and rates */
    protected LinkedList<CrawlStatSnapshot> snapshots = new LinkedList<CrawlStatSnapshot>();
    
    protected ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    
    /*
     * Cumulative data
     */
    /** tally sizes novel, verified (same hash), vouched (not-modified) */ 
    protected CrawledBytesHistotable crawledBytes = new CrawledBytesHistotable();
    
    public CrawledBytesHistotable getCrawledBytes() {
        return crawledBytes;
    }

    // TODO: fortify these against key explosion with bigmaps like other tallies
    /** Keep track of the file types we see (mime type -> count) */
    protected ConcurrentMap<String,AtomicLong> mimeTypeDistribution
     = new ConcurrentHashMap<String, AtomicLong>();
    protected ConcurrentMap<String,AtomicLong> mimeTypeBytes
    = new ConcurrentHashMap<String, AtomicLong>();
    
    /** Keep track of fetch status codes */
    protected ConcurrentMap<String,AtomicLong> statusCodeDistribution
    = new ConcurrentHashMap<String, AtomicLong>();
    
    /** Keep track of URL counts per host per seed */
    // TODO: restore spill-to-disk, like with processedSeedsRecords
    protected ConcurrentHashMap<String, ConcurrentMap<String, AtomicLong>> sourceHostDistribution = 
        new ConcurrentHashMap<String, ConcurrentMap<String,AtomicLong>>(); 

    /* Keep track of 'top' hosts for live reports */
    protected TopNSet hostsDistributionTop;
    protected TopNSet hostsBytesTop;
    protected TopNSet hostsLastFinishedTop;
    
    /**
     * Record of seeds and latest results
     */
    protected ObjectIdentityCache<SeedRecord> processedSeedsRecords = 
        new ObjectIdentityMemCache<SeedRecord>();
    protected long seedsTotal = -1; 
    protected long seedsCrawled = -1;
    
    public StatisticsTracker() {
        
    }
    
    protected List<Report> reports;
    
    public List<Report> getReports() {
        // lazy initialization so we don't pointlessly create a bunch of beans
        // right before setReports is called
        if (reports == null) {
            reports = new LinkedList<Report>();
            reports.add(new CrawlSummaryReport());
            reports.add(new SeedsReport());
            reports.add(new HostsReport());
            reports.add(new SourceTagsReport());
            reports.add(new MimetypesReport());
            reports.add(new ResponseCodeReport());
            reports.add(new ProcessorsReport());
            reports.add(new FrontierSummaryReport());
            reports.add(new ToeThreadsReport());
        }
        
        return reports;
    }
    
    public void setReports(List<Report> reports) {
        this.reports = reports;
    }

    protected boolean isRunning = false;
    public boolean isRunning() {
        return isRunning;
    }
    public void stop() {
        isRunning = false;
        executor.shutdownNow();
        progressStatisticsEvent();
        dumpReports();
    }
    
    @SuppressWarnings("unchecked")
    public void start() {
        isRunning = true;
        boolean isRecover = (recoveryCheckpoint != null); 
        try {
            this.processedSeedsRecords = bdb.getObjectCache("processedSeedsRecords",
                    isRecover, SeedRecord.class);
            
            this.hostsDistributionTop = new TopNSet(getLiveHostReportSize());
            this.hostsBytesTop = new TopNSet(getLiveHostReportSize());
            this.hostsLastFinishedTop = new TopNSet(getLiveHostReportSize());
            
            if(isRecover) {
                JSONObject json = recoveryCheckpoint.loadJson(beanName);
                
                crawlStartTime = json.getLong("crawlStartTime");
                crawlEndTime = json.getLong("crawlEndTime");
                crawlTotalPausedTime = json.getLong("crawlTotalPausedTime");
                crawlPauseStarted = json.getLong("crawlPauseStarted");
                tallyCurrentPause();
                
                JSONUtils.putAllLongs(
                        hostsDistributionTop.getTopSet(),
                        json.getJSONObject("hostsDistributionTop"));
                hostsDistributionTop.updateBounds();
                JSONUtils.putAllLongs(
                        hostsBytesTop.getTopSet(),
                        json.getJSONObject("hostsBytesTop"));
                hostsBytesTop.updateBounds();
                JSONUtils.putAllLongs(
                        hostsLastFinishedTop.getTopSet(),
                        json.getJSONObject("hostsLastFinishedTop"));
                hostsLastFinishedTop.updateBounds();
                
                JSONUtils.putAllAtomicLongs(
                    mimeTypeDistribution,
                    json.getJSONObject("mimeTypeDistribution"));
                JSONUtils.putAllAtomicLongs(
                    mimeTypeBytes,
                    json.getJSONObject("mimeTypeBytes"));
                JSONUtils.putAllAtomicLongs(
                    statusCodeDistribution,
                    json.getJSONObject("statusCodeDistribution"));
          

                JSONObject shd = json.getJSONObject("sourceHostDistribution");
                Iterator<String> keyIter = shd.keys();
                for(; keyIter.hasNext();) {
                    String source = keyIter.next();
                    ConcurrentHashMap<String, AtomicLong> hostUriCount = new ConcurrentHashMap<String, AtomicLong>();
                    JSONUtils.putAllAtomicLongs(hostUriCount,shd.getJSONObject(source));
                    sourceHostDistribution.put(source, hostUriCount);
                }
                
                JSONUtils.putAllLongs(
                    crawledBytes,
                    json.getJSONObject("crawledBytes"));
            }
        } catch (DatabaseException e) {
            throw new IllegalStateException(e);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        // Log the legend
        this.controller.logProgressStatistics(progressStatisticsLegend());
        executor.scheduleAtFixedRate(this, 0, getIntervalSeconds(), TimeUnit.SECONDS);
    }

    /**
     * Do activity. Is called by ScheduledExecutorService at intervals specified by
     * intervalSeconds
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
    
    public String getProgressStamp() {
        return 
            progressStatisticsLegend() 
            + "\n" 
            + getSnapshot().getProgressStatisticsLine();
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
    
    public LinkedList<CrawlStatSnapshot> listSnapshots() {
        // not named getSnapshots to avoid autodiscovery as a (invalid) bean-property
        return snapshots;
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
        logNote("CRAWL RUNNING - " + statusMessage);
    }
    
    public void crawlEmpty(String statusMessage) {
        logNote("CRAWL EMPTY - " + statusMessage);
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
        crawlEndTime = System.currentTimeMillis();
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
    public Map<String, AtomicLong> getFileDistribution() {
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
            lw = new AtomicLong(0);
            AtomicLong prevVal = map.putIfAbsent(key, lw);
            if(prevVal != null) {
                lw = prevVal;
            }
        } 
        lw.addAndGet(increment);
    }

    /**
     * Sort the entries of the given Map in descending order by their
     * values, which must be longs wrapped with <code>AtomicLong</code>.
     * <p>
     * Elements are sorted by value from largest to smallest. Equal values are
     * sorted by their keys. The returned map is a StoredSortedMap, and
     * thus may include duplicate keys. 
     *
     * If the passed-in map requires access to be synchronized, the caller
     * should ensure this synchronization. 
     * 
     * @param mapOfAtomicLongValues
     *            Assumes values are wrapped with AtomicLong.
     * @return a sorted set containing the same elements as the map.
     */
    public DisposableStoredSortedMap<Long,String> getReverseSortedCopy(
            final Map<String,AtomicLong> mapOfAtomicLongValues) {
        DisposableStoredSortedMap<Long,String> sortedMap = 
            bdb.getStoredMap(
                    null,
                    Long.class,
                    String.class,
                    true,
                    false);
        for(String k : mapOfAtomicLongValues.keySet()) {
            sortedMap.put(-mapOfAtomicLongValues.get(k).longValue(), k);
        }
        return sortedMap;
    }



    /**
     * Return a objectCache representing the distribution of status codes for
     * successfully fetched curis, as represented by a cache where key -&gt;
     * val represents (string)code -&gt; (integer)count.
     * 
     * <b>Note: </b> All the values are wrapped with a
     * {@link AtomicLong AtomicLong}
     * @return 
     * 
     * @return statusCodeDistribution
     */
    public Map<String, AtomicLong> getStatusCodeDistribution() {
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
    public long getHostLastFinished(String host){
        return serverCache.getHostFor(host).getSubstats().getLastSuccessTime();
    }

    /**
     * Returns the accumulated number of bytes downloaded from a given host.
     * @param host name of the host
     * @return the accumulated number of bytes downloaded from a given host
     */
    public long getBytesPerHost(String host){
        return serverCache.getHostFor(host).getSubstats().getTotalBytes();
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
     * If the curi is a seed, we update the processedSeeds cache.
     *
     * @param curi The CrawlURI that may be a seed.
     * @param disposition The disposition of the CrawlURI.
     */
    protected void handleSeed(final CrawlURI curi, final String disposition) {
        if(getTrackSeeds()) {
            if(curi.isSeed()){
                SeedRecord sr = processedSeedsRecords.getOrUse(
                        curi.getURI(),
                        new Supplier<SeedRecord>() {
                            public SeedRecord get() {
                                return new SeedRecord(curi, disposition);
                            }});
                sr.updateWith(curi,disposition); 
            }
        } // else ignore
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
        saveHostStats(sc.getHostFor(curi.getUURI()).getHostName(),
                curi.getContentSize());
        
        if (getTrackSources() && curi.getData().containsKey(A_SOURCE_TAG)) {
        	saveSourceStats((String)curi.getData().get(A_SOURCE_TAG),
                        sc.getHostFor(curi.getUURI()).
                    getHostName()); 
        }
    }
         
    protected void saveSourceStats(String source, String hostname) {
        ConcurrentMap<String,AtomicLong> hostUriCount = sourceHostDistribution.get(source); 
        if(hostUriCount == null) {
            hostUriCount = new ConcurrentHashMap<String,AtomicLong>();
            ConcurrentMap<String,AtomicLong> prevVal = sourceHostDistribution.putIfAbsent(source, hostUriCount);
            if (prevVal!=null) {
                hostUriCount = prevVal; 
            }
        }
        incrementMapCount(hostUriCount, hostname);

    }
    
    /**
     * Update some running-stats based on a URI success
     * 
     * @param hostname
     * @param size
     */
    protected void saveHostStats(String hostname, long size) {
        // TODO: consider moving 'top' accounting elsewhere, such 
        // as the frontier or ServerCache itself
        
        CrawlHost host = serverCache.getHostFor(hostname); 
        hostsDistributionTop.update(hostname, host.getSubstats().getFetchSuccesses()); 
        hostsBytesTop.update(hostname, host.getSubstats().getSuccessBytes());
        hostsLastFinishedTop.update(hostname, host.getSubstats().getLastSuccessTime());
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
     * Get a seed iterator for the job being monitored. Only reports
     * known seeds from processedSeedsRecords -- but as a SeedListener, 
     * that should be complete. 
     * 
     * <b>Note:</b> This iterator will iterate over a list of <i>strings</i> not
     * UURIs like the Scope seed iterator. The strings are equal to the URIs'
     * getURIString() values.
     * @return the seed iterator
     */
    public Iterator<String> getSeedsIterator() {
        return processedSeedsRecords.keySet().iterator();
    }

    public DisposableStoredSortedMap<Integer,SeedRecord> calcSeedRecordsSortedByStatusCode() {
        Iterator<String> i = getSeedsIterator();
        DisposableStoredSortedMap<Integer,SeedRecord> sortedMap = 
            bdb.getStoredMap(
                    null,
                    Integer.class,
                    SeedRecord.class,
                    true,
                    false);
        
        while (i.hasNext()) {
            String seed = i.next();
            SeedRecord sr = (SeedRecord) processedSeedsRecords.get(seed);
            if(sr==null) {
                sr = new SeedRecord(seed,"Seed has not been processed");
                // no need to retain synthesized record
            }
            sortedMap.put(sr.sortShiftStatusCode(), sr); 
        }
        return sortedMap;
    }
    
    /**
     * Return a copy of the hosts distribution in reverse-sorted (largest first)
     * order.
     * 
     * @return SortedMap of hosts distribution
     */
    public DisposableStoredSortedMap<Long,String> getReverseSortedHostCounts(
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
    public DisposableStoredSortedMap<Long,String> calcReverseSortedHostsDistribution() {
        final DisposableStoredSortedMap<Long,String> sortedMap = 
            bdb.getStoredMap(
                    null,
                    Long.class,
                    String.class,
                    true,
                    false);     
        serverCache.forAllHostsDo(new Closure() {
            @Override
            public void execute(Object hostObj) {
                CrawlHost host = (CrawlHost) hostObj; 
                sortedMap.put(-host.getSubstats().getFetchSuccesses(), host.getHostName());
            }
        });
        return sortedMap;
    }

    public File writeReportFile(String reportName) {
        for(Report report: getReports()) {
            if (report.getClass().getSimpleName().equals(reportName)) {
                return writeReportFile(report, false);
            }
        }
        return null;
    }

    protected File writeReportFile(Report report, boolean force) {
        File f = new File(getReportsDir().getFile(), report.getFilename());
        
        if(f.exists() && !controller.isRunning() && controller.hasStarted() && !force) {
            // controller already started and stopped 
            // and file exists
            // and force not requested
            // so, don't overwrite
            logger.info("reusing report: " + f.getAbsolutePath());
            return f;
        }
        
        try {
            FileUtils.ensureWriteableDirectory(f.getParentFile());
            PrintWriter bw = new PrintWriter(new FileWriter(f));
            report.write(bw, this);
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
        
        for (Report report: getReports()) {
            if (report.getShouldReportAtEndOfCrawl()) {
                try {
                    writeReportFile(report, true);
                } catch (RuntimeException re) {
                    logger.log(Level.SEVERE, re.getMessage(), re);
                }
            }
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
                case EMPTY:
                    this.crawlEmpty(event1.getMessage());
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

    /** 
     * Create a seed record, even on initial notification (before
     * any real attempt/processing.
     * 
     * @see org.archive.modules.seeds.SeedListener#addedSeed(org.archive.modules.CrawlURI)
     */
    public void addedSeed(CrawlURI curi) {
        // record even undisposed-seeds for reporting purposes
        handleSeed((CrawlURI) curi, "");
    }
    /**
     * Do nothing with nonseed lines.
     * 
     * @see org.archive.modules.seeds.SeedListener#nonseedLine(java.lang.String)
     */
    public boolean nonseedLine(String line) {
        return false;
    }
    
    public void concludedSeedBatch() {
        // do nothing;
    }
    
    // BeanNameAware
    protected String beanName; 
    public void setBeanName(String name) {
        this.beanName = name;
    }
    
    // Checkpointable
    public void startCheckpoint(Checkpoint checkpointInProgress) {}
    public void doCheckpoint(Checkpoint checkpointInProgress) throws IOException {
        JSONObject json = new JSONObject();
        try {
            json.put("crawlStartTime",crawlStartTime);
            json.put("crawlEndTime",crawlEndTime);
            long virtualCrawlPauseStarted = crawlPauseStarted;
            if(virtualCrawlPauseStarted<1) {
                // TODO: use instant checkpoint started?
                virtualCrawlPauseStarted = System.currentTimeMillis();
            }
            json.put("crawlPauseStarted",virtualCrawlPauseStarted);
            json.put("crawlTotalPausedTime",crawlTotalPausedTime);
            
            json.put("hostsDistributionTop", hostsDistributionTop.getTopSet());
            json.put("hostsBytesTop", hostsBytesTop.getTopSet());
            json.put("hostsLastFinishedTop", hostsLastFinishedTop.getTopSet());

            json.put("mimeTypeDistribution", mimeTypeDistribution);
            json.put("mimeTypeBytes", mimeTypeBytes);
            json.put("statusCodeDistribution", statusCodeDistribution);

            json.put("sourceHostDistribution", sourceHostDistribution);
            
            json.put("crawledBytes", crawledBytes);

            // TODO: save crawledBytesHistotable
            checkpointInProgress.saveJson(beanName, json);
        } catch (JSONException e) {
            // impossible
            throw new RuntimeException(e);
        }
    }
    public void finishCheckpoint(Checkpoint checkpointInProgress) {}
    protected Checkpoint recoveryCheckpoint;
    public void setRecoveryCheckpoint(Checkpoint recoveryCheckpoint) {
        this.recoveryCheckpoint = recoveryCheckpoint;
    }
    
}
