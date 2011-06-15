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
package org.archive.crawler.framework;

import java.io.File;
import java.io.IOException;

import javax.management.openmbean.CompositeData;

import org.archive.crawler.frontier.FrontierJournal;
import org.archive.modules.CrawlURI;
import org.archive.modules.deciderules.DecideRule;
import org.archive.modules.fetcher.FetchStats;
import org.archive.util.IdentityCacheable;
import org.archive.util.MultiReporter;
import org.json.JSONException;
import org.springframework.context.Lifecycle;


/**
 * An interface for URI Frontiers.
 *
 * <p>A URI Frontier is a pluggable module in Heritrix that maintains the
 * internal state of the crawl. This includes (but is not limited to):
 * <ul>
 *     <li>What URIs have been discovered
 *     <li>What URIs are being processed (fetched)
 *     <li>What URIs have been processed
 *     <li>In what order unprocessed URIs will be processed
 * </ul>
 *
 * <p>The Frontier is also responsible for enforcing any politeness restrictions
 * that may have been applied to the crawl. Such as limiting simultaneous
 * connection to the same host, server or IP number to 1 (or any other fixed
 * amount), delays between connections etc.
 *
 * <p>A URIFrontier is created by the
 * {@link org.archive.crawler.framework.CrawlController CrawlController} which
 * is in turn responsible for providing access to it. Most significant among
 * those modules interested in the Frontier are the
 * {@link org.archive.crawler.framework.ToeThread ToeThreads} who perform the
 * actual work of processing a URI.
 *
 * <p>The methods defined in this interface are those required to get URIs for
 * processing, report the results of processing back (ToeThreads) and to get
 * access to various statistical data along the way. The statistical data is
 * of interest to {@link org.archive.crawler.framework.StatisticsTracker
 * Statistics Tracking} modules. A couple of additional methods are provided
 * to be able to inspect and manipulate the Frontier at runtime.
 *
 * <p>The statistical data exposed by this interface is:
 * <ul>
 *     <li> {@link #discoveredUriCount() Discovered URIs}
 *     <li> {@link #queuedUriCount() Queued URIs}
 *     <li> {@link #finishedUriCount() Finished URIs}
 *     <li> {@link #succeededFetchCount() Successfully processed URIs}
 *     <li> {@link #failedFetchCount() Failed to process URIs}
 *     <li> {@link #disregardedUriCount() Disregarded URIs}
 *     <li> {@link #totalBytesWritten() Total bytes written}
 * </ul>
 *
 * <p>In addition the frontier may optionally implement an interface that
 * exposes information about hosts.
 *
 * <p>Furthermore any implementation of the URI Frontier should trigger
 * {@link org.archive.crawler.event.CrawlURIDispositionEvent
 * CrawlURIDispostionEvents} on the ApplicationContext to allow
 * statistics modules or other interested observers to collect info
 * about each completed URI's processing.
 *
 * <p>All URI Frontiers inherit from
 * {@link org.archive.crawler.settings.ModuleType ModuleType}
 * and therefore creating settings follows the usual pattern of pluggable modules
 * in Heritrix.
 *
 * @author Gordon Mohr
 * @author Kristinn Sigurdsson
 *
 * @see org.archive.crawler.framework.CrawlController
 * @see org.archive.crawler.framework.CrawlController#fireCrawledURIDisregardEvent(CrawlURI)
 * @see org.archive.crawler.framework.CrawlController#fireCrawledURIFailureEvent(CrawlURI)
 * @see org.archive.crawler.framework.CrawlController#fireCrawledURINeedRetryEvent(CrawlURI)
 * @see org.archive.crawler.framework.CrawlController#fireCrawledURISuccessfulEvent(CrawlURI)
 * @see org.archive.crawler.framework.StatisticsTracker
 * @see org.archive.crawler.framework.ToeThread
 * @see org.archive.crawler.settings.ModuleType
 */
public interface Frontier extends Lifecycle, MultiReporter {

    /**
     * Get the next URI that should be processed. If no URI becomes availible
     * during the time specified null will be returned.
     *
     * @return the next URI that should be processed.
     * @throws InterruptedException
     */
    CrawlURI next() throws InterruptedException;

    /**
     * Returns true if the frontier contains no more URIs to crawl.
     *
     * <p>That is to say that there are no more URIs either currently availible
     * (ready to be emitted), URIs belonging to deferred hosts or pending URIs
     * in the Frontier. Thus this method may return false even if there is no
     * currently availible URI.
     *
     * @return true if the frontier contains no more URIs to crawl.
     */
    boolean isEmpty();

    /**
     * Schedules a CrawlURI.
     *
     * <p>This method accepts one URI and schedules it immediately. This has
     * nothing to do with the priority of the URI being scheduled. Only that
     * it will be placed in it's respective queue at once. For priority
     * scheduling see {@link CrawlURI#setSchedulingDirective(int)}
     *
     * <p>This method should be synchronized in all implementing classes.
     *
     * @param caURI The URI to schedule.
     *
     * @see CrawlURI#setSchedulingDirective(int)
     */
    public void schedule(CrawlURI caURI);

    /**
     * Report a URI being processed as having finished processing.
     *
     * <p>ToeThreads will invoke this method once they have completed work on
     * their assigned URI.
     *
     * <p>This method is synchronized.
     *
     * @param cURI The URI that has finished processing.
     */
    public void finished(CrawlURI cURI);

    /**
     * Number of <i>discovered</i> URIs.
     *
     * <p>That is any URI that has been confirmed be within 'scope'
     * (i.e. the Frontier decides that it should be processed). This
     * includes those that have been processed, are being processed
     * and have finished processing. Does not include URIs that have
     * been 'forgotten' (deemed out of scope when trying to fetch,
     * most likely due to operator changing scope definition).
     *
     * <p><b>Note:</b> This only counts discovered URIs. Since the same
     * URI can (at least in most frontiers) be fetched multiple times, this
     * number may be somewhat lower then the combined <i>queued</i>,
     * <i>in process</i> and <i>finished</i> items combined due to duplicate
     * URIs being queued and processed. This variance is likely to be especially
     * high in Frontiers implementing 'revist' strategies.
     *
     * @return Number of discovered URIs.
     */
    public long discoveredUriCount();

    /**
     * Number of URIs <i>queued</i> up and waiting for processing.
     *
     * <p>This includes any URIs that failed but will be retried. Basically this
     * is any <i>discovered</i> URI that has not either been processed or is
     * being processed. The same discovered URI can be queued multiple times.
     *
     * @return Number of queued URIs.
     */
    public long queuedUriCount();

    
    /**
     * @return Number of URIs not currently queued/eligible but scheduled for future
     */
    public long futureUriCount(); 
    
    /**
     * Ordinal position of the 'deepest' URI eligible 
     * for crawling. Essentially, the length of the longest
     * frontier internal queue. 
     * 
     * @return long URI count to deepest URI
     */
    public long deepestUri(); // aka longest queue
    
    /**
     * Average depth of the last URI in all eligible queues.
     * That is, the average length of all eligible queues.
     * 
     * @return long average depth of last URIs in queues 
     */
    public long averageDepth(); // aka average queue length
    
    /**
     * Ratio of number of threads that would theoretically allow
     * maximum crawl progress (if each was as productive as current
     * threads), to current number of threads.
     * 
     * @return float congestion ratio 
     */
    public float congestionRatio(); // multiple of threads needed for max progress
    
    /**
     * Number of URIs that have <i>finished</i> processing.
     *
     * <p>Includes both those that were processed successfully and failed to be
     * processed (excluding those that failed but will be retried). Does not
     * include those URIs that have been 'forgotten' (deemed out of scope when
     * trying to fetch, most likely due to operator changing scope definition).
     *
     * @return Number of finished URIs.
     */
    public long finishedUriCount();

    /**
     * Number of <i>successfully</i> processed URIs.
     *
     * <p>Any URI that was processed successfully. This includes URIs that
     * returned 404s and other error codes that do not originate within the
     * crawler.
     *
     * @return Number of <i>successfully</i> processed URIs.
     */
    public long succeededFetchCount();

    /**
     * Number of URIs that <i>failed</i> to process.
     *
     * <p>URIs that could not be processed because of some error or failure in
     * the processing chain. Can include failure to acquire prerequisites, to
     * establish a connection with the host and any number of other problems.
     * Does not count those that will be retried, only those that have
     * permenantly failed.
     *
     * @return Number of URIs that failed to process.
     */
    public long failedFetchCount();

    /**
     * Number of URIs that were scheduled at one point but have been
     * <i>disregarded</i>.
     *
     * <p>Counts any URI that is scheduled only to be disregarded
     * because it is determined to lie outside the scope of the crawl. Most
     * commonly this will be due to robots.txt exclusions.
     *
     * @return The number of URIs that have been disregarded.
     */
    public long disregardedUriCount();

    /**
     * Load URIs from a file, for scheduling and/or considered-included 
     * status (if from a recovery log). 
     *
     * <p> The 'params' Map describes the source file to use and options
     * in effect regarding its format and handling. Significant keys 
     * are:
     * 
     * <p>"path": full path to source file. If the path ends '.gz', it 
     * will be considered to be GZIP compressed.
     * <p>"format": one of "onePer", "crawlLog", or "recoveryLog"
     * <p>"forceRevisit": if non-null, URIs will be force-scheduled even
     * if already considered included
     * <p>"scopeSchedules": if non-null, any URI imported be checked
     * against the frontier's configured scope before scheduling 
     * 
     * <p>If the "format" is "recoveryLog", 7 more keys are significant:
     * 
     * <p>"includeSuccesses": if non-null, success lines ("Fs") in the log
     * will be considered-included. (Usually, this is the aim of
     * a recovery-log import.)
     * <p>"includeFailures": if non-null, failure lines ("Ff") in the log
     * will be considered-included. (Sometimes, this is desired.)
     * <p>"includeScheduleds": If non-null, scheduled lines ("F+") in the 
     * log will be considered-included. (Atypical, but an option for 
     * completeness.)
     * <p>"scopeIncludes": if non-null, any of the above will be checked
     * against the frontier's configured scope before consideration
     *
     * <p>"scheduleSuccesses": if non-null, success lines ("Fs") in the log
     * will be schedule-attempted. (Atypical, as all successes
     * are preceded by "F+" lines.)
     * <p>"scheduleFailures": if non-null, failure lines ("Ff") in the log
     * will be schedule-attempted. (Atypical, as all failures
     * are preceded by "F+" lines.)
     * <p>"scheduleScheduleds": if non-null, scheduled lines ("F+") in the 
     * log will be considered-included. (Usually, this is the aim of a
     * recovery-log import.)
     * 
     * TODO: add parameter for auto-unpause-at-good-time
     * 
     * @param params Map describing source file and options as above
     * @throws IOException If problems occur reading file.
     * @throws JSONException 
     */
    public void importURIs(
            String params)
			throws IOException;

    
    /**
     * Import URIs from the given file (in recover-log-like format, with
     * a 3-character 'type' tag preceding a URI with optional hops/via).
     * 
     * If 'includeOnly' is true, the URIs will only be imported into 
     * the frontier's alreadyIncluded structure, without being queued.
     * 
     * Only imports URIs if their first tag field matches the acceptTags 
     * pattern.
     * 
     * @param source File recovery log file to use (may be .gz compressed)
     * @param applyScope whether to apply crawl scope to URIs
     * @param includeOnly whether to only add to included filter, not schedule
     * @param forceFetch whether to force fetching, even if already seen 
     * (ignored if includeOnly is set)
     * @param acceptTags String regex; only lines whose first field 
     * match will be included
     * @return number of lines in recovery log (for reference)
     * @throws IOException
     */
    public long importRecoverFormat(File source, boolean applyScope, 
            boolean includeOnly, boolean forceFetch, String acceptTags) 
    throws IOException;
    
    /**
     * Get a <code>URIFrontierMarker</code> initialized with the given
     * regular expression at the 'start' of the Frontier.
     * @param regex The regular expression that URIs within the frontier must
     *                match to be considered within the scope of this marker
     * @param inCacheOnly If set to true, only those URIs within the frontier
     *                that are stored in cache (usually this means in memory
     *                rather then on disk, but that is an implementation
     *                detail) will be considered. Others will be entierly
     *                ignored, as if they dont exist. This is usefull for quick
     *                peeks at the top of the URI list.
     * @return A URIFrontierMarker that is set for the 'start' of the frontier's
     *                URI list.
     */
//    public FrontierMarker getInitialMarker(String regex,
//                                              boolean inCacheOnly);

    /**
     * Returns a list of all uncrawled URIs starting from a specified marker
     * until <code>numberOfMatches</code> is reached.
     *
     * <p>Any encountered URI that has not been successfully crawled, terminally
     * failed, disregarded or is currently being processed is included. As
     * there may be duplicates in the frontier, there may also be duplicates
     * in the report. Thus this includes both discovered and pending URIs.
     *
     * <p>The list is a set of strings containing the URI strings. If verbose is
     * true the string will include some additional information (path to URI
     * and parent).
     *
     * <p>The <code>URIFrontierMarker</code> will be advanced to the position at
     * which it's maximum number of matches found is reached. Reusing it for
     * subsequent calls will thus effectively get the 'next' batch. Making
     * any changes to the frontier can invalidate the marker.
     *
     * <p>While the order returned is consistent, it does <i>not</i> have any
     * explicit relation to the likely order in which they may be processed.
     *
     * <p><b>Warning:</b> It is unsafe to make changes to the frontier while
     * this method is executing. The crawler should be in a paused state before
     * invoking it.
     *
     * @param marker
     *            A marker specifing from what position in the Frontier the
     *            list should begin.
     * @param numberOfMatches
     *            how many URIs to add at most to the list before returning it
     * @param verbose
     *            if set to true the strings returned will contain additional
     *            information about each URI beyond their names.
     * @return a list of all pending URIs falling within the specification
     *            of the marker
     * @throws InvalidFrontierMarkerException when the
     *            <code>URIFronterMarker</code> does not match the internal
     *            state of the frontier. Tolerance for this can vary
     *            considerably from one URIFrontier implementation to the next.
     * @see FrontierMarker
     * @see #getInitialMarker(String, boolean)
     */
    public CompositeData getURIsList(
            String marker,
            int numberOfMatches,
            String regex,
            boolean verbose);

    /**
     * Delete any URI that matches the given regular expression from the list
     * of discovered and pending URIs. This does not prevent them from being
     * rediscovered.
     *
     * <p>Any encountered URI that has not been successfully crawled, terminally
     * failed, disregarded or is currently being processed is considered to be
     * a pending URI.
     *
     * <p><b>Warning:</b> It is unsafe to make changes to the frontier while
     * this method is executing. The crawler should be in a paused state before
     * invoking it.
     *
     * @param match A regular expression, any URIs that matches it will be
     *              deleted.
     * @return The number of URIs deleted
     */
    public long deleteURIs(
            String queueRegex,
            String match);

    /**
     * Notify Frontier that a CrawlURI has been deleted outside of the
     * normal next()/finished() lifecycle. 
     * 
     * @param curi Deleted CrawlURI.
     */
    public void deleted(CrawlURI curi);

    /**
     * Notify Frontier that it should consider the given UURI as if
     * already scheduled.
     * 
     * @param u UURI instance to add to the Already Included set.
     */
    public void considerIncluded(CrawlURI curi);

    /**
     * Notify Frontier that it should not release any URIs, instead
     * holding all threads, until instructed otherwise. 
     */
    public void pause();

    /**
     * Resumes the release of URIs to crawl, allowing worker
     * ToeThreads to proceed. 
     */
    public void unpause();

    /**
     * Notify Frontier that it should end the crawl, giving
     * any worker ToeThread that askss for a next() an 
     * EndedException. 
     */
    public void terminate();
    
    /**
     * @return Return the instance of {@link FrontierJournal} that
     * this Frontier is using.  May be null if no journaling.
     */
    public FrontierJournal getFrontierJournal();
    
    /**
     * @param cauri CrawlURI for which we're to calculate and
     * set class key.
     * @return Classkey for <code>cauri</code>.
     */
    public String getClassKey(CrawlURI cauri);

    /*
     * Return the internally-configured crawl 'scope' (rules for
     * deciding whether a URI is crawled or not). 
     */
    public DecideRule getScope();

    /**
     * Request that Frontier allow crawling to begin. Usually
     * just unpauses Frontier, if paused. 
     */
    public void run();

    /**
     * Get the 'frontier group' (usually queue) for the given 
     * CrawlURI. 
     * @param curi CrawlURI to find matching group
     * @return FrontierGroup for the CrawlURI
     */
    public FrontierGroup getGroup(CrawlURI curi);
    
    /**
     * Generic interface representing the internal groupings 
     * of a Frontier's URIs -- usually queues. Currently only 
     * offers the HasCrawlSubstats interface. 
     */
    public interface FrontierGroup 
    extends FetchStats.HasFetchStats, FetchStats.CollectsFetchStats, IdentityCacheable {
    }
    
    /**
     * Request the Frontier reach the given state as soon as possible. (Only
     * when a later notification is given the CrawlController has the state
     * actually been reached.)
     * 
     * @param target Frontier.State to pursue
     */
    public void requestState(State target);
    
    /**
     * Enumeration of possible target states. 
     */
    public enum State { 
        RUN,  // juggle/prioritize/emit; usual state
        EMPTY, // running/ready but no URIs queued/scheduled
        HOLD, // NOT YET USED enter a consistent, stable, checkpointable state ASAP
        PAUSE, // enter a stable state where no URIs are in-progress; unlike
               // HOLD requires all in-process URIs to complete
        FINISH  // end and cleanup; may not return to any other state after
                  // this state is requested/reached
    }

    /**
     * Inform frontier that a block of processing that should complete atomically
     * with respect to checkpoints is about to begin. Callers should ensure an
     * endDisposition() call soon follows; a mismatch risks freezing the frontier
     * if a checkpoint is requested. 
     * @param curi
     */
    public void beginDisposition(CrawlURI curi);

    /**
     * Inform frontier the processing signalled by an earlier pending 
     * beginDisposition() call has finished. Implementors should be resilient 
     * against extra endDisposition calls, as callers dealing with exceptional
     * conditions need to be free to call this 'just in case'. 
     */
    public void endDisposition();
}
